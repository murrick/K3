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

    private Mind mind = null;
    private Map<String, IBase> storage = new HashMap<>();
    private Map<String, Long> counters = new HashMap<>();

    private static IData data = null;

    public User(Mind mind) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (mind == null) {
            this.mind = new Mind(this);
        } else {
            this.mind = mind;
        }
        data = Global.getData();
    }

    public void use(String name) throws IOException, RuntimeErrorException {

        if (data != null) {

            data.use(name);

            storage.put(DictionaryFactory.SCHEMA, data.getBase(DictionaryFactory.SCHEMA));
            storage.put(DomainFactory.SCHEMA, data.getBase(DomainFactory.SCHEMA));
            storage.put(FunctionFactory.SCHEMA, data.getBase(FunctionFactory.SCHEMA));
            storage.put(FValueFactory.SCHEMA, data.getBase(FValueFactory.SCHEMA));
            storage.put(PredicateFactory.SCHEMA, data.getBase(PredicateFactory.SCHEMA));
            storage.put(RightFactory.SCHEMA, data.getBase(RightFactory.SCHEMA));
            storage.put(RightFactory.SCHEMA_STORED, data.getBase(RightFactory.SCHEMA_STORED));
            storage.put(TValueFactory.SCHEMA, data.getBase(TValueFactory.SCHEMA));
            storage.put(TVariableFactory.SCHEMA, data.getBase(TVariableFactory.SCHEMA));
            storage.put(LibraryFactory.SCHEMA, data.getBase(LibraryFactory.SCHEMA));

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

        }
    }

    public Mind getMind() {
        return mind;
    }

    public void setMind(Mind mind) {
        this.mind = mind;
    }

    public IBase getStorage(String schema) {
        return storage.get(schema);
    }

    public void clear() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
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
            use(data.getStorageName());
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
    public long lastId(String context) {
        if (isClosed()) {
            synchronized (this) {
                if (!counters.containsKey(context)) {
                    counters.put(context, 0L);
                }
                return counters.get(context);
            }
        } else {
            return storage.get(context).lastId();
        }
    }

    @Override
    public long nextId(String context) {
        if (isClosed()) {
            synchronized (this) {
                if (!counters.containsKey(context)) {
                    counters.put(context, 0L);
                }
                long id = counters.get(context);
                counters.put(context, id + 1);
                return id;
            }
        } else {
            return storage.get(context).nextId();
        }
    }

    @Override
    public void close() throws IOException {
        if (data != null && !data.isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                if (e.getValue().getRoot() != null) {
                    if (e.getValue().getRoot().getPrev() != null) {
                        e.getValue().getRoot().getPrev().setNext(null);
                    }
                    e.getValue().getRoot().setPrev(null);
                    e.getValue().getRoot().update();
                }
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

    public void flush() throws IOException {
        if (data != null) {
            data.flush();
        }
    }

}
