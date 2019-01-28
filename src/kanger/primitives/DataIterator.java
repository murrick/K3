package kanger.primitives;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.storage.Cache;
import kanger.storage.Storage;

import java.util.Iterator;

public class DataIterator implements Iterator {

    private Iterator<Identifiable> cache = null;
    private Iterator<Identifiable> storage = null;
    private boolean backward;
    private User user = null;

    public DataIterator(boolean backward, Cache cache, Storage storage, User user) {
        this.user = user;
        this.backward = backward;
        this.cache = cache.iterator(backward);
        this.storage = storage == null ? null : storage.iterator(backward);
    }

    @Override
    public boolean hasNext() {
        if (storage != null) {
            if (backward) {
                if (!cache.hasNext()) {
                    return storage.hasNext();
                } else {
                    return true;
                }
            } else {
                if (!storage.hasNext()) {
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
                if (cache.hasNext()) {
                    return cache.next();
                } else {
                    try {
                        Identifiable next = storage.next();
                        next.linkExternal(user);
                        return next;
                    } catch (RuntimeErrorException e) {
                        e.printStackTrace(System.err);
                        return null;
                    }
                }
            } else {
                if (storage.hasNext()) {
                    try {
                        Identifiable next = storage.next();
                        next.linkExternal(user);
                        return next;
                    } catch (RuntimeErrorException e) {
                        e.printStackTrace(System.err);
                        return null;
                    }
                } else {
                    return cache.next();
                }
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
