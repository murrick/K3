package org.kanger;

import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.factory.*;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IData;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class User implements IUser {

    //    private Mind mind = null;
    private Map<String, IBase> storage = new HashMap<>();
    private Map<String, Long> counters = new HashMap<>();
    private long lastId = 0L;

    private static IData data = null;

    public User() throws RuntimeErrorException {
//        if (mind == null) {
//            this.mind = new Mind(this);
//        } else {
//            this.mind = mind;
//        }
        data = Global.getData();
    }

    public Mind use(Mind mind, String name) throws IOException, RuntimeErrorException, OutOfBufferException, ClassNotFoundException {

        if (data != null) {

            if (mind == null) {
                mind = new Mind(this);
            }
            data.use(name);

            storage.put(DictionaryFactory.SCHEMA, data.getBase(DictionaryFactory.SCHEMA));
            storage.put(DomainFactory.SCHEMA, data.getBase(DomainFactory.SCHEMA));
            storage.put(FunctionFactory.SCHEMA, data.getBase(FunctionFactory.SCHEMA));
            storage.put(FValueFactory.SCHEMA, data.getBase(FValueFactory.SCHEMA));
            storage.put(PredicateFactory.SCHEMA, data.getBase(PredicateFactory.SCHEMA));
            storage.put(RightFactory.SCHEMA, data.getBase(RightFactory.SCHEMA));
            storage.put(TValueFactory.SCHEMA, data.getBase(TValueFactory.SCHEMA));
            storage.put(TVariableFactory.SCHEMA, data.getBase(TVariableFactory.SCHEMA));
            storage.put(LibraryFactory.SCHEMA, data.getBase(LibraryFactory.SCHEMA));
            storage.put(TSolveFactory.SCHEMA, data.getBase(TSolveFactory.SCHEMA));

            while (mind.getNext() != null) {
                mind = mind.getNext();
            }

            mind.getTerms().transaction(null);
            mind.getDomains().transaction(null);
            mind.getFunctions().transaction(null);
            mind.getFValues().transaction(null);
            mind.getPredicates().transaction(null);
            mind.getRights().transaction(null);
            mind.getTValues().transaction(null);
            mind.getTVars().transaction(null);
            mind.getLibrary().transaction(null);
//            mind.getTSolves().transaction(null);

            return mind;

        } else {
            throw new RuntimeErrorException("DB module doens't loaded");
        }
    }

    public IBase getStorage(String schema) {
        return storage.get(schema);
    }

    public void clear(Mind mind) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (data != null && !data.isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                e.getValue().clear();
            }
            data.flush();
        }

        for (Mind m = mind; m != null; m = m.getNext()) {
            m.clear();
            mind = m;
        }
    }

    public void remove() {
        if (data != null && !data.isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                //TODO: Удаление БД
            }
        }
    }

    public void reindex(IReactor IReactor) throws IOException, RuntimeErrorException {
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

    @Override
    public void clearCache() {
        for (Map.Entry<String, IBase> e : storage.entrySet()) {
            e.getValue().clearCache();
        }
    }

    @Override
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

    @Override
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

    @Override
    public void clearCounters(String schema) {
        counters.put(schema, 0L);
    }

    @Override
    public long lastId() {
        return lastId;
    }

    @Override
    public long nextId() {
        return lastId++;
    }

    @Override
    public void close() throws IOException {
        if (data != null && !data.isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                e.getValue().clearCache();
            }
            data.close();
        }
    }


    public boolean isClosed() {
        return data == null || data.isClosed();
    }

    public String getStorageName() {
        return data == null ? "" : data.getStorageName();
    }

    @Override
    public void flush() throws IOException {
        if (data != null) {
            data.flush();
        }
    }

}
