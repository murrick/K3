package kanger.primitives;

import kanger.interfaces.Identifiable;
import kanger.storage.Cache;
import kanger.storage.Storage;

import java.util.Iterator;

public class DataIterator implements Iterator {

    Iterator<Identifiable> cache = null;
    Iterator<Identifiable> storage = null;

    public DataIterator(boolean backward, Cache cache, Storage storage) {
        this.cache = cache.iterator(backward);
        this.storage = storage == null ? null : storage.iterator(backward);
    }

    @Override
    public boolean hasNext() {
        if (!cache.hasNext()) {
            if (storage == null) {
                return false;
            } else {
                return storage.hasNext();
            }
        } else {
            return true;
        }
    }

    @Override
    public Identifiable next() {
        return cache.hasNext() ? cache.next() : storage.next();
    }

    @Override
    public void remove() {
        // TODO: Implement this method
    }


}
