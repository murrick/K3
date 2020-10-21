package org.kanger.storage;

import org.kanger.interfaces.IBase;
import org.kanger.interfaces.IStep;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class Base implements IBase {

    private static long MAX_CACHE_SIZE = 1024L * 1024L;
    private final Map<Long, IStep> cache = new HashMap<>();
    private final Queue<Long> timing = new LinkedList<>();
    private Connection connection;
    private volatile long cacheSize = 0L;
    private long lastId = -1;

    private String name = "";
//    private IUser user = null;

    public Base(Connection db, String name) throws Exception {
//        this.user = user;
        this.name = name;
        if (System.getProperties().containsKey("cache.size")) {
            MAX_CACHE_SIZE = Long.parseLong(System.getProperty("cache.size"));
        }

        this.connection = db;
        try (Statement st = connection.createStatement()) {
            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS " +
                            name + " (" +
                            "id BIGINT NOT NULL, " +
                            "data BYTEA, " +
                            "CONSTRAINT " + name + "_pk PRIMARY KEY (id)" +
                            ");");
        }
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
    public void add(IStep one) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + name + " (id, data) VALUES (?, ?);")) {
            ps.setLong(1, one.getId());
            ps.setBytes(2, one.pack().getBuffer());
            ps.executeUpdate();
        }
    }

    @Override
    public void update(IStep one) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE " + name + " SET " +
                "data = ? " +
                "WHERE id = ?")) {
            ps.setBytes(1, one.pack().getBuffer());
            ps.setLong(2, one.getId());
            ps.executeUpdate();
        }
    }

    @Override
    public IStep get(long id) throws Exception {
        synchronized (cache) {
            if (cache.containsKey(id)) {
                timing.remove(id);
                timing.add(id);
                return cache.get(id);
            }
        }
        IStep step = null;
        try (PreparedStatement ps = connection.prepareStatement("SELECT data FROM " + name + " WHERE id = ?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                byte[] o = rs.getBytes("data");
                if (o != null) {

                    ByteBuffer packet = new ByteBuffer(o);
                    try {
                        packet.mark();
                        step = new Sapato(this);
//                    step.setBase(this);
                        step.apply(packet);
                    } finally {
                        packet.release();
                    }
                    step.setSize(o.length);
//                if (step.getData() instanceof IUnit) {
//                    ((IUnit) step.getData()).setUser(user);
//                }

                    synchronized (cache) {
                        if (!cache.containsKey(id)) {
                            cache.put(id, step);
                            timing.add(id);
                            cacheSize += step.getSize();
                            while (cacheSize > MAX_CACHE_SIZE && timing.size() > 1) {
                                long topId = timing.poll();
                                IStep top = cache.remove(topId);
                                cacheSize -= top.getSize();
                            }
                        }
                    }

//                    try {
//                        ((Identifiable) step.getData()).linkExternal(user);
//                    } catch (Exception e) {
//                        e.printStackTrace(System.err);
//                    }
////                cache.remove(id);
//                }
                }
            }
        }
        return step;

    }

    @Override
    public int size() throws Exception {
        int count = 0;
        try (Statement st = connection.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + name + ";");
            if (rs.next()) {
                count = rs.getInt(1);
            }
        }
        return count;
    }

    @Override
    public void clearCache() {
        synchronized (cache) {
            cache.clear();
            timing.clear();
            cacheSize = 0;
        }
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
    public void delete(long id) throws Exception {
        synchronized (cache) {
            if (cache.containsKey(id)) {
                timing.remove(id);
                IStep top = cache.remove(id);
                cacheSize -= top.getSize();
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + name + " WHERE id = ?;")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public void clear() throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + name + ";")) {
            ps.executeUpdate();
        }
        clearCache();
        lastId = 0;
    }

    @Override
    public boolean containsKey(long id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM " + name + " WHERE id = ?;")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }

    @Override
    public IStep getRoot() {
        try {
            if (isEmpty()) {
                return null;
            } else {
                long id = -1;
                try (PreparedStatement ps = connection.prepareStatement("SELECT MAX(id) FROM " + name + ";")) {
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        id = rs.getLong(1);
                    }
                }
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
                long id = -1;
                try (PreparedStatement ps = connection.prepareStatement("SELECT MIN(id) FROM " + name + ";")) {
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        id = rs.getLong(1);
                    }
                }
                IStep step = get(id);
                return step;
            }
        } catch (Exception e) {
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

}
