package org.kanger;

import org.cojen.tupl.Database;
import org.cojen.tupl.DatabaseConfig;
import org.cojen.tupl.DurabilityMode;
import org.kanger.factory.*;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IReactor;
import org.kanger.storage.Base;
import org.mozilla.javascript.Context;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class User {

    private Mind mind = null;
    private Map<String, IBase> storage = new HashMap<>();
    private String storageName = "";
    private Database db = null;
    private Context scriptContext = null;

    DatabaseConfig config = new DatabaseConfig()
            .minCacheSize(100_000_000)
            .durabilityMode(DurabilityMode.NO_FLUSH);

    public User() {
        scriptContext = Context.enter();
        scriptContext.setLanguageVersion(Context.VERSION_1_7);
    }

    public void use(String name) throws Exception {
        if (!isClosed()) {
            close();
        }

        String dbPath = System.getProperty("database.dir");
        if (dbPath == null || dbPath.isEmpty()) {
            dbPath = System.getProperty("user.dir");
        }
        if (!dbPath.endsWith("/") && !dbPath.endsWith("\\")) {
            dbPath += File.separatorChar;
        }
        dbPath += name;

        config.baseFilePath(dbPath);
        db = Database.open(config);

        storageName = name;

        storage.put(DictionaryFactory.SCHEMA, new Base(this, DictionaryFactory.SCHEMA));
        storage.put(DomainFactory.SCHEMA, new Base(this, DomainFactory.SCHEMA));
        storage.put(FunctionFactory.SCHEMA, new Base(this, FunctionFactory.SCHEMA));
        storage.put(FValueFactory.SCHEMA, new Base(this, FValueFactory.SCHEMA));
        storage.put(PredicateFactory.SCHEMA, new Base(this, PredicateFactory.SCHEMA));
        storage.put(RightFactory.SCHEMA, new Base(this, RightFactory.SCHEMA));
        storage.put(RightFactory.SCHEMA_STORED, new Base(this, RightFactory.SCHEMA_STORED));
        storage.put(TValueFactory.SCHEMA, new Base(this, TValueFactory.SCHEMA));
        storage.put(TVariableFactory.SCHEMA, new Base(this, TVariableFactory.SCHEMA));
        storage.put(LibraryFactory.SCHEMA, new Base(this, LibraryFactory.SCHEMA));

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

    public void close() throws Exception {
        if (db != null) {
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

//            db.checkpoint();
//            db.close(null);
            db.shutdown();
            db = null;
        }
    }

    public void remove() throws IOException {
        if (!isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                //TODO: Удаление БД
            }
        }
    }

    public void reindex(IReactor IReactor) throws Exception {
        if (!isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                IReactor.run(e.getKey());
                //TODO: Переиндексация БД
            }
            use(storageName);
        }

    }

    public void clear() throws Exception {
        if (!isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                e.getValue().clear();
            }
            flush();
        }

        for (Mind m = mind; m != null; m = m.getNext()) {
            m.clear();
            mind = m;
        }
    }

    public void flush() throws IOException {
        if (!isClosed()) {
            db.checkpoint();
        }
    }

    public boolean isClosed() {
        return db == null;
    }

    public IBase getStorage(String schema) {
        return storage.get(schema);
    }

    public String getStorageName() {
        return storageName;
    }

    public void setMind(Mind mind) {
        this.mind = mind;
    }

    public Mind getMind() {
        return mind;
    }

    public Database getDb() {
        return db;
    }

    public Context getScriptContext() {
        return scriptContext;
    }

}
