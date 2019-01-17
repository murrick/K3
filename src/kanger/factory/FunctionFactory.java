package kanger.factory;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.primitives.DataIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.Function;
import kanger.units.Term;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FunctionFactory implements Iterable<Function> {

    public static final String SCHEMA = "functions";

    private long lastId = 0;
    private long firstId = 0;

    private Cache cache = new Cache();
    private User user= null;

    public FunctionFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(FunctionFactory base) {
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            cache.add(base.cache);
        } else {
            lastId = 0;
            firstId = 0;
            cache.clear();
        }
    }

    public void commit(FunctionFactory base) {
        List<Function> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (p.getId() < base.firstId) {
                break;
            }
            list.add(0, (Function) p);
        }
        for (Function p : list) {
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


    public Function add(Term name, ArgList arguments) {
        Function p = new Function(user);
        p.setName(name);
        p.setRange(arguments.size());
        p.getArguments().addAll(arguments);
        p.getArguments().add(new Argument());
        p.setId(lastId++);
        cache.add(p);
        return p;
    }

    public Function get(long id) {
        Function t = (Function) cache.get(id);
        if (t == null) {
            try {
                t = (Function) user.getStorage(SCHEMA).get(id);
                if (t != null) {
                    cache.add(t);
                    t.linkExternal(user);
                }
            } catch (IOException | ClassNotFoundException e) {
                //TODO: Сделать runtime error
                e.printStackTrace();
            }
        }
        return t;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getFunctions());
        } else {
            transaction(null);
        }
    }


    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }

    @Override
    public Iterator<Function> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new FunctionIterator(true, cache, storage);
    }

    public class FunctionIterator extends DataIterator {

        public FunctionIterator(boolean backward, Cache cache, Storage storage) {
            super(backward, cache, storage);
        }

        @Override
        public Identifiable next() {
            Identifiable next = super.next();
            next.linkExternal(user);
            return next;
        }
    }

}
