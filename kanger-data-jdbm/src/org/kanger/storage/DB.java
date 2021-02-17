package org.kanger.storage;

import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import org.kanger.enums.Enums;
import org.kanger.exception.CommandErrorException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public class DB implements IData {

    RecordManager connection = null;
    private String storageName = "";
    private Map<String, IBase> bases = new HashMap<>();
    private IUser user = null;

    private static ResourceBundle msg = ResourceBundle.getBundle("messages");

    @Override
    public void init(IUser user) {
        this.user = user;
        user.setData(this);
    }

    @Override
    public void use(String name) throws Exception {
        if (!isClosed()) {
            close();
        }

        String dbPath = user.getDatabaseDir();
        dbPath += name;
        dbPath = dbPath.replaceAll("/|\\\\", String.format("\\%s", Enums.FILE_SEPARATOR));
        String[] tmp = dbPath.split(String.format("\\%s", Enums.FILE_SEPARATOR));
        if (tmp.length > 1) {
            String path = dbPath.substring(0, dbPath.length() - tmp[tmp.length - 1].length());
            Files.createDirectories(Paths.get(path));
        }

        connection = RecordManagerFactory.createRecordManager(dbPath);

        storageName = name;
    }

    @Override
    public void close() throws Exception {
        if (connection != null) {
            connection.close();
            bases.clear();
            storageName = "";
            connection = null;
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        if (connection != null) {
            connection.commit();
        }
    }

    @Override
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

        String dbPath = user.getDatabaseDir();
        dbPath += tmp;
        String dbName = Paths.get(dbPath).getFileName().toString();
        dbPath = dbPath.substring(0, dbPath.length() - dbName.length());

        File[] allContents = new File(dbPath).listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                if (file.getName().startsWith(dbName)) {
                    file.delete();
                }
            }
        }
    }

    @Override
    public boolean isClosed() {
        return connection == null;
    }


    @Override
    public String getStorageName() {
        return storageName;
    }

    @Override
    public IBase getBase(String context) throws Exception {
        if (!isClosed()) {
            if (!bases.containsKey(context)) {
                IBase base = new Base(connection, context, user);
                bases.put(context, base);
            }
            return bases.get(context);
        } else {
            throw new RuntimeErrorException("Database doesn't opened");
        }
    }

    @Override
    public IBase connect(String context) throws Exception {
        return getBase(context);
    }

    @Override
    public String getDescription() {
        return "JDBM Based data model";
    }

    @Override
    public Collection<String> list() {
        //TODO: Список доступных баз
        return new ArrayList<>();
    }

}
