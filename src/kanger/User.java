package kanger;

import kanger.exception.RuntimeErrorException;
import kanger.factory.*;
import kanger.interfaces.Reactor;
import kanger.storage.Index;
import kanger.storage.Storage;
import kanger.units.Domain;
import kanger.units.Right;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User {

    private Mind mind = null;
    private Map<String, Storage> storage = null;
    private Index predicatesLink = null;
    private String storageName = "";

    public User() {
    }

    public void use(String name) throws IOException {
        if (!isClosed()) {
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
        storage.put(TValueFactory.SCHEMA, new Storage());
        storage.put(TVariableFactory.SCHEMA, new Storage());

        for (Map.Entry<String, Storage> e : storage.entrySet()) {
            e.getValue().open(name + "." + e.getKey());
        }

        predicatesLink = new Index();
        predicatesLink.open(name + ".link.index");

        storageName = name;
    }

    public void close() throws IOException {
        if (!isClosed()) {
            for (Map.Entry<String, Storage> e : storage.entrySet()) {
                e.getValue().close();
            }
            predicatesLink.close();
        }
        storage = null;
        predicatesLink = null;
    }

    public void remove() throws IOException {
        if (!isClosed()) {
            for (Map.Entry<String, Storage> e : storage.entrySet()) {
                e.getValue().remove();
            }
            predicatesLink.close();
            predicatesLink.getFile().getAbsoluteFile().delete();
        }
        storage = null;
        predicatesLink = null;
    }

    public void reindex(Reactor reactor) throws IOException, RuntimeErrorException {
        if (!isClosed()) {
            for (Map.Entry<String, Storage> e : storage.entrySet()) {
                reactor.run(e.getValue());
                e.getValue().reindex();
            }
            predicatesLink.clear();
            for (Right r : mind.getRights()) {
                for (List<Domain> tree : r.getTree()) {
                    for (Domain d : tree) {
                        predicatesLink.add(d.getPredicate().getId(), r.getId());
                    }
                }
            }
            predicatesLink.close();
            use(storageName);
        }

    }

    public void clear() throws IOException {
        if (!isClosed()) {
            for (Map.Entry<String, Storage> e : storage.entrySet()) {
                e.getValue().clear();
            }
            predicatesLink.clear();
        }
    }

    public void flush() throws IOException {
        if (!isClosed()) {
            for (Map.Entry<String, Storage> e : storage.entrySet()) {
                e.getValue().flush();
            }
            predicatesLink.flush();
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

    public Index getPredicatesLink() {
        return predicatesLink;
    }
}
