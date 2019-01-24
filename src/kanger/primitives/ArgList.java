package kanger.primitives;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.units.Function;
import kanger.units.TValue;
import kanger.units.TVariable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ArgList extends ArrayList<Argument> implements Externalizable {

    public ArgList() {
        super();
    }

    public ArgList(int size) {
        super(size);
    }

    public ArgList(ArgList lis) {
        super(lis);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(size());
        for(Argument a : this) {
            out.writeObject(a);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int count = in.readInt();
        while (count-- > 0) {
            Argument a = (Argument) in.readObject();
            add(a);
        }
    }

    public void linkExternal(User user) throws RuntimeErrorException {
        for(Argument a : this) {
            a.linkExternal(user);
        }
    }

    @Override
    public int hashCode() {
        StringBuffer buffer = new StringBuffer();
        try {
            for (Argument a : this) {
                if (!a.isEmpty()) {
                    buffer.append(a.getValue().getId());
                }
            }
        } catch (RuntimeErrorException e) {
            e.printStackTrace(System.err);
        }
        return buffer.toString().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o != null) {
            ArgList arg = null;
            if (o instanceof ArgList) {
                arg = ((ArgList) o);
            } else if (o instanceof List) {
                arg = (ArgList) o;
            }
            if (arg != null && arg.size() == size()) {
                int i = 0;
                try {
                    for (; i < arg.size(); ++i) {
                        if (!get(i).isEmpty()
                                && !arg.get(i).isEmpty()
                                && get(i).getValue().getId() != arg.get(i).getValue().getId()) {
                            break;
                        }

                        TValue a = get(i).isTSet() ? get(i).getT().getCurrent() : get(i).getV();
                        TValue b = arg.get(i).isTSet() ? arg.get(i).getT().getCurrent() : arg.get(i).getV();
                        if (a != null && b != null && a.getTVar().getId() != b.getTVar().getId()) {
                            break;
                        }
                    }
                } catch (RuntimeErrorException e) {
                    e.printStackTrace(System.err);
                }
                if (i == arg.size()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean equalsBase(Object o) {
        if (o != null) {
            ArgList arg = null;
            if (o instanceof ArgList) {
                arg = ((ArgList) o);
            } else if (o instanceof List) {
                arg = (ArgList) o;
            }
            if (arg != null && arg.size() == size()) {
                int i = 0;
                try {
                    for (; i < arg.size(); ++i) {
                        if (!get(i).isEmpty()
                                && !arg.get(i).isEmpty()
                                && get(i).getValue().getId() != arg.get(i).getValue().getId()) {
                            break;
                        }
                    }
                } catch (RuntimeErrorException e) {
                    e.printStackTrace(System.err);
                }
                if (i == arg.size()) {
                    return true;
                }
            }
        }
        return false;
    }

    public ArgList convert() {
        ArgList list = new ArgList();
        for (int i = 0; i < size(); ++i) {
            try {
                Argument t = get(i);
                if (t.isTSet()) {
                    TValue v = t.getT().getCurrent();
                    list.add(new Argument(v));
                } else if (t.isFSet()) {
                    list.add(new Argument(t.getF().getCurrent()));
                } else {
                    list.add(new Argument(t.getValue()));
                }
            } catch (Exception x) {
            }
        }
        return list;
    }

    public ArgList convertBase() {
        ArgList list = new ArgList();
        for (int i = 0; i < size(); ++i) {
            try {
                list.add(new Argument(get(i).getValue()));
            } catch (Exception x) {
            }
        }
        return list;
    }

    public List<Function> getFunctions() {
        List<Function> list = new ArrayList<>();
        for (Argument a : this) {
            if (a.isFSet()) {
                if (!list.contains(a.getF())) {
                    list.add(a.getF());
                }
//                if (full) {
//                    List<Function> temp = a.getF().getArguments().getFunctions(full);
//                    for (Function t : temp) {
//                        if (!list.contains(t)) {
//                            list.add(t);
//                        }
//                    }
//                }
            }
        }
        return list;
    }


    public List<TVariable> getTVariables(boolean full) {
        List<TVariable> list = new ArrayList<>();
        for (Argument a : this) {
            if (a.isTSet() && !list.contains(a.getT())) {
                list.add(a.getT());
            } else if (full && a.isFSet()) {
                List<TVariable> temp = a.getF().getArguments().getTVariables(full);
                for (TVariable t : temp) {
                    if (!list.contains(t)) {
                        list.add(t);
                    }
                }
            }

        }
        return list;
    }

    public List<TValue> getTValues(boolean full) {
        List<TValue> list = new ArrayList<>();
        for (Argument a : this) {
            if (a.isTSet() && !a.isEmpty() && !list.contains(a.getT().getCurrent())) {
                list.add(a.getT().getCurrent());
            } else if (a.isVSet() && !list.contains(a.getV())) {
                list.add(a.getV());
            } else if (full && a.isFSet()) {
                List<TValue> temp = a.getF().getArguments().getTValues(full);
                for (TValue t : temp) {
                    if (!list.contains(t)) {
                        list.add(t);
                    }
                }
            } else if (full && a.isRSet()) {
                List<TValue> temp = a.getR().getCondition().getTValues(full);
                for (TValue t : temp) {
                    if (!list.contains(t)) {
                        list.add(t);
                    }
                }
            }

        }
        return list;
    }

}
