package kanger.primitives;

import kanger.interfaces.Identifiable;
import kanger.storage.Cache;
import kanger.storage.Storage;

import java.util.Iterator;

public class DataIterator implements Iterator {

    private Iterator<Identifiable> cache = null;
    private Iterator<Identifiable> storage = null;
    private boolean backward;

    public DataIterator(boolean backward, Cache cache, Storage storage) {
        this.backward = backward;
        this.cache = cache.iterator(backward);
        this.storage = storage == null ? null : storage.iterator(backward);
    }

    @Override
    public boolean hasNext() {
        if(storage != null) {
            if(backward) {
                if(!cache.hasNext()) {
                    return storage.hasNext();
                } else {
                    return true;
                }
            } else {
                if(!storage.hasNext()) {
                    return cache.hasNext();
                } else {
                    return true;
                }
            }
        } else {
            return cache.hasNext();
        }
    }

    @Override
    public Identifiable next() {
        if (storage != null) {
            if (backward) {
                return cache.hasNext() ? cache.next() : storage.next();
            } else {
                return storage.hasNext() ? storage.next() : cache.next();
            }
        } else {
            return cache.next();
        }
    }

    @Override
    public void remove() {
        // TODO: Implement this method
    }


}
