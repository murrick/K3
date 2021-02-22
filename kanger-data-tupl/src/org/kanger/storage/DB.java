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

import org.cojen.tupl.Database;
import org.cojen.tupl.DatabaseConfig;
import org.cojen.tupl.DurabilityMode;
import org.kanger.User;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public class DB implements IData {

    DatabaseConfig config = null;
    private String storageName = "";
    private Database db = null;
    private Map<String, IBase> bases = new HashMap<>();
    private IUser user = null;

    @Override
    public void init(IUser user) {
        this.user = user;
        ((User) user).setData(this);
        config = new DatabaseConfig()
                .minCacheSize(100_000_000)
                .durabilityMode(DurabilityMode.NO_FLUSH);
    }

    @Override
    public void use(String name) throws IOException {
        if (!isClosed()) {
            close();
        }

        String dbPath = user.getDatabaseDir();
        dbPath += name;

        config.baseFilePath(dbPath);
        db = Database.open(config);

        storageName = name;
    }

    @Override
    public void close() throws IOException {
        if (db != null) {
            db.shutdown();
            db = null;
            bases.clear();
            storageName = "";
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        if (!isClosed()) {
            db.checkpoint();
        }
    }

    @Override
    public void remove(String storageName) throws IOException {
        if (!isClosed()) {
            String dbPath = user.getDatabaseDir();
            dbPath += storageName;
            close();
            new File(dbPath + ".db").delete();
        }
    }

    @Override
    public void reindex(IReactor<String> reactor, IMind mind) throws Exception {

    }

    @Override
    public boolean isClosed() {
        return db == null;
    }


    @Override
    public String getStorageName() {
        return storageName;
    }

    @Override
    public IBase getBase(String context) throws Exception {
        if (db != null) {
            if (!bases.containsKey(context)) {
                IBase base = new Base(db, context, user);
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
        return "Cojen Tupl based data model";
    }

    @Override
    public Collection<String> list() {
        //TODO: Список доступных баз
        return new ArrayList<>();
    }

}
