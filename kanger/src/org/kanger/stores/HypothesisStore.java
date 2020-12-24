package org.kanger.stores;

import org.kanger.Mind;
import org.kanger.primitives.ArgList;
import org.kanger.primitives.Hypothesis;
import org.kanger.units.Predicate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 28.05.15.
 */
public class HypothesisStore implements Comparable<HypothesisStore> {

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
            h.addParams(mind, arg);
            h.setQuery(isQuery);
            root.add(h);
            return h;
        }

    }


    public Hypothesis add(Hypothesis hypotese) throws Exception {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
        }
        Hypothesis h = find(hypotese);
        if (h == null /*|| h.isAntc() != hypotese.isAntc()*/) {
            h = hypotese;
            root.add(h);
            return h;
        }
        return h;

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
        return root.toArray(new Hypothesis[]{})[index];
    }


    public Hypothesis find(Boolean antc, Predicate pred, ArgList arg) throws Exception {
        if (root == null) {
            return null;
        }
        for (Hypothesis h : root) {
//            boolean ca = user.getMind().getQueryPass() == QueryPass.CHECKFALSE ? h.isAntc() : !h.isAntc();
            if (h.getPredicate().getId() == pred.getId() && (antc == null || h.isAntc() == antc)) {

                int i = 0;
                if (arg.size() == h.getSolve().size()) {
                    for (; i < h.getSolve().size(); ++i) {
                        if (h.getSolve().get(i) != null && arg.get(i) != null && !h.getSolve().get(i).equals(arg.get(i).getValue(mind))) {
                            break;
                        }
                    }
                }
                if (i == h.getSolve().size()) {
                    return h;
                }
            }
        }
        return null;
    }

    public Hypothesis find(Hypothesis hy) throws Exception {
        if (root == null) {
            return null;
        }
        for (Hypothesis h : root) {
            if (h.getPredicate().getId() == hy.getPredicate().getId() && h.isAntc() == hy.isAntc() && hy.getSolve().size() == h.getSolve().size()) {
                int i = 0;
                for (; i < h.getSolve().size(); ++i) {
                    if (!h.getSolve().get(i).isEmpty()
                            && !hy.getSolve().get(i).isEmpty()
                            && !h.getSolve().get(i).equalsTo(hy.getSolve().get(i))) {
                        break;
                    }
                }
                if (i == h.getSolve().size()) {
                    return h;
                }
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

    public void exclude(HypothesisStore exclude) throws Exception {
        if (!isEmpty() && !exclude.isEmpty()) {
            Set<Hypothesis> toDelete = new HashSet<>();
            for (Hypothesis h : root) {
                if (exclude.find(h) != null) {
                    toDelete.add(h);
                }
            }
            //Если не остается гипотез кроме базовых - оставляем базовые
            if (toDelete.size() < root.size()) {
                for (Hypothesis h : toDelete) {
                    if (!h.isQuery()) {
                        root.remove(h);
                    }
                }
            }
//        } else if (isEmpty() && !exclude.isEmpty()) {
//            for (Hypotese h : exclude.getRoot()) {
//                add(h);
//            }
        }
    }
}
