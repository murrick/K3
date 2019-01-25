package kanger.factory;

import kanger.User;
import kanger.calculator.Calculator;
import kanger.exception.RuntimeErrorException;
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
    private Cache load = new Cache();
    private User user = null;

    public FunctionFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(FunctionFactory base) {
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

    public void update() throws RuntimeErrorException {
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
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace(System.err);
                throw new RuntimeErrorException(e.toString());
            }
        }
    }


    public Function add(Term name, ArgList arguments) throws RuntimeErrorException {
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

    public Function get(long id) {
        Function t = (Function) cache.get(id);
        if (t == null) {
            t = (Function) load.get(id);
        }
        return t;
    }

    public Function load(long id) throws RuntimeErrorException {
        Function t = null;
        if (!user.isClosed()) {
            try {
                t = (Function) user.getStorage(SCHEMA).get(id);
                if (t != null) {
                    load.add(t);
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace(System.err);
                throw new RuntimeErrorException(e.toString());
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
        return new DataIterator(true, cache, storage, user);
    }
}
