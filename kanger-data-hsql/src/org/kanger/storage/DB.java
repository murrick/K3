package org.kanger.storage;

import org.kanger.User;
import org.kanger.exception.CommandErrorException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public class DB implements IData {

    Connection connection = null;
    private String storageName = "";
    private Map<String, IBase> bases = new HashMap<>();
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

        String dbPath = user.getDatabaseDir();
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
    public void reindex(IReactor<String> reactor, IMind mind) throws Exception {

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
        return "HSQL Based data model";
    }

    @Override
    public Collection<String> list() {
        //TODO: Список доступных баз
        return new ArrayList<>();
    }

}
