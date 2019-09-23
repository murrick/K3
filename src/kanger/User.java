package kanger;

import kanger.exception.RuntimeErrorException;
import kanger.factory.*;
import kanger.interfaces.Reactor;
import kanger.storage.Cache;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class User {

    private Mind mind = null;
    private Map<String, Cache> storage = null;
    private String storageName = "";

    public User() {
    }

    public void use(String name) throws IOException, RuntimeErrorException {
        if (!isClosed()) {
            close();
        }
        storage = new HashMap<>();
        storage.put(DictionaryFactory.SCHEMA, new Cache());
        storage.put(DomainFactory.SCHEMA, new Cache());
        storage.put(FunctionFactory.SCHEMA, new Cache());
        storage.put(FValueFactory.SCHEMA, new Cache());
        storage.put(PredicateFactory.SCHEMA, new Cache());
        storage.put(RightFactory.SCHEMA, new Cache());
        storage.put(TValueFactory.SCHEMA, new Cache());
        storage.put(TVariableFactory.SCHEMA, new Cache());

        for (Map.Entry<String, Cache> e : storage.entrySet()) {
            //TODO: Открытие БД
        }

        storageName = name;
//        mind.getRights().reindex();
    }

    public void close() throws IOException {
        if (!isClosed()) {
            for (Map.Entry<String, Cache> e : storage.entrySet()) {
                //TODO: Закрытие БД
            }
        }
        storage = null;
    }

    public void remove() throws IOException {
        if (!isClosed()) {
            for (Map.Entry<String, Cache> e : storage.entrySet()) {
                //TODO: Удаление БД
            }
        }
        storage = null;
    }

    public void reindex(Reactor reactor) throws IOException, RuntimeErrorException {
        if (!isClosed()) {
            for (Map.Entry<String, Cache> e : storage.entrySet()) {
                reactor.run(e.getKey());
                //TODO: Переиндексация БД
            }
            use(storageName);
        }

    }

    public void clear() throws IOException {
        if (!isClosed()) {
            for (Map.Entry<String, Cache> e : storage.entrySet()) {
                e.getValue().clear();
            }
        }
    }

    public void flush() throws IOException {
        if (!isClosed()) {
            for (Map.Entry<String, Cache> e : storage.entrySet()) {
                //TODO: flush БД
            }
        }
    }

    public boolean isClosed() {
        return storage == null;
    }

    public Cache getStorage(String schema) {
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

}
