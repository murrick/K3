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

package org.kanger.primitives;

import org.kanger.Mind;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.ParametersIncompleteException;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IList;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;
import org.kanger.storage.ByteBuffer;
import org.kanger.units.*;

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class ArgumentsList extends ArrayList<IArgument> implements IList {

    private transient Mind mind = null;
//    private List<TVariable> tVariables = null;
//    private List<Long> tVariablesIds = new ArrayList<>();

    public ArgumentsList() {
        super();
    }

    public ArgumentsList(int size) {
        super(size);
    }

    public ArgumentsList(ArgumentsList lis) {
        super(lis);
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putInt(size());
        for (IArgument a : this) {
            packet.append(((Argument) a).pack());
        }
        return packet.createMarked();
    }

    public ArgumentsList apply(ByteBuffer packet) throws OutOfBufferException {
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
//            System.err.println(new Date()); e.printStackTrace(System.err);
//        }
//
//        return hashCode;
//
//    }

    public int getHash(Mind mind) {
        int hashCode = 1;
        try {
            for (IArgument a : this) {
                if (!a.isEmpty(mind)) {
//                    long id = a.getValue(mind).getId();
                    hashCode = 31 * hashCode + a.getValue(mind).hashCode();
//                    hashCode = 31 * hashCode + (int) (id ^ (id >>> 32));
                }
            }
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
        }
        return hashCode;
    }

    public int hashCode() {
        return getHash(mind);
    }

    public boolean equals(Object o) {
        if (o != null) {
            ArgumentsList arg = null;
            if (o instanceof ArgumentsList) {
                arg = ((ArgumentsList) o);
            } else if (o instanceof List) {
                arg = (ArgumentsList) o;
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

                        TValue a = null;
                        switch (get(i).getType()) {
                            case TVARIABLE:
                                a = ((TVariable) get(i).getObject(mind)).getCurrent();
                                break;
                            case TVALUE:
                                a = (TValue) get(i).getObject(mind);
                                break;
                        }
                        TValue b = null;
                        switch (arg.get(i).getType()) {
                            case TVARIABLE:
                                b = ((TVariable) arg.get(i).getObject(mind)).getCurrent();
                                break;
                            case TVALUE:
                                b = (TValue) arg.get(i).getObject(mind);
                        }
                        if (a != null && b != null && a.getTVarId() != b.getTVarId()) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.err.println(new Date());
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
            ArgumentsList arg = null;
            if (o instanceof ArgumentsList) {
                arg = ((ArgumentsList) o);
            } else if (o instanceof List) {
                arg = (ArgumentsList) o;
            }
            if (arg != null && arg.size() == size()) {
                int i = 0;
                try {
                    for (; i < arg.size(); ++i) {
                        if (!get(i).isEmpty(mind) && !arg.get(i).isEmpty(mind)
                                && ((get(i).getValue(mind).isCVariable() && arg.get(i).getValue(mind).isCVariable()
                                && (get(i).getValue(mind).getId() == arg.get(i).getValue(mind).getId()
                                || ((Term) get(i).getValue(mind)).getParentId(mind) == arg.get(i).getValue(mind).getId()
                                || get(i).getValue(mind).getId() == ((Term) arg.get(i).getValue(mind)).getParentId(mind)))
                                || get(i).getValue(mind).getId() == arg.get(i).getValue(mind).getId())) {
                        } else {
                            break;
                        }
                    }
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                }
                if (i == arg.size()) {
                    return true;
                }
            }
        }
        return false;
    }

    public ArgumentsList convert(Mind mind) {
        ArgumentsList list = new ArgumentsList();
        for (int i = 0; i < size(); ++i) {
            try {
                IArgument t = get(i);
                if (t.getType() == ArgumentType.TVARIABLE) {
                    TValue v = ((TVariable) t.getObject(mind)).getCurrent();
                    list.add(new Argument(v));
                } else if (t.getType() == ArgumentType.FUNCTION) {
                    list.add(new Argument(((Function) t.getObject(mind)).getCurrent()));
                } else {
                    list.add(new Argument(t.getValue(mind)));
                }
            } catch (Exception x) {
            }
        }
        list.mind = mind;
        return list;
    }

    public ArgumentsList convertBase(IMind mind) {
        ArgumentsList list = new ArgumentsList();
        for (int i = 0; i < size(); ++i) {
            try {
                ITerm t = get(i).getValue(mind);
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
        list.mind = (Mind) mind;
        return list;
    }

//    public void setMind(Mind mind) {
//        this.mind = mind;
//    }

    public List<Function> getFunctions(IMind mind) throws Exception {
        List<Function> list = new ArrayList<>();
        for (IArgument a : this) {
            if (a.getType() == ArgumentType.FUNCTION) {
                if (!list.contains(a.getObject(mind))) {
                    list.add((Function) a.getObject(mind));
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


    public List<TVariable> getTVariables(IMind mind) throws Exception {
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
        for (IArgument a : this) {
            //TODO: Костыль
//            a.setUser(user);
            if (a.getType() == ArgumentType.TVARIABLE
                    && !a.isDeleted(mind)
                    && !a.isDeleted(mind)
                    && !list.contains(a.getObject(mind))) {
                list.add(((TVariable) a.getObject(mind)));
            } else if (a.getType() == ArgumentType.FUNCTION) {
                List<TVariable> temp = ((Function) a.getObject(mind)).getArguments().getTVariables(mind);
                for (TVariable t : temp) {
                    if (!list.contains(t)) {
                        list.add(t);
                    }
                }
            }

        }
        return list;
    }

    public List<ITerm> getCVariables(Mind mind) throws Exception {
        List<ITerm> list = new ArrayList<>();
        for (IArgument a : this) {
            //TODO: Костыль
//            a.setUser(user);
            if (!a.isEmpty(mind) && a.getValue(mind).isCVariable() && !a.getValue(mind).isDeleted(mind) && !list.contains(a.getValue(mind))) {
                ITerm t = a.getValue(mind);
                //TODO: Костыль
//                t.setMind(mind);
                list.add(t);
            } else if (a.getType() == ArgumentType.FUNCTION) {
                List<ITerm> temp = ((Function) a.getObject(mind)).getArguments().getCVariables(mind);
                for (ITerm t : temp) {
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

    public List<TValue> getTValues(Mind mind, boolean total) throws Exception {
        List<TValue> list = new ArrayList<>();
        for (IArgument a : this) {
            if (a.getType() == ArgumentType.TVARIABLE
                    && !a.isDeleted(mind)
                    && !a.isEmpty(mind)
                    && !list.contains(((TVariable) a.getObject(mind)).getCurrent())) {
                list.add(((TVariable) a.getObject(mind)).getCurrent());
            } else if (a.getType() == ArgumentType.TVALUE
                    && !a.isDeleted(mind)
                    && !list.contains(a.getObject(mind))) {
                list.add((TValue) a.getObject(mind));
            } else if (total && a.getType() == ArgumentType.FUNCTION) {
                List<TValue> temp = ((Function) a.getObject(mind)).getArguments().getTValues(mind, total);
                for (TValue t : temp) {
                    if (!list.contains(t)) {
                        list.add(t);
                    }
                }
            } else if (total && a.getType() == ArgumentType.FVALUE) {
                List<TValue> temp = ((FValue) a.getObject(mind)).getCondition().getTValues(mind, total);
                for (TValue t : temp) {
                    if (!list.contains(t)) {
                        list.add(t);
                    }
                }
            }

        }
        return list;
    }

    public Collection<Long> getTerms(IMind mind, boolean total) throws Exception {
        Set<Long> list = new HashSet<>();
        for (IArgument a : this) {
            if (a.getType() == ArgumentType.TERM) {
                list.add(a.getValue(mind).getId());
            } else if (a.getType() == ArgumentType.FUNCTION) {
                if (total) {
                    list.add(((Function) a.getObject(mind)).getNameId());
                }
                list.addAll(((Function) a.getObject(mind)).getArguments().getTerms(mind, total));
            } else if (a.getType() == ArgumentType.TVARIABLE) {
                if (total) {
                    list.add(((TVariable) a.getObject(mind)).getNameId());
                }
            } else if (a.getType() == ArgumentType.TVALUE) {
                if (total) {
                    list.add(((TValue) a.getObject(mind)).getTVar((Mind) mind).getNameId());
                }
                list.add(a.getValue(mind).getId());
            } else if (a.getType() == ArgumentType.FVALUE) {
                list.add(((FValue) a.getObject(mind)).getValue((Mind) mind).getId());
            }
        }
        return list;
    }

//    public String asString(Mind mind) {
//        String str = "[";
//        for (IArgument a : this) {
//            if (str.length() > 1) {
//                str += ", ";
//            }
//            try {
//                str += a.getType() == ArgumentType.TVALUE ? a.getObject(mind).toString() : a.toString(mind);
//            } catch (Exception e) {
//                System.err.println(new Date()); e.printStackTrace(System.err);
//            }
//        }
//        str += "]";
//        return str;
//    }

//    public IUser getUser() {
//        return user;
//    }

//    public void setUser(IUser user) {
//        this.user = user;
//        for (Argument a : this) {
//            a.setUser(user);
//        }
//    }

    public List<ITerm> getStamp(Mind mind) throws Exception {
        List<ITerm> list = new ArrayList<>();
        for (TVariable t : getTVariables(mind)) {
            if (t.isEmpty()) {
                throw new ParametersIncompleteException(t.toString());
            }
            list.add(t.getValue());
        }
        return list;
    }

    public boolean equalsStamp(Mind mind, List<ITerm> list) throws Exception {
        try {
            List<ITerm> curr = getStamp(mind);
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

//    public void applyArguments(Mind mind, ArgumentsList arguments) throws Exception {
//        for (int i = 0; i < this.size(); ++i) {
//            this.get(i).setValue(mind, arguments.get(i).getValue(mind));
//        }
////        this.setMind(mind);
//    }

    public void applyStamp(Mind mind, List<ITerm> list) throws Exception {
        List<TVariable> curr = getTVariables(mind);
        for (int i = 0; i < curr.size(); ++i) {
            if (curr.get(i).find(list.get(i)) != null) {
                curr.get(i).setValue(list.get(i));
            }
        }
    }

    //    @Override
    public boolean contains(ITerm t, IMind mind) throws Exception {
        for (IArgument a : this) {
            if (a.getValue(mind).getId() == t.getId()) {
                return true;
            }
        }
        return false;
    }

    //    @Override
    public IArgument remove(ITerm t, IMind mind) throws Exception {
        for (IArgument a : this) {
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

    public List<Map<String, Object>> createMap(IMind mind) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        for (IArgument a : this) {
            list.add(((Argument) a).createMap(mind));
        }
        return list;
    }

    public void applyMap(List<Map<String, Object>> map) throws Exception {
        this.clear();
        for (Map<String, Object> a : map) {
            Argument argument = new Argument().applyMap(a);
            add(argument);
        }
    }

//    @Override
//    public boolean add(Argument argument) {
//        if (argument.getType() == ArgumentType.TVARIABLE) {
//            if (!tVariablesIds.contains(argument.getId())) {
//                tVariablesIds.add(argument.getId());
//            }
//        }
//        return super.add(argument);
//    }

//    public boolean isOverlaps(ArgumentsList arg) throws Exception {
//
////        for (Argument a : this) {
////            boolean found = false;
////            for (Argument b : arg) {
////                if (!a.isEmpty(mind) && !b.isEmpty(mind) && a.getValue(mind).getId() == b.getValue(mind).getId()) {
////                    found = true;
////                    break;
////                }
////            }
////            if (!found) {
////                return false;
////            }
////        }
////        return true;
//
//        for (IArgument a : this) {
//            for (IArgument b : arg) {
//                if (!a.isEmpty(mind) && !b.isEmpty(mind) && a.getValue(mind).getId() == b.getValue(mind).getId()) {
//                    return true;
//                }
//            }
//        }
//        return false;
//
//    }

}
