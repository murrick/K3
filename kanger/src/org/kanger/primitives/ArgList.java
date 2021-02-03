package org.kanger.primitives;

import org.kanger.Mind;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.ParametersIncompleteException;
import org.kanger.storage.ByteBuffer;
import org.kanger.units.Function;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;
import org.kanger.units.Term;

import java.util.ArrayList;
import java.util.List;

public class ArgList extends ArrayList<Argument> {

    private Mind mind = null;
//    private List<TVariable> tVariables = null;
//    private List<Long> tVariablesIds = new ArrayList<>();

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
//                a.setUser(user);
                add(a);
            } finally {
                packet.release();
            }
        }
        return this;
    }

//    public int getHash(Mind mind) {
//        int hashCode = 1;
//        try {
//            for (Argument a : this) {
//                if (!a.isEmpty(mind)) {
//                    long id = a.getValue(mind).getId();
////                    hashCode = 31 * hashCode + a.getValue(mind).hashCode();
//                    hashCode = 31 * hashCode + (int) (id ^ (id >>> 32));
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace(System.err);
//        }
//
//        return hashCode;
//
//    }

    public int getHash(Mind mind) {
        int hashCode = 1;
        try {
            for (Argument a : this) {
                if (!a.isEmpty(mind)) {
//                    long id = a.getValue(mind).getId();
                    hashCode = 31 * hashCode + a.getValue(mind).hashCode();
//                    hashCode = 31 * hashCode + (int) (id ^ (id >>> 32));
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return hashCode;
    }

    public int hashCode() {
        return getHash(mind);
    }

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
                        if (!get(i).isEmpty(mind)
                                && !arg.get(i).isEmpty(mind)
                                && get(i).getValue(mind).getId() != arg.get(i).getValue(mind).getId()) {
                            break;
                        }

                        TValue a = get(i).isTSet() ? get(i).getT(mind).getCurrent() : get(i).getV(mind);
                        TValue b = arg.get(i).isTSet() ? arg.get(i).getT(mind).getCurrent() : arg.get(i).getV(mind);
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

    public boolean equalsBase(Mind mind, Object o) {
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
                        if (!get(i).isEmpty(mind)
                                && !arg.get(i).isEmpty(mind)
                                && get(i).getValue(mind).getId() != arg.get(i).getValue(mind).getId()) {
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

    public ArgList convert(Mind mind) {
        ArgList list = new ArgList();
        for (int i = 0; i < size(); ++i) {
            try {
                Argument t = get(i);
                if (t.isTSet()) {
                    TValue v = t.getT(mind).getCurrent();
                    list.add(new Argument(v));
                } else if (t.isFSet()) {
                    list.add(new Argument(t.getF(mind).getCurrent()));
                } else {
                    list.add(new Argument(t.getValue(mind)));
                }
            } catch (Exception x) {
            }
        }
        list.mind = mind;
        return list;
    }

    public ArgList convertBase(Mind mind) {
        ArgList list = new ArgList();
        for (int i = 0; i < size(); ++i) {
            try {
                Term t = get(i).getValue(mind);
//                if(t.isXVariable()) {
//                    t.toCVariable();
//                }

//                if(t.isCVariable()) {
//                    t = mind.getTerms().createCVar(t.getRight(), t.getName());
//                }
                list.add(new Argument(t));
            } catch (Exception x) {
            }
        }
        list.mind = mind;
        return list;
    }

//    public void setMind(Mind mind) {
//        this.mind = mind;
//    }

    public List<Function> getFunctions(Mind mind) throws Exception {
        List<Function> list = new ArrayList<>();
        for (Argument a : this) {
            if (a.isFSet()) {
                if (!list.contains(a.getF(mind))) {
                    list.add(a.getF(mind));
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


    public List<TVariable> getTVariables(Mind mind) throws Exception {
//        if(tVariables == null || tVariables.size() != tVariablesIds.size()) {
//            tVariables = new ArrayList<>();
//            for(long id : tVariablesIds) {
//                TVariable t = mind.getTVars().load(id);
//                tVariables.add(t);
//            }
//        }
//        return  tVariables;
//
        List<TVariable> list = new ArrayList<>();
        for (Argument a : this) {
            //TODO: Костыль
//            a.setUser(user);
            if (a.isTSet() && !a.getT(mind).isDeleted() && !a.getT(mind).isDeleted() && !list.contains(a.getT(mind))) {
                list.add(a.getT(mind));
            } else if (a.isFSet()) {
                List<TVariable> temp = a.getF(mind).getArguments().getTVariables(mind);
                for (TVariable t : temp) {
                    if (!list.contains(t)) {
                        list.add(t);
                    }
                }
            }

        }
        return list;
    }

    public List<Term> getCVariables(Mind mind) throws Exception {
        List<Term> list = new ArrayList<>();
        for (Argument a : this) {
            //TODO: Костыль
//            a.setUser(user);
            if (!a.isEmpty(mind) && a.getValue(mind).isCVariable() && !a.getValue(mind).isDeleted() && !list.contains(a.getValue(mind))) {
                Term t = a.getValue(mind);
                //TODO: Костыль
//                t.setMind(mind);
                list.add(t);
            } else if (a.isFSet()) {
                List<Term> temp = a.getF(mind).getArguments().getCVariables(mind);
                for (Term t : temp) {
                    if (!list.contains(t)) {
                        //TODO: Костыль
//                        t.setMind(mind);
                        list.add(t);
                    }
                }
            }
        }
        return list;
    }

    public List<TValue> getTValues(Mind mind, boolean full) throws Exception {
        List<TValue> list = new ArrayList<>();
        for (Argument a : this) {
            if (a.isTSet() && !a.getT(mind).isDeleted() && !a.isEmpty(mind) && !list.contains(a.getT(mind).getCurrent())) {
                list.add(a.getT(mind).getCurrent());
            } else if (a.isVSet() && !a.getV(mind).isDeleted() && !list.contains(a.getV(mind))) {
                list.add(a.getV(mind));
            } else if (full && a.isFSet()) {
                List<TValue> temp = a.getF(mind).getArguments().getTValues(mind, full);
                for (TValue t : temp) {
                    if (!list.contains(t)) {
                        list.add(t);
                    }
                }
            } else if (full && a.isRSet()) {
                List<TValue> temp = a.getR(mind).getCondition().getTValues(mind, full);
                for (TValue t : temp) {
                    if (!list.contains(t)) {
                        list.add(t);
                    }
                }
            }

        }
        return list;
    }

    public List<Term> getTerms(Mind mind) throws Exception {
        List<Term> list = new ArrayList<>();
        for (Argument a : this) {
            //TODO: Костыль
//            a.setUser(user);
            if (a.getType() == ArgumentType.TERM) {
                list.add(a.getValue(mind));
            } else if (a.isFSet()) {
                List<Term> temp = a.getF(mind).getArguments().getTerms(mind);
                for (Term t : temp) {
                    if (!list.contains(t)) {
                        list.add(t);
                    }
                }
            }

        }
        return list;
    }

    public String asString(Mind mind) {
        String str = "[";
        for (Argument a : this) {
            if (str.length() > 1) {
                str += ", ";
            }
            try {
                str += a.isVSet() ? a.getV(mind).toString() : a.asString(mind);
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
        str += "]";
        return str;
    }

//    public IUser getUser() {
//        return user;
//    }

//    public void setUser(IUser user) {
//        this.user = user;
//        for (Argument a : this) {
//            a.setUser(user);
//        }
//    }

    public List<Term> getStamp(Mind mind) throws Exception {
        List<Term> list = new ArrayList<>();
        for (TVariable t : getTVariables(mind)) {
            if (t.isEmpty()) {
                throw new ParametersIncompleteException(t.toString());
            }
            list.add(t.getValue());
        }
        return list;
    }

    public boolean equalsStamp(Mind mind, List<Term> list) throws Exception {
        try {
            List<Term> curr = getStamp(mind);
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

    public void applyArguments(Mind mind, ArgList arguments) throws Exception {
        for (int i = 0; i < this.size(); ++i) {
            this.get(i).setValue(mind, arguments.get(i).getValue(mind));
        }
//        this.setMind(mind);
    }

    public void applyStamp(Mind mind, List<Term> list) throws Exception {
        List<TVariable> curr = getTVariables(mind);
        for (int i = 0; i < curr.size(); ++i) {
            if (curr.get(i).find(list.get(i)) != null) {
                curr.get(i).setValue(list.get(i));
            }
        }
    }

    public boolean contains(Mind mind, Term t) throws Exception {
        for (Argument a : this) {
            if (a.getValue(mind).getId() == t.getId()) {
                return true;
            }
        }
        return false;
    }

    public Argument remove(Mind mind, Term t) throws Exception {
        for (Argument a : this) {
            if (a.getValue(mind).getId() == t.getId()) {
                this.remove(a);
                return a;
            }
        }
        return null;
    }

    public UnitType getUnitType() {
        return UnitType.ARGLIST;
    }

//    @Override
//    public boolean add(Argument argument) {
//        if (argument.isTSet()) {
//            if (!tVariablesIds.contains(argument.getId())) {
//                tVariablesIds.add(argument.getId());
//            }
//        }
//        return super.add(argument);
//    }

    public boolean isOverlaps(ArgList arg) throws Exception {

//        for (Argument a : this) {
//            boolean found = false;
//            for (Argument b : arg) {
//                if (!a.isEmpty(mind) && !b.isEmpty(mind) && a.getValue(mind).getId() == b.getValue(mind).getId()) {
//                    found = true;
//                    break;
//                }
//            }
//            if (!found) {
//                return false;
//            }
//        }
//        return true;

        for (Argument a : this) {
            for (Argument b : arg) {
                if (!a.isEmpty(mind) && !b.isEmpty(mind) && a.getValue(mind).getId() == b.getValue(mind).getId()) {
                    return true;
                }
            }
        }
        return false;

    }

}
