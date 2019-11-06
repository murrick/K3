package org.kanger.primitives;

import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.ParametersIncompleteException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IUser;
import org.kanger.storage.ByteBuffer;
import org.kanger.units.Function;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;
import org.kanger.units.Term;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

public class ArgList extends ArrayList<Argument> implements Externalizable {

    private transient IUser user = null;

    public ArgList() {
        super();
    }

    public ArgList(int size) {
        super(size);
    }

    public ArgList(ArgList lis) {
        super(lis);
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putInt(size());
        for (Argument a : this) {
            packet.append(a.pack());
        }
        return packet.createMarked();
    }

    public ArgList apply(ByteBuffer packet) throws OutOfBufferException {
        int count = packet.getInt();
        while (count-- > 0) {
            try {
                packet.mark();
                Argument a = new Argument().apply(packet);
                a.setUser(user);
                add(a);
            } finally {
                packet.release();
            }
        }
        return this;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.write(pack().getBuffer());
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        try {
            apply(new ByteBuffer(in));
        } catch (OutOfBufferException e) {
        }
    }

//    public void linkExternal(User user) throws IOException, ClassNotFoundException {
//        for(Argument a : this) {
//            a.linkExternal(user);
//        }
//    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        try {
            for (Argument a : this) {
                if (!a.isEmpty()) {
                    hashCode = 31 * hashCode + a.getValue().hashCode();
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        return hashCode;

//        StringBuffer buffer = new StringBuffer();
//        try {
//            for (Argument a : this) {
//                if (!a.isEmpty()) {
//                    buffer.append(a.getValue().getId());
//                }
//            }
//        } catch (RuntimeErrorException e) {
//            e.printStackTrace(System.err);
//        }
//        return buffer.toString().hashCode();
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
                        if (a != null && b != null && a.getTVarId() != b.getTVarId()) {
                            break;
                        }
                    }
                } catch (Exception e) {
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
                } catch (Exception e) {
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

    public List<Function> getFunctions() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
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


    public List<TVariable> getTVariables(boolean full) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        List<TVariable> list = new ArrayList<>();
        for (Argument a : this) {
            //TODO: Костыль
            a.setUser(user);
            if (a.isTSet() && !a.getT().isDeleted() && !a.getT().isDeleted() && !list.contains(a.getT())) {
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

    public List<Term> getCVariables(boolean full) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        List<Term> list = new ArrayList<>();
        for (Argument a : this) {
            //TODO: Костыль
            a.setUser(user);
            if (a.isCVar() && !a.getValue().isDeleted() && !list.contains(a.getValue())) {
                list.add(a.getValue());
            } else if (full && a.isFSet()) {
                List<Term> temp = a.getF().getArguments().getCVariables(full);
                for (Term t : temp) {
                    if (!list.contains(t)) {
                        list.add(t);
                    }
                }
            }

        }
        return list;
    }

    public List<TValue> getTValues(boolean full) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        List<TValue> list = new ArrayList<>();
        for (Argument a : this) {
            if (a.isTSet() && !a.getT().isDeleted() && !a.isEmpty() && !list.contains(a.getT().getCurrent())) {
                list.add(a.getT().getCurrent());
            } else if (a.isVSet() && !a.getV().isDeleted() && !list.contains(a.getV())) {
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

    @Override
    public String toString() {
        String str = "[";
        for (Argument a : this) {
            if (str.length() > 1) {
                str += ", ";
            }
            try {
                str += a.isVSet() ? a.getV().toString() : a.toString();
            } catch (IOException | ClassNotFoundException | OutOfBufferException | RuntimeErrorException e) {
                e.printStackTrace(System.err);
            }
        }
        str += "]";
        return str;
    }

    public IUser getUser() {
        return user;
    }

    public void setUser(IUser user) {
        this.user = user;
        for (Argument a : this) {
            a.setUser(user);
        }
    }

    public List<Term> getStamp() throws IOException, ClassNotFoundException, ParametersIncompleteException, OutOfBufferException, RuntimeErrorException {
        List<Term> list = new ArrayList<>();
        for (TVariable t : getTVariables(true)) {
            if (t.isEmpty()) {
                throw new ParametersIncompleteException(t.toString());
            }
            list.add(t.getValue());
        }
        return list;
    }

    public boolean equalsStamp(List<Term> list) throws IOException, ClassNotFoundException, RuntimeErrorException, OutOfBufferException {
        try {
            List<Term> curr = getStamp();
            if (curr.size() == list.size()) {
                for (int i = 0; i < curr.size(); ++i) {
                    if (curr.get(i).isEmpty() || curr.get(i).getId() != list.get(i).getId()) {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        } catch (ParametersIncompleteException e) {
            return false;
        }
    }

    public void applyArguments(ArgList arguments) throws Exception {
        for (int i = 0; i < this.size(); ++i) {
            this.get(i).setValue(arguments.get(i).getValue());
        }
    }

    public void applyStamp(List<Term> list) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        List<TVariable> curr = getTVariables(true);
        for (int i = 0; i < curr.size(); ++i) {
            if (curr.get(i).find(list.get(i)) != null) {
                curr.get(i).setValue(list.get(i));
            }
        }
    }

    public boolean contains(Term t) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        for (Argument a : this) {
            if (a.getValue().getId() == t.getId()) {
                return true;
            }
        }
        return false;
    }

    public Argument remove(Term t) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        for (Argument a : this) {
            if (a.getValue().getId() == t.getId()) {
                this.remove(a);
                return a;
            }
        }
        return null;
    }

    public UnitType getUnitType() {
        return UnitType.ARGLIST;
    }

}
