package kanger.storage;

import kanger.interfaces.Identifiable;
import kanger.units.Domain;
import kanger.units.Predicate;
import kanger.units.Right;

import java.util.*;

public class RightsCache extends Cache {

    private NavigableMap<Long, Identifiable> stored;
    private NavigableMap<Long, Set<Identifiable>> predicates;

    public RightsCache() {
        super();
        stored = new TreeMap<>();
        predicates = new TreeMap<>();
    }

    @Override
    public void add(Identifiable one) {
        super.add(one);
        Set<Identifiable> list = new HashSet<>();
        for (List<Domain> row : ((Right) one).getTree()) {
            for (Domain d : row) {
                if (!list.contains(d.getPredicate())) {
                    list.add(d.getPredicate());
                }
            }
        }
        predicates.put(one.getId(), list);
        if (((Right) one).isStored()) {
            stored.put(one.getId(), one);
        }
    }

    public void setStored(Right r) {
        r.setStored();
        stored.put(r.getId(), r);
    }

    public void add(RightsCache base) {
        super.add(base);
        stored.putAll(base.stored);
        predicates.putAll(base.predicates);
    }

    @Override
    public void remove(long id) {
        super.remove(id);
        stored.remove(id);
        predicates.remove(id);
    }

    @Override
    public void clear() {
        super.clear();
        stored.clear();
        predicates.clear();
    }

    @Override
    public long release() {
        long id = super.release();
        if (id == -1L) {
            stored.clear();
            predicates.clear();
        } else {
            List<Long> toDelete = new ArrayList<>();
            for (long idx : predicates.tailMap(id).keySet()) {
                if (idx > id) {
                    toDelete.add(idx);
                }
            }
            for (long idx : toDelete) {
                stored.remove(idx);
                predicates.remove(idx);
            }
        }
        return id;
    }

    // ****************** DATABASE

    public Database getDatabase() {
        return new Database();
    }

    public class Database implements Iterable<Right> {

        @Override
        public Iterator<Right> iterator() {
            return new DatabaseIterator(true);
        }

        public int size() {
            return stored.size();
        }

        public class DatabaseIterator implements Iterator<Right> {

            private long currentId;
            private boolean backward = false;

            public DatabaseIterator()  {
                currentId = -1;
            }

            public DatabaseIterator(boolean backward) {
                this();
                this.backward = backward;
            }

            @Override
            public void remove() {

            }

            @Override
            public boolean hasNext() {
                if(backward) {
                    return getPrevious(currentId, stored) != -1;
                } else {
                    return getNext(currentId, stored) != -1;
                }
            }

            @Override
            public Right next() {
                if(backward) {
                    currentId = getPrevious(currentId, stored);
                } else {
                    currentId = getNext(currentId, stored);
                }
                if (currentId != -1) {
                    return (Right) stored.get(currentId);
                } else {
                    return null;
                }
            }
        }

    }

    // ****************** LINKS

    public Links getLinks(Predicate predicate) {
        return new Links(predicate);
    }

    public class Links implements Iterable<Right> {

        private Predicate predicate;

        public Links(Predicate predicate) {
            this.predicate = predicate;
        }

        @Override
        public Iterator<Right> iterator() {
            return new LinksIterator(true);
        }

        public int size() {
            return stored.size();
        }

        public class LinksIterator implements Iterator<Right> {

            private long currentId;
            private boolean backward = false;

            public LinksIterator()  {
                currentId = -1;
            }

            public LinksIterator(boolean backward) {
                this();
                this.backward = backward;
            }

            @Override
            public void remove() {

            }

            @Override
            public boolean hasNext() {
                long id = -1;
                do {
                    if (backward) {
                        id = getPrevious(currentId, index);
                    } else {
                        id = getNext(currentId, index);
                    }
                    if (id != -1) {
                        if (predicates.get(id).contains(predicate)) {
                            return true;
                        } else {
                            currentId = id;
                        }
                    }
                } while(id != -1);
                return false;
            }

            @Override
            public Right next() {
                if(backward) {
                    currentId = getPrevious(currentId, index);
                } else {
                    currentId = getNext(currentId, index);
                }
                if (currentId != -1) {
                    return (Right) index.get(currentId);
                } else {
                    return null;
                }
            }
        }

    }
}
