package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.storage.Cache;
import kanger.units.Right;
import kanger.units.TVariable;
import kanger.units.Term;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by murray on 25.05.15.
 */
public class TVariableFactory {

    public static final String SCHEMA = "tvariables";

    private long lastId = 0;
    private long firstId = 0;

    private Cache cache = new Cache();
    private Cache load = new Cache();
    private User user = null;

    public TVariableFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(TVariableFactory base) {
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

    public void commit(TVariableFactory base, Collection vars) {
        List<TVariable> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (p.getId() < base.firstId) {
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
                e.printStackTrace(System.err);
            } catch (ClassNotFoundException e) {
                e.printStackTrace(System.err);
            }
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

    public TVariable get(long id) throws RuntimeErrorException {
        TVariable t = (TVariable) cache.get(id);
        if (t == null) {
            t = (TVariable) load.get(id);
            if (t == null && !user.isClosed()) {
                try {
                    t = (TVariable) user.getStorage(SCHEMA).get(id);
                    if (t != null) {
                        t.linkExternal(user);
                        load.add(t);
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace(System.err);
                    throw new RuntimeErrorException(e.toString());
                }
            }
        }
        if(t == null) {
            throw new RuntimeErrorException("TVariable id=" + id + " not found");
        }
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
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }

}
