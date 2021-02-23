/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger.primitives;

import org.kanger.units.TSolve;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class TVariableSet implements Comparable<TVariableSet> {

    private SortedSet<TVariable> set = new TreeSet<>();

    public TVariableSet(List<TValue> list) throws Exception {
        for (TValue v : list) {
            set.add(v.getTVar());
        }
    }

    public TVariableSet(TSolve solve) throws Exception {
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
