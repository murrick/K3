/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger.storage;

import org.cojen.tupl.Cursor;
import org.cojen.tupl.Database;
import org.cojen.tupl.Index;
import org.cojen.tupl.Transaction;
import org.kanger.User;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IStep;

import java.io.IOException;
import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class Base implements IBase {

    private static long MAX_CACHE_SIZE = 1024L * 1024L;
    private static boolean CACHE_ENABLE = true;

    private final Index index;
    private final Map<Long, IStep> cache = new HashMap<>();
    private final Queue<Long> timing = new LinkedList<>();
    private volatile long cacheSize = 0L;
    private long lastId = -1;
    private final Object locker = new Object();
    private Class udf = null;

    private String name = "";
//    private IUser user = null;

    public Base(Database db, String name, IUser user) throws Exception {
//        this.user = user;
        this.name = name;
        try {
            ((User) user).getUdf();
            this.udf = ((User) user).getUdf().getClass();
        } catch (RuntimeErrorException e) {
            //
        }

        MAX_CACHE_SIZE = Long.parseLong(user.getProperty("cache.size", (1024L * 1024L) + ""));
        CACHE_ENABLE = Boolean.parseBoolean(user.getProperty("cache.enable", "true"));

        this.index = db.openIndex(name + ".index");
        IStep root = getRoot();
        if (root != null) {
            lastId = root.getId() + 1;
        } else {
            lastId = 0;
        }
    }

//    private byte[] fromObject(Object o) {
//        if (o instanceof Long) {
//            return new ByteBuffer().putByte(0).putLong((Long) o).getBuffer();
//        } else { //if(o instanceof Sapato) {
//            return new ByteBuffer().putByte(1).append(((Sapato) o).pack()).getBuffer();
//        }
////        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
////        ObjectOutput out = new ObjectOutputStream(buffer);
////        out.writeObject(o);
////        out.close();
////        return buffer.toByteArray();
//    }

//    private Object toObject(byte[] bytes) throws OutOfBufferException, RuntimeErrorException {
//        if (bytes == null) {
//            return null;
//        } else {
//            ByteBuffer packet = new ByteBuffer(bytes);
//            int mode = packet.getByte();
//            switch (mode) {
//                case 0:
//                    return packet.getLong();
//                default:
//                    try {
//                        packet.mark();
//                        Sapato s = new Sapato();
//                        s.setBase(this);
//                        s.apply(user, packet);
//                        return s;
//                    } finally {
//                        packet.release();
//                    }
//            }
////            ObjectInput in = new ObjectInputStream(new ByteArrayInputStream(bytes));
////            Object o = in.readObject();
////            in.close();
////            if (o instanceof IStep) {
////                ((IStep) o).setBase(this);
////            }
////            return o;
//        }
//    }

    @Override
    public void add(IStep one) throws IOException {
        synchronized (locker) {
            index.store(Transaction.BOGUS, new ByteBuffer().putLong(one.getId()).getBuffer(), one.pack().getBuffer());
        }

//        int h = one.getHash();
//        Set<Long> set = (Set<Long>) toObject(hash.load(null, fromObject(h)));
//        if (set == null) {
//            set = new HashSet<>();
//        }
//        set.add(one.getId());
//        hash.store(null, fromObject(h), fromObject(set));
    }

    @Override
    public void update(IStep one) throws IOException {
        add(one);
    }

    @Override
    public IStep get(long id) throws Exception {
        if (CACHE_ENABLE) {
            synchronized (cache) {
                if (cache.containsKey(id)) {
                    timing.remove(id);
                    timing.add(id);
                    return cache.get(id);
                }
            }
        }

        synchronized (locker) {
            IStep step = null;
            byte[] o = index.load(Transaction.BOGUS, new ByteBuffer().putLong(id).getBuffer());
            if (o != null) {
                ByteBuffer packet = new ByteBuffer(o);
                try {
                    packet.mark();
                    step = new Sapato(this);
                    step.apply(packet);
                } finally {
                    packet.release();
                }
                step.setSize(o.length);

//                if (step.getData() instanceof IUnit) {
//                    ((IUnit) step.getData()).setUser(user);
//                }
                if (CACHE_ENABLE) {
                    synchronized (cache) {
                        if (!cache.containsKey(id)) {
                            timing.add(id);
                            cache.put(id, step);
                            cacheSize += step.getSize();
                            while (cacheSize > MAX_CACHE_SIZE && timing.size() > 1) {
                                long topId = timing.poll();
                                IStep top = cache.remove(topId);
                                cacheSize -= top.getSize();
                            }
                        }
                    }
                }

//                    try {
//                        ((Identifiable) step.getData()).linkExternal(user);
//                    } catch (Exception e) {
//                        System.err.println(new Date()); e.printStackTrace(System.err);
//                    }
////                cache.remove(id);
//                }
            }
            return step;
        }

    }

    //    @Override
    public int size() throws IOException {
//        synchronized (locker) {
        return (int) (index.count(null, null));
//        }
    }

    @Override
    public void clearCache() {
        if (CACHE_ENABLE) {
            synchronized (cache) {
                cache.clear();
                timing.clear();
                cacheSize = 0;
            }
        }
    }

    @Override
    public boolean isEmpty() {
        try {
            return size() == 0;
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return true;
        }
    }

    @Override
    public void delete(long id) throws IOException {
        if (CACHE_ENABLE) {
            synchronized (cache) {
                if (cache.containsKey(id)) {
                    timing.remove(id);
                    IStep top = cache.remove(id);
                    cacheSize -= top.getSize();
                }
            }
        }
        synchronized (locker) {
            Object one = index.load(Transaction.BOGUS, new ByteBuffer().putLong(id).getBuffer());
            if (one != null) {
                index.delete(Transaction.BOGUS, new ByteBuffer().putLong(id).getBuffer());
            }
        }
    }

    @Override
    public void clear() throws Exception {
        synchronized (locker) {
            while (size() > 0) {
                Cursor c = index.newCursor(Transaction.BOGUS);
                c.first();
                byte[] first = c.key();
                c.last();
                byte[] last = new ByteBuffer().putLong(new ByteBuffer(c.key()).getLong() + 1).getBuffer();
                index.evict(Transaction.BOGUS, first, last, null, true);
            }
            clearCache();
        }
        lastId = 0;
    }

    @Override
    public void reindex(IBase base, IMind mind) throws Exception {
//        if(size() > 0) {
//            Cursor c = index.newCursor(Transaction.BOGUS);
//            c.first();
//            do {
//                long id = new ByteBuffer(c.key()).getLong();
//                IStep o = get(id);
//                IUnit u = (IUnit) o.getData((Mind) mind);
//                if(!u.isDeleted(mind)) {
//                    base.add(o);
//                }
//            } while (c.next() != null);
//        }
    }

    @Override
    public boolean containsKey(long id) throws IOException {
//        synchronized (locker) {
        return index.load(Transaction.BOGUS, new ByteBuffer().putLong(id).getBuffer()) != null;
//        }
    }

    @Override
    public IStep getRoot() {
        try {
            if (isEmpty()) {
                return null;
            } else {
//                synchronized (locker) {
                Cursor c = index.newCursor(Transaction.BOGUS);
                c.last();
                long id = new ByteBuffer(c.key()).getLong();
                IStep step = get(id);
                return step;
//                }
            }
        } catch (Exception e) {
            System.err.println(new Date());
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
//                synchronized (locker) {
                Cursor c = index.newCursor(Transaction.BOGUS);
                c.first();
                long id = new ByteBuffer(c.key()).getLong();
                IStep step = get(id);
                return step;
//                }
            }
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return null;
        }
    }

//    public IUser getUser() {
//        return user;
//    }

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


    @Override
    public synchronized long lastId() {
        return lastId;
    }

    @Override
    public synchronized long nextId() {
        return lastId++;
    }

    @Override
    public void flush() throws Exception {

    }

    @Override
    public void close() throws Exception {
        index.close();
    }

    @Override
    public Class getUdf() {
        return udf;
    }

}
