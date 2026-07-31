/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package org.kanger.storage;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.Enums;
import org.kanger.exception.CommandErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class DB implements IData {

    private static final Object locker = new Object();
    private static final String[] GENERATION_SUFFIXES = {
            ".index", ".store", ".integrity"
    };

    private String storageName = "";
    private Map<String, IBase> bases = new HashMap<String, IBase>();
    private IUser user = null;

    @Override
    public void init(IUser user) {
        this.user = user;
        ((User) user).setData(this);
    }

    @Override
    public void use(String name) throws Exception {
        if (!isClosed()) {
            close();
        }
        storageName = name;
    }

    @Override
    public void close() throws Exception {
        Exception failure = null;
        Iterator<Map.Entry<String, IBase>> iterator = bases.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, IBase> entry = iterator.next();
            try {
                entry.getValue().close();
                iterator.remove();
            } catch (Exception closeError) {
                if (failure == null) {
                    failure = closeError;
                } else {
                    failure.addSuppressed(closeError);
                }
            }
        }
        if (bases.isEmpty()) {
            storageName = "";
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void flush() throws Exception {
        for (IBase b : bases.values()) {
            b.flush();
        }
    }

    public void remove(String name) throws Exception {
        String tmp;
        if (!isClosed() && (name == null || name.isEmpty() || storageName.equals(name))) {
            tmp = storageName;
            close();
        } else if (name != null) {
            tmp = name;
        } else {
            throw new CommandErrorException("DB name expected");
        }

        String dbPath = user.getDatabaseDir() + tmp;
        deleteStorageFiles(dbPath);
    }

    @Override
    public void reindex(IReactor<String> reactor, IMind mind) throws Exception {
        String tmp = storageName;
        if (isClosed()) {
            throw new CommandErrorException("DB not used");
        }

        IUser u = new User();
        IMind m = new Mind(u);
        u.setDatabaseDir(user.getDatabaseDir());
        DB tmpDB = new DB();
        tmpDB.init(u);
        m.useStorage(tmp + "-temporary");

        for (Map.Entry<String, IBase> e : bases.entrySet()) {
            if (reactor != null) {
                reactor.run(e.getKey());
            }
            e.getValue().reindex(tmpDB.getBase(e.getKey()), mind);
        }

        close();
        m.closeStorage();

        String dbPath = user.getDatabaseDir() + tmp;
        String temporaryPath = dbPath + "-temporary";
        replaceGeneration(dbPath, temporaryPath);

        new File(dbPath + ".integrity.delta").delete();
        deleteRecoveryLogs(dbPath);
        new File(temporaryPath + ".integrity.delta").delete();
        deleteRecoveryLogs(temporaryPath);

        use(tmp);
    }

    private void replaceGeneration(String dbPath, String temporaryPath)
            throws Exception {
        List<GenerationFile> generation = new ArrayList<GenerationFile>();
        String backupMarker = ".reindex-backup-" + UUID.randomUUID().toString();

        for (String suffix : GENERATION_SUFFIXES) {
            GenerationFile file = new GenerationFile(
                    new File(dbPath + suffix),
                    new File(temporaryPath + suffix),
                    new File(dbPath + suffix + backupMarker));
            if (!file.temporary.isFile()) {
                throw new IOException("Temporary reindex file is missing: "
                        + file.temporary.getPath());
            }
            generation.add(file);
        }

        List<GenerationFile> backedUp = new ArrayList<GenerationFile>();
        List<GenerationFile> installed = new ArrayList<GenerationFile>();
        try {
            for (GenerationFile file : generation) {
                if (file.live.exists()) {
                    Files.move(file.live.toPath(), file.backup.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    backedUp.add(file);
                }
            }
            for (GenerationFile file : generation) {
                Files.move(file.temporary.toPath(), file.live.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                installed.add(file);
            }
        } catch (Exception publicationError) {
            rollbackGeneration(installed, backedUp, publicationError);
            throw publicationError;
        }

        for (GenerationFile file : backedUp) {
            file.backup.delete();
        }
    }

    private void rollbackGeneration(List<GenerationFile> installed,
                                    List<GenerationFile> backedUp,
                                    Exception publicationError) {
        for (int i = installed.size() - 1; i >= 0; --i) {
            GenerationFile file = installed.get(i);
            try {
                if (file.live.exists()) {
                    Files.move(file.live.toPath(), file.temporary.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception rollbackError) {
                publicationError.addSuppressed(rollbackError);
            }
        }
        for (int i = backedUp.size() - 1; i >= 0; --i) {
            GenerationFile file = backedUp.get(i);
            try {
                if (file.backup.exists()) {
                    Files.move(file.backup.toPath(), file.live.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception rollbackError) {
                publicationError.addSuppressed(rollbackError);
            }
        }
    }

    private static final class GenerationFile {
        private final File live;
        private final File temporary;
        private final File backup;

        private GenerationFile(File live, File temporary, File backup) {
            this.live = live;
            this.temporary = temporary;
            this.backup = backup;
        }
    }

    private void deleteStorageFiles(String dbPath) {
        new File(dbPath + ".index").delete();
        new File(dbPath + ".store").delete();
        new File(dbPath + ".integrity").delete();
        new File(dbPath + ".integrity.delta").delete();
        deleteRecoveryLogs(dbPath);
    }

    private void deleteRecoveryLogs(String dbPath) {
        File database = new File(dbPath).getAbsoluteFile();
        File directory = database.getParentFile();
        if (directory == null) {
            directory = new File(".").getAbsoluteFile();
        }
        final String prefix = database.getName() + ".wal.";
        File[] logs = directory.listFiles();
        if (logs == null) {
            return;
        }
        for (File log : logs) {
            if (log.isFile() && log.getName().startsWith(prefix)) {
                log.delete();
            }
        }
    }

    @Override
    public boolean isClosed() {
        return bases.isEmpty();
    }

    @Override
    public String getStorageName() {
        return storageName;
    }

    @Override
    public IBase getBase(String context) throws Exception {
        if (!bases.containsKey(context)) {
            IBase base = new Base(user.getDatabaseDir() + storageName,
                    bases.size() + 1, locker, false, user);
            bases.put(context, base);
        }
        return bases.get(context);
    }

    @Override
    public IBase connect(String context) throws Exception {
        if (!isClosed()) {
            return bases.get(context);
        } else {
            return null;
        }
    }

    @Override
    public String getDescription() {
        return "DUMB data model";
    }

    @Override
    public Collection<String> list() {
        List<String> list = new ArrayList<String>();
        recurseList(user.getDatabaseDir(), "", list);
        return list;
    }

    private void recurseList(String path, String prefix, Collection list) {
        File[] dir = new File(path).listFiles();
        if (dir != null) {
            for (File f : dir) {
                if (!f.isDirectory()) {
                    if (f.getName().contains(".store")) {
                        list.add(prefix + f.getName().replaceAll(".store", ""));
                    }
                } else {
                    recurseList(path + Enums.FILE_SEPARATOR + f.getName(),
                            prefix + (prefix.isEmpty() ? "" : ".")
                                    + f.getName() + ".", list);
                }
            }
        }
    }

    private boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
}
