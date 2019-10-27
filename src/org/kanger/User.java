package org.kanger;

import org.kanger.factory.*;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IData;
import org.kanger.interfaces.IReactor;
import org.mozilla.javascript.Context;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class User {

    private Mind mind = null;
    private Map<String, IBase> storage = new HashMap<>();
    private Context scriptContext = null;
    private IData data = null;

    public User(IData data) {
        this.data = data;
        if (data != null) {
            data.init();
        }

        scriptContext = Context.enter();
        scriptContext.setLanguageVersion(Context.VERSION_1_7);
    }

    public void use(String name) throws Exception {

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

    public void setMind(Mind mind) {
        this.mind = mind;
    }

    public Mind getMind() {
        return mind;
    }

    public Context getScriptContext() {
        return scriptContext;
    }

    public IBase getStorage(String schema) {
        return storage.get(schema);
    }

    public void clear() throws Exception {
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

    public void reindex(IReactor IReactor) throws Exception {
        if (data != null && !data.isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                IReactor.run(e.getKey());
                //TODO: Переиндексация БД
            }
            use(data.getStorageName());
        }

    }

    public void close() throws Exception {
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
