package kanger.storage;

import kanger.interfaces.Identifiable;
import kanger.units.Domain;
import kanger.units.Predicate;
import kanger.units.Right;
import kanger.units.TValue;

import java.util.*;

public class RightsCache extends Cache {

    private NavigableMap<Long, Identifiable> stored;
    private NavigableMap<Long, Set<Predicate>> predicates;
    private NavigableMap<Long, Long> solves;
    private NavigableMap<Long, SortedSet<TValue>> tagsOrdinal;
    private NavigableMap<Long, SortedSet<TValue>> tagsSystem;

    public RightsCache() {
        super();
        stored = new TreeMap<>();
        predicates = new TreeMap<>();
        solves = new TreeMap<>();
        tagsOrdinal = new TreeMap<>();
        tagsSystem = new TreeMap<>();
    }

    @Override
    public void add(Identifiable one) {
        super.add(one);
        Set<Predicate> list = new HashSet<>();
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

    public void addSolve(Right query, Right solve) {
        solves.put(query.getId(), solve == null ? -1 : solve.getId());
        long tag = -1;

        List<TValue> list = query.getDomain().getArguments().getTValues(true);
        NavigableMap<Long, SortedSet<TValue>> tags = query.getDomain().isCalculated() ? tagsSystem : tagsOrdinal;
        for (TValue v : list) {
            if (!tags.containsKey(v.getTag())) {
                if (tag == -1) {
                    tag = v.getTag();
                }
            }
        }
        for (TValue v : list) {
            if (tag == -1) {
                tag = v.getTag();
            }
            if (!tags.containsKey(tag)) {
                tags.put(tag, new TreeSet<>());
            }
            tags.get(tag).add(v);
        }
    }

    public void addSolve(Right query) {
        addSolve(query, null);
    }

    // ****************** DATABASE

    public Database getDatabase(long fromId) {
        return new Database(fromId);
    }

    public class Database implements Iterable<Right> {

        private long fromId;

        public Database(long fromId) {
            this.fromId = fromId;
        }

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

            public DatabaseIterator() {
                currentId = fromId;
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
                if (backward) {
                    return getPrevious(currentId, stored) != -1;
                } else {
                    return getNext(currentId, stored) != -1;
                }
            }

            @Override
            public Right next() {
                if (backward) {
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

            public LinksIterator() {
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
                } while (id != -1);
                return false;
            }

            @Override
            public Right next() {
                if (backward) {
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

    // ****************** SOLVES

    public Solves getSolves() {
        return new Solves();
    }

    public class Solves implements Iterable<Right> {

        @Override
        public Iterator<Right> iterator() {
            return new SolvesIterator();
        }

        public int size() {
            return solves.size();
        }

        public class SolvesIterator implements Iterator<Right> {

            private long currentTag;
            private long currentId;

            public SolvesIterator() {
                currentId = -1L;
                currentTag = -1;
            }


            @Override
            public void remove() {

            }

            @Override
            public boolean hasNext() {
                if (solves.isEmpty()) {
                    return false;
                } else {
                    Long nextId;
                    while ((nextId = solves.higherKey(currentId)) != null) {

                        if (solves.get(nextId) != -1L) {
                            return true;
                        } else {
                            currentId = nextId;
                        }
                    }
                    return false;
                }
            }

            @Override
            public Right next() {
                currentId = solves.higherKey(currentId);
                long solveId = solves.get(currentId);
                if (solveId != -1) {
                    return (Right) get(solveId);
                } else {
                    return (Right) get(currentId);
                }
            }
        }
    }

    // ****************** VALUES

    public Values getValues() {
        return new Values();
    }

    public class Values implements Iterable<List<TValue>> {

        NavigableMap<Long, SortedSet<TValue>> tags;
        @Override
        public Iterator<List<TValue>> iterator() {
            return new ValuesIterator();
        }

        public int size() {
            return tags.size();
        }

        public class ValuesIterator implements Iterator<List<TValue>> {

            private long currentId;

            public ValuesIterator() {
                currentId = -1L;
                tags = tagsOrdinal.isEmpty() ? tagsSystem : tagsOrdinal;
            }


            @Override
            public void remove() {

            }

            @Override
            public boolean hasNext() {
                if (tags.isEmpty()) {
                    return false;
                } else {
                    return tags.higherKey(currentId) != null;
                }
            }

            @Override
            public List<TValue> next() {
                currentId = tags.higherKey(currentId);
                List<TValue> list = new ArrayList<>();
                list.addAll(tags.get(currentId));
                return list;
            }
        }
    }
}
