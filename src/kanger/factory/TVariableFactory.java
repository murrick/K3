package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.storage.Cache;
import kanger.units.Right;
import kanger.units.TVariable;
import kanger.units.Term;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class TVariableFactory {

    public static final String SCHEMA = "tvariables";

    private long lastId = 0;
    private long firstId = 0;

    private Cache cache;
    private User user = null;

    public TVariableFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(TVariableFactory base) {
//        cache.clear();
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            cache = new Cache(base.cache);
        } else {
            lastId = 0;
            firstId = 0;
            cache = user.getStorage(SCHEMA);
        }
    }

    public void commit(TVariableFactory base, Collection vars) {
        List<TVariable> list = new ArrayList();
        for (Object p : base.cache) {
            if (((Identifiable) p).getId() < base.firstId) {
                break;
            }
            list.add(0, (TVariable) p);
        }
        for (TVariable p : list) {
            p.setId(lastId++);
            cache.add(p);
            vars.add(p);
        }
    }

    public void update() throws RuntimeErrorException {
        if (!user.isClosed()) {
            //TODO: Коммит в БД
            firstId = lastId;
        }
    }

    public TVariable createTVar(Term name, Right r) {
        TVariable p = new TVariable(user);
        p.setId(lastId++);
        p.setIndex(user.getMind().getTerms().nextVarIndex());
        p.setRight(r);
        p.setName(name);
        cache.add(p);
        return p;
    }

    public TVariable get(long id) {
        TVariable t = (TVariable) cache.get(id);
        return t;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getTVars());
        } else {
            transaction(null);
        }
    }

    public int size() {
        return cache.size();
    }

}
