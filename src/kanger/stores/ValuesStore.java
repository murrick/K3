package kanger.stores;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.units.TValue;
import kanger.units.Term;

import java.io.IOException;
import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 28.05.15.
 */
public class ValuesStore implements Iterable<Map<String, Object>> {

    private List<ArgList> root = new ArrayList<>();

    private User user = null;

    public ValuesStore(User user) {
        this.user = user;
    }

    public void commit(ValuesStore base) {
        clear();
        if (!base.isEmpty()) {
            root.addAll(base.root);
        }
    }


    public void add(Collection<TValue> raw) {

        ArgList row = new ArgList();
        for (Identifiable one : raw) {
            row.add(new Argument(one));
        }
        if (!root.contains(row)) {
            root.add(row);
        }
    }

    public List<Term> getValues(String name) throws IOException, ClassNotFoundException {
        List<Term> list = new ArrayList<>();
        for (ArgList row : root) {
            for (Argument t : row) {
                if (name == null || name.equals(t.getV().getTVar().getName().getValue())) {
                    list.add(t.getV().getValue());
                }
            }
        }
        return list;
    }


    public void clear() {
        root.clear();
    }

    public int size() {
        return root.size();
    }

    public boolean isEmpty() {
        return root.isEmpty();
    }

    @Override
    public Iterator<Map<String, Object>> iterator() {
        return new ValuesIterator();
    }

    public class ValuesIterator implements Iterator<Map<String, Object>> {

        Iterator<ArgList> iterator = root.iterator();

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Map<String, Object> next() {
            SortedMap<String, Object> row = new TreeMap<>();
            for (Argument v : iterator.next()) {
                try {
                    row.put(v.getV().getTVar().getName().toString(), v.getV().getValue().getValue());
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace(System.err);
                }
            }
            return row;
        }

        @Override
        public void remove() {

        }
    }
}
