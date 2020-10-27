package org.kanger.storage;

import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import org.hsqldb.lib.FileUtil;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class DB implements IData {

    RecordManager connection = null;
    private String storageName = "";
    private Map<String, IBase> bases = new HashMap<>();

    @Override
    public void init() {
    }

    @Override
    public void use(String name) throws Exception {
        if (!isClosed()) {
            close();
        }

        String dbPath = System.getProperty("database.dir");
        if (dbPath == null || dbPath.isEmpty()) {
            dbPath = System.getProperty("user.dir");
        }
        if (!dbPath.endsWith("/") && !dbPath.endsWith("\\")) {
            dbPath += File.separatorChar;
        }
        dbPath += name;
        dbPath = dbPath.replaceAll("/|\\\\", String.format("\\%s", File.separatorChar));
        String[] tmp = dbPath.split(String.format("\\%s", File.separatorChar));
        if (tmp.length > 1) {
            String path = dbPath.substring(0, dbPath.length() - tmp[tmp.length - 1].length());
            FileUtil.makeDirectories(path);
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
    public void remove() throws Exception {
        if (!isClosed()) {

            String tmp = storageName;
            close();

            String dbPath = System.getProperty("database.dir");
            if (dbPath == null || dbPath.isEmpty()) {
                dbPath = System.getProperty("user.dir");
            }
            if (!dbPath.endsWith("/") && !dbPath.endsWith("\\")) {
                dbPath += File.separatorChar;
            }
            dbPath += tmp;
            String name = Paths.get(dbPath).getFileName().toString();
            dbPath = dbPath.substring(0, dbPath.length() - name.length());


            File[] allContents = new File(dbPath).listFiles();
            if (allContents != null) {
                for (File file : allContents) {
                    if (file.getName().startsWith(name)) {
                        file.delete();
                    }
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
                IBase base = new Base(connection, context);
                bases.put(context, base);
            }
            return bases.get(context);
        } else {
            throw new RuntimeErrorException("Database doesn't opened");
        }
    }

}
