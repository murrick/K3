package kanger;

import kanger.factory.*;
import kanger.storage.Storage;
import kanger.units.TValue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class User {

    private Mind mind = null;
    private Map<String, Storage> storage = null;
    private String storageName = "";

    public User() {
    }

    public void use(String name) throws IOException {
        if(!isClosed()) {
            close();
        }
        storage = new HashMap<>();
        storage.put(DatabaseFactory.SCHEMA, new Storage());
        storage.put(DictionaryFactory.SCHEMA, new Storage());
        storage.put(DomainFactory.SCHEMA, new Storage());
        storage.put(FunctionFactory.SCHEMA, new Storage());
        storage.put(FValueFactory.SCHEMA, new Storage());
        storage.put(PredicateFactory.SCHEMA, new Storage());
        storage.put(RightFactory.SCHEMA, new Storage());
        storage.put(TreeFactory.SCHEMA, new Storage());
        storage.put(TValueFactory.SCHEMA, new Storage());
        storage.put(TVariableFactory.SCHEMA, new Storage());

        for (Map.Entry<String, Storage> e : storage.entrySet()) {
            e.getValue().open(name + "." + e.getKey());
        }

        storageName = name;
    }

    public void close() throws IOException {
        if (!isClosed()) {
            for (Map.Entry<String, Storage> e : storage.entrySet()) {
                e.getValue().close();
            }
        }
        storage = null;
    }

    public void remove() throws IOException {
        if(!isClosed()) {
            for (Map.Entry<String, Storage> e : storage.entrySet()) {
                e.getValue().remove();
            }
        }
        storage = null;
    }

    public void clear() throws IOException {
        if (!isClosed()) {
            for (Map.Entry<String, Storage> e : storage.entrySet()) {
                e.getValue().clear();
            }
        }
    }

    public void flush() throws IOException {
        if (!isClosed()) {
            for (Map.Entry<String, Storage> e : storage.entrySet()) {
                e.getValue().flush();
            }
        }
    }

    public boolean isClosed() {
        return storage == null;
    }

    public Storage getStorage(String schema) {
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
