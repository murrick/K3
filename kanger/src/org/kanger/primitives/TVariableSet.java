package org.kanger.primitives;

import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.units.TSolve;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.io.IOException;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class TVariableSet implements Comparable<TVariableSet> {

    private SortedSet<TVariable> set = new TreeSet<>();

    public TVariableSet(List<TValue> list) throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException {
        for (TValue v : list) {
            set.add(v.getTVar());
        }
    }

    public TVariableSet(TSolve solve) throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException {
        for (TValue v : solve.getSolve()) {
            set.add(v.getTVar());
        }
    }

    @Override
    public int hashCode() {
        int hashCode = 3;
        for (TVariable t : set) {
            long id = t.getId();
            hashCode = 47 * hashCode + (int) (id ^ (id >>> 32));
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object t) {
        return !(t == null || !(t instanceof TVariableSet)) && ((TVariableSet) t).set.equals(set);
    }

    @Override
    public int compareTo(TVariableSet zet) {
        if (set.size() > zet.set.size()) {
            return zet.set.size() - set.size();
        } else if (!equals(zet) && !set.isEmpty()) {
            return (int) (set.first().getId() - zet.set.first().getId());
        } else {
            return 0;
        }
    }

    public boolean contains(TVariable t) {
        return set.contains(t);
    }
}
