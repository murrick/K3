package org.kanger.storage;

import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class DB implements IData {

    Connection connection = null;
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

        connection = DriverManager.getConnection("jdbc:hsqldb:file:"
                + dbPath, "SA", "");

        storageName = name;
    }

    @Override
    public void close() throws Exception {
        if (connection != null) {
            connection.close();
            bases.clear();
            storageName = "";
        }
    }

    @Override
    public synchronized void flush() throws IOException {
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
        try {
            return connection == null || connection.isClosed();
        } catch (SQLException throwables) {
            return true;
        }
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

    @Override
    public IBase connect(String context) throws Exception {
        return getBase(context);
    }

    @Override
    public String getDescription() {
        return "HSQL Based data model";
    }

}
