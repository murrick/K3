package kanger.primitives;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.storage.Storage;

import java.util.Iterator;

public class UnitIterator implements Iterator {


    @Override
    public void remove() {
        // TODO: Implement this method
    }


    Iterator<Identifiable> cache = null;
    Storage storage = null;

    public UnitIterator(Iterator cache, Storage storage) {
        this.cache = cache;
        this.storage = storage;
    }

    @Override
    public boolean hasNext() {
        if(!cache.hasNext()) {
            if(storage == null) {
                return false;
            } else {
                cache = storage.iterator();
                return cache.hasNext();
            }
        } else {
            return true;
        }
    }

    @Override
    public Identifiable next() {
        return cache.next();
    }
}
