package kanger.factory; 

import java.util.*;
import kanger.*;
import kanger.primitives.*;
import java.io.*;

public class FunctionFactory { 

    private Function root = null;
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private Mind mind = null;

    public FunctionFactory(Mind mind) {
        this.mind = mind;
        reset();
    } 
    
    public Function add(Domain owner) {
        Function p = new Function(owner, mind);
        p.setId(++lastID);
        p.setNext(root);
        root = p;
        return p;
    }

    public Function get(long id) {
        for (Function r = root; r != null; r = r.getNext()) {
            if (r.getId() == id) {
                return r;
            }
        }
        return null;
    }

    public Function getRoot() {
        return root;
    }

    public void setRoot(Function o) {
        root = o;
    }

    public void reset() {
        while(stack.size() > 1) {
            stack.pop();
        }
        release();
    }

    public void clear() {
        root = null;
        lastID = 0;
        stack.clear();
        mark();
    }


    public void mark() {
        stack.push(new Object[]{root, lastID});
    }

    public void commit() {
        if(!stack.empty()) {
            stack.pop();
        }
    }

    public void release() {
        if (!stack.empty()) {
            Object[] pop = stack.pop();
            Function saved = (Function) pop[0];
            lastID = (long) pop[1];
//            if (root != null && saved != null && root.getId() != saved.getId()) {
//                for (Function t = root; t != null; t = t.getNext()) {
//                    if (t.getNext() != null && t.getNext().getId() == saved.getId()) {
//                        t.setNext(null);
//                        break;
//                    }
//                }
//            }
            root = saved;
        }
        if(stack.isEmpty()) {
            mark();
        }
    }

    public int size() {
        int cnt = 0;
        for (Function q = root; q != null; q = q.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(lastID);
        dos.writeInt(size());
        for (Function r = root; r != null; r = r.getNext()) {
            r.writeCompiledData(dos);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        Function a = null, b;
        while (count-- > 0) {
            b = new Function(dis, mind);
            if (a == null) {
                root = b;
            } else {
                a.setNext(b);
            }
            a = b;
        }
    }

}
