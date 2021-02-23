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

package org.kanger;

import org.kanger.exception.RuntimeErrorException;
import org.kanger.factory.*;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IData;
import org.kanger.units.Operation;

import java.io.*;
import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class User implements IUser {

    private long id = -1L;
    private final Object locker = new Object();
    Properties userSettings = new Properties();
    private IData data = null;
    private Class udf = null;
    private Map<String, IBase> storage = new HashMap<>();
    private Map<String, Long> counters = new HashMap<>();
    private long lastId = 0L;
    private String sourceFileName = "mind.k";


    public IBase getStorage(String schema) {
        return storage.get(schema);
    }

//    @Override
//    public IBase connect(String schema) throws Exception {
//        if (data != null && !data.isClosed()) {
//            return data.connect(schema);
//        } else {
//            return null;
//        }
//    }

    protected IMind clear(IMind mind) throws Exception {
        if (data != null && !data.isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                e.getValue().clear();
            }
            data.flush();
        }

        for (IMind m = mind; m != null; m = m.getNext()) {
            ((Mind) m).clearMind();
            mind = m;
        }
        return mind;
    }

    public IMind remove(IMind mind, String name) throws Exception {
        data.remove(name);
        return clear(mind);
    }

    public IMind reindex(IReactor reactor, IMind mind, String name) throws Exception {
        boolean reopened = true;
        String saveName = "";
        if (isClosed()) {
            reopened = false;
        } else {
            saveName = data.getStorageName();
            close(mind);
        }
        use(mind, name);
        if (data != null && !data.isClosed()) {
            data.reindex(reactor, mind);
        }

        close(mind);
        if (reopened) {
            use(mind, saveName);
        }
        return mind;
    }

//    public long getUsedCacheSize() {
//        long sz = 0;
//        for (Map.Entry<String, IBase> e : storage.entrySet()) {
//            sz += e.getValue().getUsedCacheSize();
//        }
//        return sz;
//    }
//
//    public long getMaxCacheSize() {
//        long sz = 0;
//        for (Map.Entry<String, IBase> e : storage.entrySet()) {
//            sz += e.getValue().getMaxCacheSize();
//        }
//        return sz;
//    }

//    @Override
//    public void clearCache() {
//        for (Map.Entry<String, IBase> e : storage.entrySet()) {
//            e.getValue().clearCache();
//        }
//    }

    public long lastId(String schema) {
        if (isClosed()) {
            synchronized (this) {
                if (!counters.containsKey(schema)) {
                    counters.put(schema, 0L);
                }
                return counters.get(schema);
            }
        } else {
            return storage.get(schema).lastId();
        }
    }

    public long nextId(String schema) {
        if (isClosed()) {
            synchronized (this) {
                if (!counters.containsKey(schema)) {
                    counters.put(schema, 0L);
                }
                long id = counters.get(schema);
                counters.put(schema, id + 1);
                return id;
            }
        } else {
            return storage.get(schema).nextId();
        }
    }

    public void clearCounters(String schema) {
        counters.put(schema, 0L);
    }

    public long lastId() {
        return lastId;
    }

    public long nextId() {
        return lastId++;
    }

    public IMind close(IMind mind) throws Exception {
        if (data != null && !data.isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                e.getValue().clearCache();
            }
            data.close();
        }

//        return mind;
        if (mind != null) {
            return clear(mind);
        } else {
            return null;
        }
    }


    public boolean isClosed() {
        return data == null || data.isClosed();
    }

    public String getStorageName() {
        return data == null ? "" : data.getStorageName();
    }

    public Collection<String> getStoragesList() {
        if (data != null) {
            return data.list();
        } else {
            return new ArrayList<>();
        }
    }

    public void flush() throws Exception {
        if (data != null) {
            data.flush();
        }
    }

    public IMind use(IMind mind, String name) throws Exception {

        if (data != null) {

            for (IMind m = mind; m != null; m = m.getNext()) {
                ((Mind) m).clearMind();
                mind = m;
            }
            if (mind == null) {
                mind = new Mind(this);
            }

            data.use(name);

            storage.put(DictionaryFactory.SCHEMA, data.getBase(DictionaryFactory.SCHEMA));
            storage.put(DomainFactory.SCHEMA, data.getBase(DomainFactory.SCHEMA));
            storage.put(FunctionFactory.SCHEMA, data.getBase(FunctionFactory.SCHEMA));
            storage.put(PredicateFactory.SCHEMA, data.getBase(PredicateFactory.SCHEMA));
            storage.put(RuleFactory.SCHEMA, data.getBase(RuleFactory.SCHEMA));
            storage.put(TVariableFactory.SCHEMA, data.getBase(TVariableFactory.SCHEMA));
            storage.put(LibraryFactory.SCHEMA, data.getBase(LibraryFactory.SCHEMA));

            storage.put(TValueFactory.SCHEMA, data.getBase(TValueFactory.SCHEMA));
            storage.put(FValueFactory.SCHEMA, data.getBase(FValueFactory.SCHEMA));

            storage.put(CommentFactory.SCHEMA, data.getBase(CommentFactory.SCHEMA));

            ((DictionaryFactory) mind.getTerms()).transaction(null);
            ((Mind) mind).getDomains().transaction(null);
            ((Mind) mind).getFunctions().transaction(null);
            ((Mind) mind).getFValues().transaction(null);
            ((PredicateFactory) mind.getPredicates()).transaction(null);
            ((RuleFactory) mind.getRules()).transaction(null);
            ((CommentFactory) mind.getComments()).transaction(null);
            ((Mind) mind).getTValues().transaction(null);
            ((Mind) mind).getTVars().transaction(null);
            ((LibraryFactory) mind.getLibrary()).transaction(null);

//            Mind m = new Mind(mind);
//            m.link(null, true);
//            Boolean ar = m.analise(null, true);
//
//            if (ar) {
//                m.getLog().add(LogMode.ANALIZER, "ERROR: Collisions in Program");
//                mind.release(m);
//                close();
//                throw new RuntimeErrorException("Collisions in Program");
//            } else {
//                m.getLog().add(LogMode.ANALIZER, "SUCCESS: No Collisions in Program");
//                mind.commit(m);
//            }

            return mind;

        } else {
            throw new RuntimeErrorException("DB module doesn't loaded");
        }
    }


    public IData getData() throws RuntimeErrorException {
        if (data != null) {
            return data;
        } else {
            throw new RuntimeErrorException("DB module doesn't loaded");
        }
    }

    public void setData(IData db) {
        data = db;
    }

    @Override
    public String getProperty(String key, String val) {
        if (userSettings.containsKey(key)) {
            return userSettings.getProperty(key);
        } else {
            setProperty(key, val);
        }
        return val;
    }

    @Override
    public void setProperty(String key, String val) {
        userSettings.setProperty(key, val);
        if (userSettings.containsKey("user.dir")) {
            String confName = userSettings.getProperty("user.dir") + "kanger.conf";
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(confName))) {
                userSettings.store(bw, new Date().toString());
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }
    }

    @Override
    public String getUserDir() {
        if (userSettings.containsKey("user.dir")) {
            return userSettings.getProperty("user.dir");
        } else {
            return "";
        }
    }

    @Override
    public String getDatabaseDir() {
        if (userSettings.containsKey("database.dir")) {
            return userSettings.getProperty("database.dir");
        } else {
            return "";
        }
    }

    @Override
    public String getSourceDir() {
        if (userSettings.containsKey("sources.dir")) {
            return userSettings.getProperty("sources.dir");
        } else {
            return "";
        }
    }

    @Override
    public void setUserDir(String dir) {
        userSettings.setProperty("user.dir", dir);
    }

    @Override
    public void setDatabaseDir(String dir) {
        userSettings.setProperty("database.dir", dir);
    }

    @Override
    public void setSourceDir(String dir) {
        userSettings.setProperty("sources.dir", dir);
    }

    @Override
    public void loadProperties(String confName) throws Exception {
        if (new File(confName).exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(confName))) {
                userSettings.load(br);
            }
        }
    }

    @Override
    public boolean containsProperty(String key) {
        return userSettings.containsKey(key);
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    public Operation getUdf() throws Exception {
        if (udf != null) {
            return (Operation) udf.getConstructors()[0].newInstance();
        } else {
            return null;
        }
    }

    public void setUdf(Class udf) {
        this.udf = udf;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }
}
