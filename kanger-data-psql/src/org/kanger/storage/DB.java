package org.kanger.storage;

import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IData;

import java.io.IOException;
import java.sql.*;
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

        storageName = name.replaceAll("/", "_").replaceAll("\\\\", "_").replaceAll("-", "_");

        if (!isClosed()) {
            close();
        }

        String dbName = System.getProperty("database.name");
        if (dbName == null || dbName.isEmpty()) {
            dbName = "kanger";
        }
        String dbHost = System.getProperty("database.host");
        if (dbHost == null || dbHost.isEmpty()) {
            dbHost = "localhost";
        }
        String dbUsername = System.getProperty("database.username");
        if (dbUsername == null || dbUsername.isEmpty()) {
            dbUsername = "kanger";
        }
        String dbPassword = System.getProperty("database.password");
        if (dbPassword == null || dbPassword.isEmpty()) {
            dbPassword = "kanger";
        }

        connection = DriverManager.getConnection("jdbc:postgresql://" + dbHost + "/" + dbName,
                dbUsername,
                dbPassword);

        try (Statement st = connection.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) AS cnt FROM pg_namespace WHERE nspname = '" + storageName + "'");
            if (rs.next() && rs.getInt(1) == 0) {
                st.executeUpdate("CREATE SCHEMA " + storageName + ";");
            }
            st.executeUpdate("SET search_path TO '" + storageName + "';");
        }

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
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("SET search_path TO public;");
                st.executeUpdate("DROP SCHEMA " + storageName + " CASCADE;");
            }
            close();
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

}
