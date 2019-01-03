package kanger.storage;

import kanger.interfaces.Identifiable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Storage {

    private Map<Integer, Set<Long>> hashCache = new HashMap<>();
    private Map<Long, StorageOne> idCache = new HashMap<>();

    public Identifiable get(long id) {
        if (idCache.containsKey(id)) {
            return idCache.get(id).getObject();
        } else {
            return getFromStorage(id);
        }
    }

    public void release(long id) {
        if (idCache.containsKey(id)) {
            if (idCache.get(id).decCounter() <= 0) {
                idCache.remove(id);
            }
        }
    }

    private Identifiable getFromStorage(long id) {
        return null;
    }

    public class StorageOne {
        private Identifiable object;
        private long timeCreated;
        private long counter;

        public StorageOne(Identifiable object) {
            this.object = object;
            this.counter = 0;
            this.timeCreated = System.currentTimeMillis();
        }

        public long incCounter() {
            return ++counter;
        }

        public long decCounter() {
            return --counter;
        }

        public Identifiable getObject() {
            return object;
        }

        public void setObject(Identifiable object) {
            this.object = object;
        }

        public long getTimeCreated() {
            return timeCreated;
        }

        public void setTimeCreated(long timeCreated) {
            this.timeCreated = timeCreated;
        }

        public long getCounter() {
            return counter;
        }

        public void setCounter(long counter) {
            this.counter = counter;
        }
    }

}
