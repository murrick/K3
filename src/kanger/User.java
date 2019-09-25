package kanger;

import kanger.factory.*;
import kanger.interfaces.ICache;
import kanger.interfaces.Reactor;
import kanger.storage.Base;
import org.cojen.tupl.Database;
import org.cojen.tupl.DatabaseConfig;
import org.cojen.tupl.DurabilityMode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class User {

    private Mind mind = null;
    private Map<String, Base> storage = new HashMap<>();
    private String storageName = "";
    private Database db = null;

    public User() throws IOException {
    }

    public void use(String name) throws Exception {
        if (!isClosed()) {
            close();
        }

        DatabaseConfig config = new DatabaseConfig()
                .baseFilePath(name)
                .minCacheSize(100_000_000)
                .durabilityMode(DurabilityMode.NO_FLUSH);

        db = Database.open(config);

        storage.put(DictionaryFactory.SCHEMA, new Base(this, DictionaryFactory.SCHEMA, null));
        storage.put(DomainFactory.SCHEMA, new Base(this, DomainFactory.SCHEMA, null));
        storage.put(FunctionFactory.SCHEMA, new Base(this, FunctionFactory.SCHEMA, null));
        storage.put(FValueFactory.SCHEMA, new Base(this, FValueFactory.SCHEMA, null));
        storage.put(PredicateFactory.SCHEMA, new Base(this, PredicateFactory.SCHEMA, null));
        storage.put(RightFactory.SCHEMA, new Base(this, RightFactory.SCHEMA, null));
        storage.put(RightFactory.SCHEMA_STORED, new Base(this, RightFactory.SCHEMA_STORED, null));
        storage.put(TValueFactory.SCHEMA, new Base(this, TValueFactory.SCHEMA, null));
        storage.put(TVariableFactory.SCHEMA, new Base(this, TVariableFactory.SCHEMA, null));

        for (Map.Entry<String, Base> e : storage.entrySet()) {
            //TODO: Открытие БД
        }

        storageName = name;
//        mind.getRights().reindex();
    }

    public void close() throws IOException {
        if (db != null) {
            db.checkpoint();
            db.close(null);
            db = null;
        }
    }

    public void remove() throws IOException {
        if (!isClosed()) {
            for (Map.Entry<String, Base> e : storage.entrySet()) {
                //TODO: Удаление БД
            }
        }
    }

    public void reindex(Reactor reactor) throws Exception {
        if (!isClosed()) {
            for (Map.Entry<String, Base> e : storage.entrySet()) {
                reactor.run(e.getKey());
                //TODO: Переиндексация БД
            }
            use(storageName);
        }

    }

    public void clear() throws Exception {
        if (!isClosed()) {
            for (Map.Entry<String, Base> e : storage.entrySet()) {
                e.getValue().clear();
            }
        }
    }

    public void flush() throws IOException {
        if (!isClosed()) {
            for (Map.Entry<String, Base> e : storage.entrySet()) {
                //TODO: flush БД
            }
        }
    }

    public boolean isClosed() {
        return false;
    }

    public ICache getStorage(String schema) {
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
}
