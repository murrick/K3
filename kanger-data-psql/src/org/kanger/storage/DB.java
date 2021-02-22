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
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger.storage;

import org.kanger.User;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IData;

import java.io.IOException;
import java.sql.*;
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

        storageName = name.replaceAll("/", "_").replaceAll("\\\\", "_").replaceAll("-", "_");

        if (!isClosed()) {
            close();
        }

        String dbName = user.getProperty("database.name", "kanger");
        String dbHost = user.getProperty("database.host", "localhost");
        String dbUsername = user.getProperty("database.username", "kanger");
        String dbPassword = user.getProperty("database.password", "kanger");

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
    public void remove(String storageName) throws Exception {
        if (!isClosed()) {
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("SET search_path TO public;");
                st.executeUpdate("DROP SCHEMA " + storageName + " CASCADE;");
            }
            close();
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
        return "PostgreSQL based data model";
    }

    @Override
    public Collection<String> list() {
        //TODO: Список доступных баз
        return new ArrayList<>();
    }

}
