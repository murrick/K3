package org.kanger.storage;

import org.kanger.enums.Enums;
import org.kanger.exception.CommandErrorException;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IData;
import org.kanger.interfaces.IUser;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public class DB implements IData {

    //    String dbPath = "";
    private static final Object locker = new Object();
    private String storageName = "";
    private Map<String, IBase> bases = new HashMap<>();
    private IUser user = null;

    @Override
    public void init(IUser user) {
        this.user = user;
        user.setData(this);
    }

    @Override
    public void use(String name) throws IOException {
        if (!isClosed()) {
            close();
        }
        storageName = name;
    }

    @Override
    public void close() throws IOException {
        for (IBase b : bases.values()) {
            ((Base) b).close();
        }
        bases.clear();
        storageName = "";
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

        new File(dbPath + ".index").delete();
        new File(dbPath + ".store").delete();

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
            IBase base = new Base(user.getDatabaseDir() + storageName, bases.size() + 1, locker, false, user);
            bases.put(context, base);
        }
        return bases.get(context);
    }

    @Override
    public IBase connect(String context) throws Exception {
        if (!isClosed()) {
            return bases.get(context);
//            IBase base = new Base(dbPath + context, true);
//            return base;
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
        List<String> list = new ArrayList<>();
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
                    recurseList(path + Enums.FILE_SEPARATOR + f.getName(), prefix + (prefix.isEmpty() ? "" : ".") + f.getName() + ".", list);
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
