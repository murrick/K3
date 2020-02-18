package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
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

//    private long lastId = 0;
//    private long firstId = 0;

    private ICache cache;
    private IStep top = null;
    private Mind mind = null;

    public FunctionFactory(Mind mind) {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(FunctionFactory base) {
        if (base != null) {
//            lastId = base.lastId;
//            firstId = base.lastId;
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
//            if (!cache.isEmpty()) {
//                lastId = cache.getRoot().getId() + 1;
//                firstId = lastId;
//            } else {
//                lastId = 0;
//                firstId = 0;
//            }
        }
    }

    public void commit(FunctionFactory base) throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException {
        if (base.top != null) {
            if (cache.getRoot() == null) {
                top = base.top;
            } else {
                base.top.setNext(cache.getRoot());
            }
        }
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
                if (((IUnit) s.getData()).getMindId() == base.mind.getId()) {
                    ((IUnit) s.getData()).setMind(mind);
                    ((IUnit) s.getData()).setMindId(mind.getId());
                } else {
                    break;
                }
            }
        }
    }

    public void update() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (cache.update()) {
//            firstId = lastId;
        }
    }


    public synchronized Function add(Term name, ArgList arguments) throws Exception {
        Function f = new Function(mind);
        f.setName(name);
        f.setRange(arguments.size());
        f.getArguments().clear();
        f.getArguments().addAll(arguments);
//        f.setArguments(arguments);
        f.getArguments().add(new Argument());
        f.setId(mind.getUser().nextId(SCHEMA));
        f.setMindId(mind.getId());
        cache.add(f);
        if (top == null) {
            top = cache.getRoot();
        }

        if (!f.isCalculable()) {
            mind.getCalculator().calculate(f, false);
        }

        return f;
    }

    public Function load(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Function t = get(id);
        if (t == null && !mind.getUser().isClosed()) {
            IStep s = mind.getUser().getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (Function) s.getData(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    private Function get(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Function t = (Function) cache.get(id);
        return t;
    }

    public void clear() throws IOException, OutOfBufferException {
        if (mind.getNext() != null) {
            transaction(mind.getNext().getFunctions());
        } else {
            cache.clear();
            transaction(null);
        }
    }

//    public void unlink() throws Exception {
//        cache.unlink();
//    }

    public void mark() throws Exception {
        cache.mark();
    }


    public void commit() throws Exception {
        cache.commit();
    }

    public void release() throws Exception {
        cache.release();
    }

    public int size() throws Exception {
        return cache.size();
    }

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

    public void pack() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted()) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
        }
//        update();

//        if (!cache.isEmpty()) {
//            lastId = cache.getRoot().getId() + 1;
//            firstId = lastId;
//        } else {
//            lastId = 0;
//            firstId = 0;
//        }

    }

    public void delete(Function f) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        f.setDeleted();
        FValue v = mind.getFValues().find(f);
        if (v != null) {
            mind.getFValues().delete(v);
        }
    }
}
