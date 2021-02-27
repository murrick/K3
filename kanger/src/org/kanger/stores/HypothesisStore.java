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
import org.kanger.primitives.ArgumentsList;
import org.kanger.primitives.Hypothesis;
import org.kanger.units.Predicate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Quznetsov on 28.05.15.
 */
public class HypothesisStore implements IFactory<IHypothesis> {

    private final transient Mind mind;
    private List<IHypothesis> root = null;
    private boolean enableStore = true;

    public HypothesisStore(Mind mind) {
        this.mind = mind;
    }

    public void commit(HypothesisStore base) throws Exception {
        if (!enableStore) {
            return;
        }
        if (!base.isEmpty()) {
            if (root == null) {
                root = new ArrayList<>();
            }
            for (IHypothesis h : base.getRoot()) {
                add(h);
            }
        }
    }

    public IHypothesis add(boolean antc, boolean isQuery, Predicate pred, ArgumentsList arg) throws Exception {
        if (!enableStore) {
            return null;
        }
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
//            boolean ca = user.getMind().getQueryPass() == QueryPass.CHECKFALSE ? antc : !antc;
            h = new Hypothesis();
            ((Hypothesis) h).setAntc(antc);
            ((Hypothesis) h).setPredicate(pred);
            h.getArguments().addAll(arg.convertBase(mind));
            ((Hypothesis) h).setQuery(isQuery);

//            if (mind.getRights().find(antc, pred, arg) == null) {
            root.add(h);
            return h;
//            } else {
//                return null;
//            }
        }

    }


    public IHypothesis add(IHypothesis hypothesis) throws Exception {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
        }
        IHypothesis h = find(hypothesis);
        if (h == null /*|| h.isAntc() != hypothesis.isAntc()*/) {
//            h = hypothesis;
//            h.setAntc(true);
            root.add(hypothesis);
            return hypothesis;
        } else {
            return h;
        }
    }

//    public void addAll(Collection<Hypotese> list) {
//        if (list != null && !list.isEmpty()) {
//            if (root == null) {
//                root = new ArrayList<>();
//            }
//            root.addAll(list);
//        }
//    }

    public void enable(boolean enable) {
        enableStore = enable;
    }

    public boolean isEnabled() {
        return enableStore;
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
//            boolean ca = user.getMind().getQueryPass() == QueryPass.CHECKFALSE ? h.isAntc() : !h.isAntc();
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
                    && ((ArgumentsList) hy.getArguments()).equalsBase(mind, h.getArguments())) {
                return h;
            }
        }
        return null;
    }


    public boolean contains(Hypothesis h) throws Exception {
        return find(h) != null;
    }

    public List<IHypothesis> getRoot() {
        return root;
    }

    public void clear() {
        if (enableStore) {
            root = null;
        }
    }

    public int size() {
        return root == null ? 0 : root.size();
    }

    public boolean isEmpty() {
        return root == null || root.isEmpty();
    }


//    public int compareTo(HypothesisStore o) {
//        return Integer.valueOf(size()).compareTo(Integer.valueOf(o.size()));
//    }

    @Override
    public Iterator<IHypothesis> iterator() {
        if (root != null) {
            return root.iterator();
        } else {
            return new ArrayList<IHypothesis>().iterator();
        }
    }

//    public void exclude(HypothesisStore exclude) throws Exception {
//        if (!isEmpty() && !exclude.isEmpty()) {
//            Set<Hypothesis> toDelete = new HashSet<>();
//            for (Hypothesis h : root) {
//                if (exclude.find(h) != null) {
//                    toDelete.add(h);
//                }
//            }
//            //Если не остается гипотез кроме базовых - оставляем базовые
//            if (toDelete.size() < root.size()) {
//                for (Hypothesis h : toDelete) {
//                    if (!h.isQuery()) {
//                        root.remove(h);
//                    }
//                }
//            }
////        } else if (isEmpty() && !exclude.isEmpty()) {
////            for (Hypotese h : exclude.getRoot()) {
////                add(h);
////            }
//        }
//    }
}
