package org.kanger.stores;

import org.kanger.Mind;
import org.kanger.primitives.ArgList;
import org.kanger.primitives.Hypothesis;
import org.kanger.units.Predicate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 28.05.15.
 */
public class HypothesisStore implements Comparable<HypothesisStore>, Iterable<Hypothesis> {

    private final Mind mind;
    private List<Hypothesis> root = null;
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
            for (Hypothesis h : base.getRoot()) {
                add(h);
            }
        }
    }

    public Hypothesis add(boolean antc, boolean isQuery, Predicate pred, ArgList arg) throws Exception {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
        }
        Hypothesis h = find(antc, pred, arg);
        if (h != null) {
            if (isQuery) {
                h.setQuery(isQuery);
            }
            return h;
        } else {
//            boolean ca = user.getMind().getQueryPass() == QueryPass.CHECKFALSE ? antc : !antc;
            h = new Hypothesis();
            h.setAntc(antc);
            h.setPredicate(pred);
            h.getArguments().addAll(arg.convertBase(mind));
            h.setQuery(isQuery);

//            if (mind.getRights().find(antc, pred, arg) == null) {
            root.add(h);
            return h;
//            } else {
//                return null;
//            }
        }

    }


    public Hypothesis add(Hypothesis hypothesis) throws Exception {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
        }
        Hypothesis h = find(hypothesis);
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

    public Hypothesis get(int index) {
        return root.get(index);
    }


    public Hypothesis find(Boolean antc, Predicate pred, ArgList arg) throws Exception {
        if (root == null) {
            return null;
        }
        for (Hypothesis h : root) {
//            boolean ca = user.getMind().getQueryPass() == QueryPass.CHECKFALSE ? h.isAntc() : !h.isAntc();
            if (h.getPredicate().getId() == pred.getId()
                    && (antc == null || h.isAntc() == antc)
                    && h.getArguments().equalsBase(mind, arg)) {
                return h;
            }
        }
        return null;
    }

    public Hypothesis find(Hypothesis hy) throws Exception {
        if (root == null) {
            return null;
        }
        for (Hypothesis h : root) {
            if (h.getPredicate().getId() == hy.getPredicate().getId()
                    && h.isAntc() == hy.isAntc()
                    && hy.getArguments().equalsBase(mind, h.getArguments())) {
                return h;
            }
        }
        return null;
    }


    public boolean contains(Hypothesis h) throws Exception {
        return find(h) != null;
    }

    public List<Hypothesis> getRoot() {
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


    @Override
    public int compareTo(HypothesisStore o) {
        return Integer.valueOf(size()).compareTo(Integer.valueOf(o.size()));
    }

    @Override
    public Iterator<Hypothesis> iterator() {
        if (root != null) {
            return root.iterator();
        } else {
            return new ArrayList<Hypothesis>().iterator();
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
