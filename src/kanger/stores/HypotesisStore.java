package kanger.stores;

import kanger.User;
import kanger.primitives.ArgList;
import kanger.primitives.Hypotese;
import kanger.units.Predicate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 28.05.15.
 */
public class HypotesisStore implements Comparable<HypotesisStore> {

    private List<Hypotese> root = null;
    private boolean enableStore = true;
    private User user = null;

    public HypotesisStore(User user) {
        this.user = user;
    }

    public void commit(HypotesisStore base) throws IOException, ClassNotFoundException {
        if (!enableStore) {
            return;
        }
        if (!base.isEmpty()) {
            if (root == null) {
                root = new ArrayList<>();
            }
            for (Hypotese h : base.getRoot()) {
                add(h);
            }
        }
    }

    public Hypotese add(boolean antc, boolean isQuery, Predicate pred, ArgList arg) throws Exception {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
        }
        Hypotese h = find(antc, pred, arg);
        if (h != null) {
            if (isQuery) {
                h.setQuery(isQuery);
            }
            return h;
        } else {
//            boolean ca = user.getMind().getQueryPass() == QueryPass.CHECKFALSE ? antc : !antc;
            h = new Hypotese(user);
            h.setAntc(antc);
            h.setPredicate(pred);
            h.addParams(arg);
            h.setQuery(isQuery);
            root.add(h);
            return h;
        }

    }


    public Hypotese add(Hypotese hypotese) throws IOException, ClassNotFoundException {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
        }
        Hypotese h = find(hypotese);
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

    public Hypotese get(int index) {
        return root.toArray(new Hypotese[]{})[index];
    }


    public Hypotese find(Boolean antc, Predicate pred, ArgList arg) throws Exception {
        if (root == null) {
            return null;
        }
        for (Hypotese h : root) {
//            boolean ca = user.getMind().getQueryPass() == QueryPass.CHECKFALSE ? h.isAntc() : !h.isAntc();
            if (h.getPredicate().getId() == pred.getId() && (antc == null || h.isAntc() == antc)) {

                int i = 0;
                if (arg.size() == h.getSolve().size()) {
                    for (; i < h.getSolve().size(); ++i) {
                        if (h.getSolve().get(i) != null && arg.get(i) != null && !h.getSolve().get(i).equals(arg.get(i).getValue())) {
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

    public Hypotese find(Hypotese hy) throws IOException, ClassNotFoundException {
        if (root == null) {
            return null;
        }
        for (Hypotese h : root) {
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


    public boolean contains(Hypotese h) throws IOException, ClassNotFoundException {
        return find(h) != null;
    }

    public List<Hypotese> getRoot() {
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
    public int compareTo(HypotesisStore o) {
        return Integer.valueOf(size()).compareTo(Integer.valueOf(o.size()));
    }

    public void exclude(HypotesisStore exclude) throws IOException, ClassNotFoundException {
        if (!isEmpty() && !exclude.isEmpty()) {
            Set<Hypotese> toDelete = new HashSet<>();
            for (Hypotese h : root) {
                if (exclude.find(h) != null) {
                    toDelete.add(h);
                }
            }
            //Если не остается гипотез кроме базовых - оставляем базовые
            if (toDelete.size() < root.size()) {
                for (Hypotese h : toDelete) {
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
