package kanger.factory;

import kanger.User;
import kanger.calculator.Calculator;
import kanger.interfaces.ICache;
import kanger.interfaces.IStep;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.storage.Escalera;
import kanger.units.Function;
import kanger.units.Term;

import java.io.IOException;
import java.util.Iterator;

public class FunctionFactory implements Iterable<Function> {

    public static final String SCHEMA = "functions";

    private long lastId = 0;
    private long firstId = 0;

    private ICache cache;
    private User user = null;

    public FunctionFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(FunctionFactory base) {
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            cache = new Escalera(user, SCHEMA, base.cache);
        } else {
            cache = new Escalera(user, SCHEMA, null);
            if (!cache.isEmpty()) {
                lastId = cache.getRoot().getId() + 1;
                firstId = lastId;
            } else {
                lastId = 0;
                firstId = 0;
            }
        }
    }

    public void commit(FunctionFactory base) throws Exception {
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
            lastId = cache.getRoot().getId() + 1;
            if (cache.getTop() == null) {
                cache.setTop(base.cache.getTop());
                firstId = cache.getTop().getId();
            }
        }
//
//        List<Function> list = new ArrayList();
//        for (Object p : base.cache) {
//            if (((Identifiable) p).getId() < base.firstId) {
//                break;
//            }
//            list.add(0, (Function) p);
//        }
//        for (Function p : list) {
//            p.setId(lastId++);
//            cache.add(p);
//        }
    }

    public void update() throws Exception {
        if (cache.update()) {
            firstId = lastId;
        }
    }


    public Function add(Term name, ArgList arguments) throws Exception {
        Function f = new Function(user);
        f.setName(name);
        f.setRange(arguments.size());
        f.getArguments().addAll(arguments);
        f.getArguments().add(new Argument());
        f.setId(lastId++);
        cache.add(f);

        if (!f.isCalculable()) {
            new Calculator(user).calculate(f, false);
        }

        return f;
    }

    public Function load(long id) throws IOException, ClassNotFoundException {
        Function t = get(id);
        if (t == null && !user.isClosed()) {
            IStep s = user.getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (Function) s.getData();
                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    public Function get(long id) throws IOException, ClassNotFoundException {
        Function t = (Function) cache.get(id);
        return t;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getFunctions());
        } else {
            transaction(null);
        }
    }

    public void unlink() throws Exception {
        cache.unlink();
    }

    public int size() throws Exception {
        return cache.size();
    }

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

}
