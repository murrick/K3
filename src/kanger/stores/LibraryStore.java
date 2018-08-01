package kanger.stores;

import kanger.Mind;
import kanger.compiler.SysOp;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Dmitry Kuznetsov on 25.01.2016.
 */
public class LibraryStore {
    private SysOp root = null;
    private SysOp save = null;
    private Map<String, SysOp> index = new HashMap<>();
    private Mind mind = null;

    public LibraryStore(Mind mind) {
        this.mind = mind;
    }

    public SysOp add(SysOp s) {
        String key = s.toString();
        SysOp x = find(key);
        if (x != null) {
            x.setMode(s.getMode());
            x.setProc(s.getProc());
            x.getScripts().clear();
            x.getScripts().addAll(s.getScripts());
        } else {
            s.setNext(root);
            root = s;
            index.put(key, s);
        }
        return s;
    }

    public boolean remove(String key) {
        SysOp x = find(key);
        if (x != null) {
            if (x == root) {
                root = root.getNext();
            } else {
                SysOp y;
                for (y = root; y.getNext() != null && !y.getNext().toString().equals(x.toString()); y = y.getNext()) ;
                if (y != null) {
                    y.setNext(x.getNext());
                }
            }
            index.remove(key);
        }
        return x != null;
    }

    public SysOp find(String key) {
        if (index.containsKey(key)) {
            return index.get(key);
        } else {
            return null;
        }
    }

    //    public void mark() {
//        save = root;
//    }
//
//    public void release() {
//        root = save;
//    }
//
    public void reset() {
        root = null;
        save = null;
        index.clear();
    }

//    public LibraryStore clone(Mind mind) {
//        LibraryStore stores = new LibraryStore(mind);
//        stores.root = root;
//        stores.save = root;
//        for(String key : index.keySet()) {
//            stores.index.put(key, index.get(key));
//        }
//        return stores;
//    }
//
//    public void commit() {
//        LibraryStore parent = mind.getParent().getLibrary();
//        for(SysOp op = root; op != null && op != save; op = op.getNext()) {
//            parent.add(op);
//        }
//    }

    public Map<String, SysOp> getRoot() {
        return index;
    }
}
