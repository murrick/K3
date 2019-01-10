package kanger.primitives;

import kanger.interfaces.Identifiable;

import java.util.Iterator;

public class UnitIterator implements Iterator<Identifiable> {

    Identifiable current = null;
    Identifiable root = null;

    public UnitIterator(Identifiable root) {
        this.root = root;
    }

    @Override
    public boolean hasNext() {
        if (current == null) {
            return root != null;
        } else {
            return current.getNext() != null;
        }
    }

    @Override
    public Identifiable next() {
        if (current == null) {
            current = root;
        } else {
            current = current.getNext();
        }
        return current;
    }
}
