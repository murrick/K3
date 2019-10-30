package org.kanger;

import org.kanger.exception.OutOfBufferException;
import org.kanger.factory.*;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IData;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.units.SysOp;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class User implements IUser {

    private Mind mind = null;
    private Map<String, IBase> storage = new HashMap<>();

    private IData data = null;
    private Class udf = null;

    public User(IData data, Class udf) throws IOException, ClassNotFoundException, OutOfBufferException {
        this.data = data;
        if (data != null) {
            data.init();
        }
        this.udf = udf;
        mind = new Mind(this);
    }

    public void use(String name) throws IOException {

        if (data != null) {
            data.use(name);

            storage.put(DictionaryFactory.SCHEMA, data.counstructBase(this, DictionaryFactory.SCHEMA));
            storage.put(DomainFactory.SCHEMA, data.counstructBase(this, DomainFactory.SCHEMA));
            storage.put(FunctionFactory.SCHEMA, data.counstructBase(this, FunctionFactory.SCHEMA));
            storage.put(FValueFactory.SCHEMA, data.counstructBase(this, FValueFactory.SCHEMA));
            storage.put(PredicateFactory.SCHEMA, data.counstructBase(this, PredicateFactory.SCHEMA));
            storage.put(RightFactory.SCHEMA, data.counstructBase(this, RightFactory.SCHEMA));
            storage.put(RightFactory.SCHEMA_STORED, data.counstructBase(this, RightFactory.SCHEMA_STORED));
            storage.put(TValueFactory.SCHEMA, data.counstructBase(this, TValueFactory.SCHEMA));
            storage.put(TVariableFactory.SCHEMA, data.counstructBase(this, TVariableFactory.SCHEMA));
            storage.put(LibraryFactory.SCHEMA, data.counstructBase(this, LibraryFactory.SCHEMA));

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

    public void clear() throws IOException, ClassNotFoundException, OutOfBufferException {
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

    public void remove() throws IOException {
        if (data != null && !data.isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                //TODO: Удаление БД
            }
        }
    }

    public void reindex(IReactor IReactor) throws IOException {
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

    @Override
    public SysOp getUdf() {
        try {
            return (SysOp) udf.getConstructors()[0].newInstance(this);
        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace(System.err);
            return null;
        }
    }


}
