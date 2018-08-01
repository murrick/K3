package kanger.prototypes;

import kanger.Mind;
import kanger.interfaces.IEntry;
import kanger.interfaces.IFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created by murray on 02.12.16.
 */
public class Factory implements IFactory {
    protected Mark main = null;
    protected Mark applied = null;
    protected Mark core = null;

    protected Mind mind = null;

    public Factory(Mind mind) {
        this.mind = mind;
        this.core = this.applied = this.main = new Mark();
    }

    public Factory(Mind mind, IFactory parent) {
        this.mind = mind;
        this.core = this.applied = this.main = new Mark(((Factory)parent).applied);
    }

    @Override
    public void mark() {
        main = new Mark(main);
    }

    @Override
    public void release() {
        if(!main.equals(applied)) {
            main = main.next;
        }
    }

    @Override
    public void apply() {
        applied = main;
    }

    @Override
    public void reset() {
        applied = main = core;
    }

    @Override
    public IEntry getRoot() {
        return main.root;
    }

    @Override
    public IEntry getApplied() {
        return applied.root;
    }

    @Override
    public IEntry append(IEntry entry) {
        entry.setId(++main.lastID);
        entry.setNext(main.root);
        main.root = entry;
        return entry;
    }

    @Override
    public IEntry register(IEntry entry) {
        IEntry e = find(entry);
        if(e != null) {
            return e;
        } else {
            entry.setId(++main.lastID);
            entry.setNext(main.root);
            main.root = entry;
            return entry;
        }
    }

    @Override
    public IEntry find(IEntry entry) {
        if (entry == null) {
            return null;
        }
        for (IEntry e = main.root; e != null; e = e.getNext()) {
            if (e.equals(entry)) {
                return e;
            }
        }
        return null;
    }

    @Override
    public void remove(IEntry entry) {
        if (entry != null && entry.getId() > applied.root.getId()) {
            if(main.root.equals(entry)) {
                main.root = entry.getNext();
            } else {
                for (IEntry e = main.root; e.getNext() != null; e = e.getNext()) {
                    if (e.getNext().equals(entry)) {
                        e.setNext(entry.getNext());
                        break;
                    }
                }
            }
        }
    }

    @Override
    public long counter() {
        return ++main.counter;
    }

    @Override
    public long getCounter() {
        return main.counter;
    }

    @Override
    public IEntry getEntry(long id) {
        for (IEntry e = main.root; e != null; e = e.getNext()) {
            if(e.getId() == id) {
                return e;
            }
        }
        return null;
    }

    @Override
    public int size() {
        int cnt = 0;
        for (IEntry e = main.root; e != null; e = e.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    @Override
    public long getLastID() {
        return main.lastID;
    }

    @Override
    public void writeCompiledData(DataOutputStream dos) throws IOException {
        int count = size();
        dos.writeInt(count);
        for (EntryX d = (EntryX) applied.getRoot(); d != null; d = (EntryX) d.getNext()) {
            d.writeCompiledData(dos);
        }
        dos.writeLong(applied.getLastID());
        dos.writeLong(applied.getCounter());
    }

    @Override
    public void readCompiledData(DataInputStream dis, Class baseClass) throws IOException {
        reset();
        int count = dis.readInt();
        while (count-- > 0) {
            try {
                EntryX e = (EntryX) baseClass.newInstance();
                e.readCompiledData(dis);
            } catch (InstantiationException | IllegalAccessException e1) {
                e1.printStackTrace();
            }

        }
        applied.setLastID(dis.readLong());
        applied.setCounter(dis.readLong());
    }



    public class Mark {
        protected IEntry root = null;
        protected long counter = 0;
        protected long lastID = 0;
        protected Mark next = null;

        public Mark() {

        }

        public Mark(Mark parent) {
            root = parent.root;
            lastID = parent.lastID;
            counter = parent.counter;
            next = parent;
        }

        public IEntry getRoot() {
            return root;
        }

        public void setRoot(IEntry root) {
            this.root = root;
        }

        public long getCounter() {
            return counter;
        }

        public void setCounter(long counter) {
            this.counter = counter;
        }

        public long getLastID() {
            return lastID;
        }

        public void setLastID(long lastID) {
            this.lastID = lastID;
        }

        public Mark getNext() {
            return next;
        }

        public void setNext(Mark next) {
            this.next = next;
        }
    }
}
