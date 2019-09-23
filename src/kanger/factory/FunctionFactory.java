package kanger.factory;

import kanger.User;
import kanger.calculator.Calculator;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.storage.Cache;
import kanger.units.Function;
import kanger.units.Term;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FunctionFactory implements Iterable<Function> {

    public static final String SCHEMA = "functions";

    private long lastId = 0;
    private long firstId = 0;

    private Cache cache = new Cache();
    private User user = null;

    public FunctionFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(FunctionFactory base) {
        cache.clear();
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
        for (Object p : base.cache) {
            if (((Identifiable) p).getId() < base.firstId) {
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
            //TODO: Коммит в БД
            firstId = lastId;
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
        return cache.size();
    }

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

    public Cache use(Cache cache) {
        this.cache = cache;
        return cache;
    }
}
