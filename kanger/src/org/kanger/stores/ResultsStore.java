package org.kanger.stores;

import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IUser;
import org.kanger.units.Right;
import org.kanger.units.TValue;

import java.io.IOException;
import java.util.*;

public class ResultsStore {

    private NavigableMap<Long, Long> solves;
    private NavigableMap<Long, SortedSet<TValue>> tagsOrdinal;
    private NavigableMap<Long, SortedSet<TValue>> tagsSystem;
    private IUser user = null;

    public ResultsStore(IUser user) {
        this.user = user;
        this.solves = new TreeMap<>();
        this.tagsOrdinal = new TreeMap<>();
        this.tagsSystem = new TreeMap<>();
    }

    public void addSolve(Right query, Right solve) throws IOException, ClassNotFoundException, OutOfBufferException {
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

    public void addSolve(Right query) throws IOException, ClassNotFoundException, OutOfBufferException {
        addSolve(query, null);
    }

    // ****************** SOLVES

    public Solves getSolves() {
        return new Solves();
    }

    public void commit(ResultsStore results) {
    }

    public void clear() {
        solves.clear();
        tagsOrdinal.clear();
        tagsSystem.clear();
    }

    public Values getValues() {
        return new Values();
    }

    // ****************** VALUES

    public class Solves implements Iterable<Right> {

        @Override
        public Iterator<Right> iterator() {
            return new SolvesIterator();
        }

        public int size() {
            return solves.size();
        }

        public class SolvesIterator implements Iterator<Right> {

            private long currentId;

            public SolvesIterator() {
                currentId = -1L;
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
                try {
                    if (solveId != -1) {
                        return (Right) user.getMind().getRights().get(solveId);
                    } else {
                        return (Right) user.getMind().getRights().get(currentId);
                    }
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    return null;
                }
            }
        }
    }

    public class Values implements Iterable<List<TValue>> {

        @Override
        public Iterator<List<TValue>> iterator() {
            return new ValuesIterator();
        }

        public class ValuesIterator implements Iterator<List<TValue>> {

            private long currentId;
            private Right currentRight;

            public ValuesIterator() {
                currentId = -1L;
                currentRight = null;
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
                        try {
                            currentRight = (Right) user.getMind().getRights().get(nextId);
                            if (currentRight.getSolves() != null && !currentRight.getSolves().isEmpty()) {
                                return true;
                            } else {
                                currentId = nextId;
                            }
                        } catch (Exception e) {
                            e.printStackTrace(System.err);
                            return false;
                        }
                    }
                    return false;
                }
            }

            @Override
            public List<TValue> next() {
                currentId = solves.higherKey(currentId);
                List<TValue> list = new ArrayList<>();
                list.addAll(currentRight.getSolves());
                return list;
            }
        }
    }
}
