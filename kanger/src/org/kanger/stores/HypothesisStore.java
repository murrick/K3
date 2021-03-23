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

package org.kanger.stores;

import org.kanger.Mind;
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.IList;
import org.kanger.primitives.ArgumentsList;
import org.kanger.primitives.Hypothesis;
import org.kanger.units.Predicate;
import org.kanger.units.Rule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Quznetsov on 28.05.15.
 */
public class HypothesisStore implements IFactory<IHypothesis> {

    private List<IHypothesis> root = null;

    private final Mind mind;
    private boolean optimized = false;      // Признак того что текущий список оптимизирован

    public HypothesisStore(Mind mind) {
        this.mind = mind;
    }

    public void commit(HypothesisStore base) throws Exception {
        if (!base.isEmpty()) {
            if (root == null) {
                root = new ArrayList<>();
            }
            if (!base.isEmpty()) {
                for (IHypothesis h : base) {
                    add(h);
                }
            }
        }
    }

    public IHypothesis add(boolean antc, boolean isQuery, Predicate pred, ArgumentsList arg) throws Exception {
        if (root == null) {
            root = new ArrayList<>();
        }
        IHypothesis h = find(antc, pred, arg);
        if (h != null) {
            if (isQuery) {
                ((Hypothesis) h).setQuery(isQuery);
            }
            return h;
        } else {
            h = new Hypothesis();
            ((Hypothesis) h).setAntc(antc);
            ((Hypothesis) h).setPredicate(pred);
            h.getArguments().addAll(arg.convertBase(mind));
            ((Hypothesis) h).setQuery(isQuery);
            root.add(h);
            return h;
        }

    }

    public IHypothesis add(IHypothesis hypothesis) throws Exception {
        if (root == null) {
            root = new ArrayList<>();
        }
        IHypothesis h = find(hypothesis);
        if (h == null) {
            root.add(hypothesis);
            return hypothesis;
        } else {
            return h;
        }
    }

    @Override
    public IHypothesis get(long index) {
        return root.get((int) index);
    }

    public IHypothesis find(Boolean antc, Predicate pred, ArgumentsList arg) throws Exception {
        if (root == null) {
            return null;
        }
        for (IHypothesis h : root) {
            if (h.getPredicate().getId() == pred.getId()
                    && (antc == null || h.isAntc() == antc)
                    && ((ArgumentsList) h.getArguments()).equalsBase(mind, arg)) {
                return h;
            }
        }
        return null;
    }

    public IHypothesis find(IHypothesis hy) throws Exception {
        if (root == null) {
            return null;
        }
        for (IHypothesis h : root) {
            if (h.getPredicate().getId() == hy.getPredicate().getId()
                    && h.isAntc() == hy.isAntc()
                    && equalsBase(hy.getArguments(), h.getArguments())) {
                return h;
            }
        }
        return null;
    }

    private boolean equalsBase(IList a, IList b) throws Exception {
        if (a.size() == b.size()) {
            for (int i = 0; i < a.size(); ++i) {
                if (a.get(i).getValue(mind).getId() == b.get(i).getValue(mind).getId()
                        || (a.get(i).getValue(mind).isCVariable() && b.get(i).getValue(mind).isCVariable())) {
                } else {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public boolean contains(Hypothesis h) throws Exception {
        return find(h) != null;
    }

    public void clear() {
        optimized = false;
        root = null;
    }

    public int size() {
        return root == null ? 0 : root.size();
    }

    public boolean isEmpty() {
        return root == null || root.isEmpty();
    }

    @Override
    public Iterator<IHypothesis> iterator() {
        if (root != null) {
            return root.iterator();
        } else {
            return new ArrayList<IHypothesis>().iterator();
        }
    }

    public void optimize() throws Exception {
        if (root != null && !root.isEmpty() && !optimized) {
            List<IHypothesis> list = new ArrayList<>();
            List<IHypothesis> success = new ArrayList<>();
            list.addAll(root);
            for (IHypothesis h : list) {
                Mind m = new Mind(mind);
                Rule r = (Rule) m.compileLine(((Hypothesis) h).toString(m), false, null);
                m.link(r, false);
                Boolean ar = m.analyze(null, false);
                mind.release(m);
                if (!ar) {
                    success.add(h);
                }
            }
            root.clear();
            root.addAll(success);
            optimized = true;
        }
    }

    public void removeAll(Collection<IHypothesis> toDelete) {
        if (root != null) {
            root.removeAll(toDelete);
        }
    }
}
