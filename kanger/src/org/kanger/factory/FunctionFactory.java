package org.kanger.factory;

import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;
import org.kanger.primitives.ArgList;
import org.kanger.primitives.Argument;
import org.kanger.storage.Escalera;
import org.kanger.units.FValue;
import org.kanger.units.Function;
import org.kanger.units.Term;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FunctionFactory implements Iterable<Function> {

    public static final String SCHEMA = "functions";

    private long lastId = 0;
    private long firstId = 0;

    private ICache cache;
    private IUser user = null;

    public FunctionFactory(IUser user) {
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

    public void commit(FunctionFactory base) {
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
            lastId = cache.getRoot().getId() + 1;
            if (cache.getTop() == null) {
                cache.setTop(base.cache.getTop());
                firstId = cache.getTop().getId();
            }
        }
    }

    public void update() throws IOException {
        if (cache.update()) {
            firstId = lastId;
        }
    }


    public Function add(Term name, ArgList arguments) throws Exception {
        Function f = new Function(user);
        f.setName(name);
        f.setRange(arguments.size());
        f.getArguments().clear();
        f.getArguments().addAll(arguments);
//        f.setArguments(arguments);
        f.getArguments().add(new Argument());
        f.setId(lastId++);
        cache.add(f);

        if (!f.isCalculable()) {
            user.getMind().getCalculator().calculate(f, false);
        }

        return f;
    }

    public Function load(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
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

    public Function get(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Function t = (Function) cache.get(id);
        return t;
    }

    public void clear() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getFunctions());
        } else {
            cache.clear();
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

    public void pack() throws IOException, ClassNotFoundException {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted()) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
        }
        update();

        if (!cache.isEmpty()) {
            lastId = cache.getRoot().getId() + 1;
            firstId = lastId;
        } else {
            lastId = 0;
            firstId = 0;
        }

    }

    public void delete(Function f) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        f.setDeleted();
        FValue v = user.getMind().getFValues().find(f);
        if (v != null) {
            user.getMind().getFValues().delete(v);
        }
    }
}
