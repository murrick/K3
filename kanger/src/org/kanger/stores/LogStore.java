package org.kanger.stores;

import org.kanger.Mind;
import org.kanger.enums.Enums;
import org.kanger.enums.LogMode;
import org.kanger.primitives.LogEntry;
import org.kanger.units.Domain;
import org.kanger.units.Right;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 28.05.15.
 */
public class LogStore {

    private List<LogEntry> root = null;
    private boolean enableLogging = true;
    private final Mind mind;

    public LogStore(Mind mind) {
        this.mind = mind;
    }

    public static List<List<String>> formatTree(Mind mind, Right r) throws Exception {
//        int save = mind.getDebugLevel();
//        mind.setDebugLevel(mind.getDebugLevel() & ~Enums.DEBUG_OPTION_VALUES);
        List<List<String>> list = new ArrayList<>();
        int depth = 0;
        for (List<Domain> t : r.getTree()) {
            List<String> v = new ArrayList<>();
//            v.add((t.getRight().isGenerated() ? "G" : "") + (t.isClosed() ? "C" : "") + (t.isUsed() ? "U" : "") + (t.isReady() ? "R" : ""));
            list.add(v);
            int len = 0;
            for (Domain d : t) {
                String s = d.toString(); // + (d.isUsed() ? " *" : "");
                len = Math.max(len, s.length());
                v.add(s);
            }
            depth = Math.max(depth, v.size());
            for (int i = 0; i < v.size(); ++i) {
                String s = v.get(i);
                while (s.length() < len) {
                    s += " ";
                }
                v.set(i, s);
            }
        }
        for (List<String> v : list) {
//            if(!v.isEmpty()) {
            int len = v.get(0).length();
            String s = " ";
            while (s.length() < len) {
                s += " ";
            }
            while (v.size() < depth) {
                v.add(s);
            }
//            }
        }
//        mind.setDebugLevel(save);
        return list;
    }

    public void commit(LogStore base) {
        if (!enableLogging) {
            return;
        }
        if (!base.isEmpty()) {
            if (root == null) {
                root = new ArrayList<>();
                root.add(new LogEntry(LogMode.TIMING, "* LOG START AT " + new Date(System.currentTimeMillis()) + " --"));
            }
            if (base.getRoot() != null) {
                root.addAll(base.getRoot());
                root.add(new LogEntry(LogMode.TIMING, "* LOG COMMITED AT " + new Date(System.currentTimeMillis()) + " --"));
            }
        }
    }

    private boolean isEmpty() {
        return root == null || root.isEmpty();
    }

    public LogEntry add(LogMode m, Right r) throws Exception {
        if (!enableLogging) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
            root.add(new LogEntry(LogMode.TIMING, "* LOG START AT " + new Date(System.currentTimeMillis()) + " --"));
        }
        LogEntry log = null;
        List<List<String>> net = formatTree(mind, r);
        for (int i = 0; i < net.get(0).size(); ++i) {
            String s = "";
            for (int k = 0; k < net.size(); ++k) {
                s += net.get(k).get(i);
                if (k + 1 < net.size()) {
                    s += " ";
                }
            }
            log = new LogEntry(m, s);
            root.add(log);
            if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) != 0) {
                System.out.println(log.getRecord());
            }
        }
        return log;
    }

    public LogEntry find(LogMode m, String s) {
        for (LogEntry e : root) {
            if (e.getType() == m && s.equals(e.getRecord())) {
                return e;
            }
        }
        return null;
    }

    public LogEntry add(LogMode m, String s) {
        if (!enableLogging) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
            root.add(new LogEntry(LogMode.TIMING, "* LOG START AT " + new Date(System.currentTimeMillis()) + " --"));
        }
        LogEntry log = null;
        log = new LogEntry(m, s);
        root.add(log);
        if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_RTLOGS) != 0) {
            System.out.println(log.getRecord());
        }
        return log;
    }

    public void enable(boolean enableLogging) {
        this.enableLogging = enableLogging;
    }

    public boolean isEnabled() {
        return enableLogging;
    }

    public LogEntry get(int index) {
        return root.get(index);
    }

    public Object find(Object... objects) {
        return root.indexOf(objects[0]);
    }

    public List<LogEntry> getRoot() {
        return root;
    }

    public LogEntry getCurrent(LogMode mode) {
        if (root == null || root.size() == 0) {
            return null;
        } else {
            if (mode == LogMode.ALL) {
                return root.get(root.size() - 1);
            } else {
                for (int i = root.size() - 1; i >= 0; --i) {
                    if (root.get(i).getType() == mode) {
                        return root.get(i);
                    }
                }
            }
            return null;
        }
    }

    public void clear() {
        if (enableLogging) {
            root = null;
        }
    }

    public int size() {
        return root == null ? 0 : root.size();
    }


}
