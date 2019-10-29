package org.kanger.storage;

import org.cojen.tupl.Cursor;
import org.cojen.tupl.Database;
import org.cojen.tupl.Index;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class Base implements IBase {

    private static long MAX_CACHE_SIZE = 1024 * 512;

    private Index index;
    private Map<Long, IStep> cache = new HashMap<>();
    private Queue<Long> timing = new LinkedList<>();
    private volatile long cacheSize = 0L;

    private String name = "";
    private IUser user = null;

    public Base(Database db, IUser user, String name) throws IOException {
        this.user = user;
        this.name = name;
        if (System.getProperties().containsKey("cache.size")) {
            MAX_CACHE_SIZE = Long.parseLong(System.getProperty("cache.size"));
        }

        this.index = db.openIndex(name + ".index");
    }

    private byte[] fromObject(Serializable o) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(buffer);
        out.writeObject(o);
        out.close();
        return buffer.toByteArray();
    }

    private Object toObject(byte[] bytes) throws IOException, ClassNotFoundException {
        if (bytes == null) {
            return null;
        } else {
            ObjectInput in = new ObjectInputStream(new ByteArrayInputStream(bytes));
            Object o = in.readObject();
            in.close();
            if (o instanceof IStep) {
                ((IStep) o).setBase(this);
            }
            return o;
        }
    }

    @Override
    public void add(Sapato one) throws IOException {
        index.store(null, fromObject(one.getId()), fromObject(one));
//        int h = one.getHash();
//        Set<Long> set = (Set<Long>) toObject(hash.load(null, fromObject(h)));
//        if (set == null) {
//            set = new HashSet<>();
//        }
//        set.add(one.getId());
//        hash.store(null, fromObject(h), fromObject(set));
    }

    @Override
    public void update(Sapato one) throws IOException {
        index.store(null, fromObject(one.getId()), fromObject(one));
    }

    @Override
    public IStep get(long id) throws IOException, ClassNotFoundException {
        if (cache.containsKey(id)) {
            timing.remove(id);
            timing.add(id);
            return cache.get(id);
        } else {
            byte[] o = index.load(null, fromObject(id));
            IStep step = (IStep) toObject(o);
            if (step != null) {
                cache.put(id, step);
                if (step.getData() instanceof IUnit) {
                    ((IUnit) step.getData()).setUser(user);
                }

                timing.add(id);
                step.setSize(o.length);
                cacheSize += step.getSize();
                while (cacheSize > MAX_CACHE_SIZE && timing.size() > 1) {
                    long topId = timing.poll();
                    IStep top = cache.get(topId);
                    cache.remove(topId);
                    cacheSize -= top.getSize();
                }

//                    try {
//                        ((Identifiable) step.getData()).linkExternal(user);
//                    } catch (Exception e) {
//                        e.printStackTrace(System.err);
//                    }
////                cache.remove(id);
//                }
            }
            return step;
        }
    }

    @Override
    public int size() throws IOException {
        return (int) (index.count(null, null));
    }

    @Override
    public void clearCache() {
        cache.clear();
        timing.clear();
        cacheSize = 0;
    }

    @Override
    public boolean isEmpty() {
        try {
            return size() == 0;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return true;
        }
    }

    @Override
    public void delete(long id) throws IOException {
        Object one = index.load(null, fromObject(id));
        if (one != null) {
            index.delete(null, fromObject(id));
        }
    }

    @Override
    public void clear() throws IOException, ClassNotFoundException {
        while (size() > 0) {
            Cursor c = index.newCursor(null);
            c.first();
            byte[] first = c.key();
            c.last();
            byte[] last = fromObject(((long) toObject(c.key())) + 1);
            index.evict(null, first, last, null, true);
        }
        clearCache();
    }

    @Override
    public boolean containsKey(long id) throws IOException {
        return index.load(null, fromObject(id)) != null;
    }

    @Override
    public IStep getRoot() {
        try {
            if (isEmpty()) {
                return null;
            } else {
                Cursor c = index.newCursor(null);
                c.last();
                long id = (long) toObject(c.key());
                IStep step = get(id);
                return step;
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }
    }

    @Override
    public IStep getTop() {
        try {
            if (isEmpty()) {
                return null;
            } else {
                Cursor c = index.newCursor(null);
                c.first();
                long id = (long) toObject(c.key());
                IStep step = get(id);
                return step;
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }
    }

    public IUser getUser() {
        return user;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getUsedCacheSize() {
        return cacheSize;
    }

    @Override
    public long getMaxCacheSize() {
        return MAX_CACHE_SIZE;
    }


//    public void delete(long id) throws IOException {
//        cache.remove(id);
//        byte[] ident = fromObject(id);
//        index.evict(null, ident, ident, null, true);
//    }
}
