package kanger.factory;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.primitives.DataIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.Domain;
import kanger.units.Predicate;
import kanger.units.Right;

import java.io.IOException;
import java.util.*;

/**
 * Created by murray on 25.05.15.
 */
public class RightFactory implements Iterable<Right> {

    public static final String SCHEMA = "rights";

    private long lastId = 0;
    private long firstId = 0;

    private Cache cache = new Cache();
    private Cache load = new Cache();
    private User user = null;

    public RightFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(RightFactory base) {
        cache.clear();
        load.clear();
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            cache.add(base.cache);
        } else {
            lastId = 0;
            firstId = 0;
        }
    }

    public void commit(RightFactory base) {
        List<Right> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (p.getId() < base.firstId) {
                break;
            }
            list.add(0, (Right) p);
        }
        for (Right p : list) {
            p.setId(lastId++);
            cache.add(p);
        }
    }

    public void update() {
        if (!user.isClosed()) {
            try {
                for (Identifiable p : cache) {
                    if (p.getId() < firstId) {
                        break;
                    }
                    user.getStorage(SCHEMA).add(p);
                }
                cache.clear();
                firstId = lastId;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public Right add() {
        Right p = new Right(user);
        p.setId(lastId++);
        cache.add(p);
        return p;
    }

    public Right get(long id) {
        Right t = (Right) cache.get(id);
        if (t == null) {
            t = (Right) load.get(id);
            if (t == null) {
                try {
                    t = (Right) user.getStorage(SCHEMA).get(id);
                    if (t != null) {
                        load.add(t);
                        t.linkExternal(user);
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        return t;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getRights());
        } else {
            transaction(null);
        }
    }

    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }

    public void add(Domain d) {
        Right r = add();
        List<Domain> t = new ArrayList<>();
        r.getTree().add(t);
        ArgList arg = new ArgList();
        for (Argument a : d.getArguments()) {
            arg.add(new Argument(a.getValue()));
        }
        t.add(user.getMind().getDomains().add(d.getPredicate(), d.isAntc(), arg, r));
    }

    @Override
    public Iterator<Right> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new RightIterator(true, cache, storage);
    }

    public class RightIterator extends DataIterator {

        public RightIterator(boolean backward, Cache cache, Storage storage) {
            super(backward, cache, storage);
        }

        @Override
        public Identifiable next() {
            Identifiable next = super.next();
            next.linkExternal(user);
            return next;
        }
    }

    public LinkedRights getLinkedRights(Predicate predicate) {
        return new LinkedRights(predicate);
    }

    public class LinkedRights implements Iterable<Right> {
        private Predicate predicate;
        private Right current;
        private Iterator<Right> iterator;

        public LinkedRights(Predicate p) {
            predicate = p;
            iterator = new RightIterator(true, cache, user.isClosed() ? null : user.getStorage(SCHEMA));
            current = null;
            boolean found = false;
            while (iterator.hasNext()) {
                current = iterator.next();
                if (current.contains(predicate)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                current = null;
            }
        }

        @Override
        public Iterator<Right> iterator() {
            return new Iterator<Right>() {

                @Override
                public void remove() {
                    // TODO: Implement this method
                }


                @Override
                public boolean hasNext() {
                    return current != null;
                }

                @Override
                public Right next() {
                    Right right = current;
                    boolean found = false;
                    while (iterator.hasNext()) {
                        current = iterator.next();
                        if (current.contains(predicate)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        current = null;
                    }
                    return right;
                }
            };
        }
    }


//    public LinkedTrees getLinkedTrees(Predicate predicate) {
//        return new LinkedTrees(predicate);
//    }
//
//    public class LinkedTrees implements Iterable<Tree> {
//        private Predicate predicate;
//        private Right current;
//        private int index = -1;
//        private Iterator<Right> iterator;
//
//        public LinkedTrees(Predicate p) {
//            predicate = p;
//            iterator = new RightIterator(true, cache, user.isClosed() ? null : user.getStorage(SCHEMA));
//            current = null;
//            boolean found = false;
//            while (iterator.hasNext()) {
//                current = iterator.next();
//                for (index = 0; index < current.getTree().size(); ++index) {
//                    if (current.getTree().get(index).contains(predicate)) {
//                        found = true;
//                        break;
//                    }
//                }
//                if (found) {
//                    break;
//                }
//            }
//            if (!found) {
//                current = null;
//                index = -1;
//            }
//        }
//
//        @Override
//        public Iterator<Tree> iterator() {
//            return new Iterator<Tree>() {
//
//                @Override
//                public void remove() {
//                    // TODO: Implement this method
//                }
//
//
//                @Override
//                public boolean hasNext() {
//                    return current != null && index != -1;
//                }
//
//                @Override
//                public Tree next() {
//                    Right right = current;
//                    int ix = index;
//                    boolean found = false;
//
//                    ++index;
//                    for (; index < current.getTree().size(); ++index) {
//                        if (current.getTree().get(index).contains(predicate)) {
//                            found = true;
//                            break;
//                        }
//                    }
//
//                    if (!found) {
//                        while (iterator.hasNext()) {
//                            current = iterator.next();
//                            for (index = 0; index < current.getTree().size(); ++index) {
//                                if (current.getTree().get(index).contains(predicate)) {
//                                    found = true;
//                                    break;
//                                }
//                            }
//                            if (found) {
//                                break;
//                            }
//                        }
//                    }
//                    if (!found) {
//                        current = null;
//                        index = -1;
//                    }
//
//                    return right.getTree().get(ix);
//                }
//            };
//        }
//    }

}
