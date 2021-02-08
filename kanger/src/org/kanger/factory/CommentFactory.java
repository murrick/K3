package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.Comment;
import org.kanger.units.Term;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 20.12.2020.
 */
public class CommentFactory implements Iterable<Term> {

    public static final String SCHEMA = "comments";

    public static final long HEADER_ID = -2L;
    public static final long FOOTER_ID = -3L;

    private ICache cache;
    private IStep top = null;
    private Mind mind = null;
    private IBase connection = null;

    public CommentFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(CommentFactory base) throws Exception {
        if (mind.getNext() == null && !mind.getUser().isClosed()) {
            connection = mind.getUser().getStorage(SCHEMA);
        }

        if (base != null) {
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
        }
    }

    public void commit(CommentFactory base) throws Exception {
        if (top == null) {
            top = base.top;
        } else if (base.top != null) {
            base.top.setNext(cache.getRoot());
        }
//        if (cache.getRoot() != null) {
//            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
//                if (((IUnit) s.getData()).getMindId() == base.mind.getId()) {
//                    ((IUnit) s.getData()).setMind(mind);
//                    ((IUnit) s.getData()).setMindId(mind.getId());
//                } else {
//                    break;
//                }
//            }
//        }
        cache.setRoot(base.cache.getRoot());
        for (Object s : cache) {
            if (((IUnit) s).getMindId() == base.mind.getId()) {
                ((IUnit) s).setMind(mind);
                ((IUnit) s).setMindId(mind.getId());
            }
        }
    }

    public void update() throws Exception {
        if (cache.update()) {
        }
    }

    public synchronized Comment add(long ruleId, String comment) throws Exception {
        Comment p = get(ruleId);
        if (p != null) {
            p.setDeleted(false, mind);
            if (!p.getComment().equals(comment)) {
                p.setComment(comment);
                if (connection != null) {
                    IStep s = connection.get(p.getId());
                    if (s != null) {
                        s.setData(p);
                        s.update();
                    } else {
                        System.err.println("!");
                    }
                }

            }
            return p;
        } else {
            p = new Comment(ruleId, comment, mind);
            p.setId(ruleId);
            p.setMindId(mind.getId());
            cache.add(p);
            if (top == null) {
                top = cache.getRoot();
            }
            return p;
        }
    }

    public Comment get(long id) throws Exception {
//        Comment t = get(id);
        Comment t = (Comment) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (Comment) s.getData(mind);
            }
        }
        return t;
    }

//    public Comment get(long id) throws Exception {
//        Comment t = (Comment) cache.get(id);
//        return t;
//    }

//    public Term load(long id) throws RuntimeErrorException {
//        Term t = null;
//        if (!user.isClosed()) {
//            t = (Term) user.getStorage(SCHEMA).get(id);
//            if (t != null) {
//                load.add(t);
//            }
//        }
//        return t;
//    }

//    public Term getRoot() {
//        return root;
//    }

//    public void setRoot(Term o) {
//        root = o;
//    }

//    private void mark() {
//        stack.push(new Object[]{root, lastId, varIndex});
//    }
//
//    private void release() {
//        if (!stack.empty()) {
//            Object[] pop = stack.pop();
//            Term saved = (Term) pop[0];
//            lastId = (long) pop[1];
//            varIndex = (int) pop[2];
//            root = saved;
//        }
//        if (stack.empty()) {
//            mark();
//        }
//    }

    public int size() throws Exception {
        return cache.size();
    }


    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction(mind.getNext().getComments());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    @Override
    public Iterator iterator() {
        return cache.iterator(-1);
    }

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted(mind)) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
        }
    }

    public void mark() throws Exception {
        cache.mark();
    }


    public void commit() throws Exception {
        cache.commit();
    }

    public void release() throws Exception {
        cache.release();
    }

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

//    public void delete(long id) throws Exception {
//        Comment c = get(id);
//        if (c != null) {
//            c.setDeleted(true, mind);
//        }
//    }
}
