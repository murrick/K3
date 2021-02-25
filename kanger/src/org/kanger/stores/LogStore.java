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

package org.kanger.stores;

import org.kanger.Mind;
import org.kanger.enums.Enums;
import org.kanger.enums.LogMode;
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.ILogEntry;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.LogEntry;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Quznetsov on 28.05.15.
 */
public class LogStore implements IFactory<ILogEntry> {

    private List<ILogEntry> root = null;
    private boolean enableLogging = true;
    private final Mind mind;

    public LogStore(Mind mind) {
        this.mind = mind;
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
                root.add(new LogEntry(LogMode.TIMING, "* LOG COMMITTED AT " + new Date(System.currentTimeMillis()) + " --"));
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return root == null || root.isEmpty();
    }

    public LogEntry add(LogMode m, IRule r) throws Exception {
        if (!enableLogging) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
            root.add(new LogEntry(LogMode.TIMING, "* LOG START AT " + new Date(System.currentTimeMillis()) + " --"));
        }
        LogEntry log = null;
        List<List<String>> net = mind.formatTree(r);
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

    public ILogEntry find(LogMode m, String s) {
        for (ILogEntry e : root) {
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

    public ILogEntry get(int index) {
        return root.get(index);
    }

    public Object find(Object... objects) {
        return root.indexOf(objects[0]);
    }

    public List<ILogEntry> getRoot() {
        return root;
    }

    public ILogEntry getCurrent(LogMode mode) {
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

    @Override
    public LogEntry get(long id) throws Exception {
        return null;
    }

    public int size() {
        return root == null ? 0 : root.size();
    }


    @Override
    public Iterator<ILogEntry> iterator() {
        return root.iterator();
    }
}
