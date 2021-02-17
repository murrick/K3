package org.kanger;

import org.kanger.exception.RuntimeErrorException;
import org.kanger.factory.*;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IData;
import org.kanger.interfaces.internal.IReactor;
import org.kanger.units.SysOp;

import java.io.*;
import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
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

    protected Mind clear(Mind mind) throws Exception {
        if (data != null && !data.isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                e.getValue().clear();
            }
            data.flush();
        }

        for (Mind m = mind; m != null; m = m.getNext()) {
            m.clearMind();
            mind = m;
        }
        return mind;
    }

    @Override
    public void remove(Mind mind, String name) throws Exception {
        data.remove(name);
        clear(mind);
    }

    @Override
    public void reindex(IReactor IReactor) {
        if (data != null && !data.isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                try {
                    IReactor.run(e.getKey());
                } catch (Exception ex) {
                }
                //TODO: Переиндексация БД
            }
//            use(data.getStorageName());
        }
    }

    @Override
    public long getUsedCacheSize() {
        long sz = 0;
        for (Map.Entry<String, IBase> e : storage.entrySet()) {
            sz += e.getValue().getUsedCacheSize();
        }
        return sz;
    }

    @Override
    public long getMaxCacheSize() {
        long sz = 0;
        for (Map.Entry<String, IBase> e : storage.entrySet()) {
            sz += e.getValue().getMaxCacheSize();
        }
        return sz;
    }

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

    @Override
    public Mind close(Mind mind) throws Exception {
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


    @Override
    public boolean isClosed() {
        return data == null || data.isClosed();
    }

    @Override
    public String getStorageName() {
        return data == null ? "" : data.getStorageName();
    }

    @Override
    public Collection<String> getStoragesList() {
        if (data != null) {
            return data.list();
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public void flush() throws Exception {
        if (data != null) {
            data.flush();
        }
    }

    @Override
    public Mind use(Mind mind, String name) throws Exception {

        if (data != null) {

            for (Mind m = mind; m != null; m = m.getNext()) {
                m.clearMind();
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

            mind.getTerms().transaction(null);
            mind.getDomains().transaction(null);
            mind.getFunctions().transaction(null);
            mind.getFValues().transaction(null);
            mind.getPredicates().transaction(null);
            mind.getRules().transaction(null);
            mind.getComments().transaction(null);
            mind.getTValues().transaction(null);
            mind.getTVars().transaction(null);
            mind.getLibrary().transaction(null);

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


    @Override
    public IData getData() throws RuntimeErrorException {
        if (data != null) {
            return data;
        } else {
            throw new RuntimeErrorException("DB module doesn't loaded");
        }
    }

    @Override
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
        return userSettings.getProperty("user.dir");
    }

    @Override
    public String getDatabaseDir() {
        return userSettings.getProperty("database.dir");
    }

    @Override
    public String getSourceDir() {
        return userSettings.getProperty("sources.dir");
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

    public SysOp getUdf() throws Exception {
        if (udf != null) {
            return (SysOp) udf.getConstructors()[0].newInstance();
        } else {
            return null;
        }
    }

    public void setUdf(Class udf) {
        this.udf = udf;
    }
}
