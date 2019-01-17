package kanger.factory;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.DataIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.Right;
import kanger.units.Tree;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by murray on 25.05.15.
 */
public class TreeFactory implements Iterable<Tree>{

    public static final String SCHEMA = "trees";

    private long lastId = 0;
    private long firstId = 0;

    private Cache cache = new Cache();
    private User user = null;

    public TreeFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(TreeFactory base) {
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

    public void commit(TreeFactory base) {
        List<Tree> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (p.getId() < base.firstId) {
                break;
            }
            list.add(0, (Tree) p);
        }
        for (Tree p : list) {
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

    public Tree add(Right r) {
        Tree p = new Tree(user);
        p.setId(lastId++);
        p.setRight(r);
        cache.add(p);
        return p;
    }

    public Tree get(long id) {
        Tree t = (Tree) cache.get(id);
        if (t == null) {
            try {
                t = (Tree) user.getStorage(SCHEMA).get(id);
                if (t != null) {
                    cache.add(t);
                    t.linkExternal();
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return t;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getTrees());
        } else {
            transaction(null);
        }
    }

    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }

    @Override
    public Iterator<Tree> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new DataIterator(true, cache, storage);
    }

}
