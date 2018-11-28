package kanger.primitives;

import kanger.User;
import kanger.compiler.Operation;
import kanger.compiler.Parser;
import kanger.enums.Enums;
import kanger.enums.Tools;
import kanger.exception.ParametersIncompleteException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Описатель варианта решения предиката
 */
public class Domain {

    private Predicate predicate = null;                         // Ссылка на описатель предиката
    private List<Argument> arguments = new ArrayList<>();       // Массив подстановочных переменных
    private boolean antc = true;                                // ! или ?
    private Right right;                                        // Ссылка на правило
    private long id = -1;                                       // id домена
    private Domain next = null;                                 // Следующий элемент
//    private Set<Domain> parents = new HashSet<>();              // Родительские домены для БД

    private User user = null;

    public Domain(User user) {
        this.user = user;
    }

    public Domain(DataInputStream dis, User user) throws IOException {
        id = dis.readLong();
        user.getMind().getDomainLinks().put(this, dis.readLong());
        int flags = dis.readInt();
        antc = (flags & 0x0001) != 0;
//        used = (flags & 0x0002) != 0;
        long id = dis.readLong();
        predicate = user.getMind().getPredicates().get(id);
        int count = dis.readInt();
        arguments.clear();
        while (count-- > 0) {
            Argument a = new Argument(dis, user);
            arguments.add(a);
        }
    }

    public Predicate getPredicate() {
        return predicate;
    }

    public void setPredicate(Predicate predicate) {
        this.predicate = predicate;
    }

    public Right getRight() {
        return right;
    }

    public void setRight(Right right) {
        this.right = right;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Domain getNext() {
        return next;
    }

    public void setNext(Domain next) {
        this.next = next;
    }

    public Argument get(int i) {
        return arguments.get(i);
    }

    public void add(Argument t) {
        arguments.add(t);
    }


//    public boolean isQueued() {
//        return mind.getQueuedDomains().contains(id);
//    }

//    public boolean isAcceptor() {
//        return mind.getAcceptorDomains().contains(id);
//    }
//
//    public void setAcceptor(boolean on) {
//        if (on) {
//            mind.getAcceptorDomains().createTVar(id);
//        } else {
//            mind.getAcceptorDomains().remove(id);
//        }
//    }
//

//    public void setQueued() {
//        mind.getQueuedDomains().createTVar(id);
//    }

    public Set<Domain> getCauses() {
        Set<Domain> list = new HashSet<>();
        for (TValue t : getTValues(true)) {
            if (!t.isEmpty()) {
                for (TValue.Solve s : t.getSolves()) {
                    list.add(s.getSrc());
                }
            }
        }
        return list;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public boolean isAntc() {
        return antc;
    }

    public void setAntc(boolean antc) {
        this.antc = antc;
    }

//    public Set<Domain> getParents() {
//        return parents;
//    }


    //    String s = String.format("%c%s(", d.isAntc() ? Enums.ANT : Enums.SUC, d.getPredicate().getName());
//    int i = 0;
//    for (TList t : d.getArguments()) {
//        String name = "_";
//        if (t.isCSet()) name = t.getC().toString();
//        else if (t.isFunction() && t.getFunction().getResult() != null)
//            name = t.getFunction().toString(); // + "=" + t.getFunction().getResult().toString();
//        else if (t.isTVariable() && t.getTVariable().getOwner() != 0) name = t.getTVariable().getValue().getTerm().getName();
//        s += name;
//        if (i + 1 != d.getPredicate().getRange()) {
//            s += String.format("%c", Enums.COMMA);
//        }
//        ++i;
//    }
//    s += ");";
//    return s;
    private String formatParam(Argument t) {
        String s = "";
        if (t.isFSet()) {
            s += t.getF().toString();
        } else if (t.isTSet()) {
            s += t.getT().toString();
        } else if (t.isVSet()) {
            s += t.getV().toString();
        } else if (!t.isEmpty()) {
            s += t.getValue().toString();
        } else {
            s += "_";
        }
        return s;
    }

    public String toString() {
        String s = String.format("%c", antc ? Enums.ANT : Enums.SUC);
        Operation op = Parser.getOp(predicate.getName());
        if (op == null) {
            s += predicate.getName() + "(";
            int i = 0;
            for (Argument t : arguments) {
                s += formatParam(t);
                if (i + 1 != predicate.getRange()) {
                    s += (char) Enums.COMMA;
                }
                ++i;
            }
            s += ")";
        } else if (op.getRange() == 1) {
            if (op.isPost()) {
                s += formatParam(arguments.get(0)) + op.getName();
            } else {
                s += op.getName() + formatParam(arguments.get(0));
            }
        } else {
            try {
                for (int i = 0; i < op.getRange(); ++i) {
                    s += formatParam(arguments.get(i));
                    if (i + 1 < op.getRange()) {
                        s += " " + op.getName() + " ";
                    }
                }
            } catch (IndexOutOfBoundsException ex) {
                System.out.print(ex);
            }
        }

        String suffix = "";
        if((user.getMind().getDebugLevel() & 0x00FF) == Enums.DEBUG_LEVEL_DEBUG) {
            suffix += " " + id;
        }
        if ((user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0) {
            suffix += /*isDest() ||*/ isQuery() || isClosed() || isUsed() || isExcluded() || isProduced() || isStored() || isCalculated()
                    ? " " +
                    //(isDest() ? "A" : "") +
                    (isQuery() ? "Q" : "") +
                    (isClosed() ? "C" : "") +
                    (isUsed() ? "U" : "") +
                    (isExcluded() ? "X" : "") +
                    (isProduced() ? "P" : "") +
                    (isStored() ? "B" : "") +
                    (isCalculated() ? "S" : "") +
                    " "
                    : "";
        }
        return s + ";" + suffix;
    }


    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(right.getId());
        int flags = (antc ? 0x0001 : 0);
//                | (used ? 0x0002 : 0);
//                | (cst ? 0x0004 : 0);
        dos.writeInt(flags);
        dos.writeLong(predicate.getId());
        dos.writeInt(arguments.size());
        for (Argument a : arguments) {
            a.writeCompiledData(dos);
        }
    }

    public boolean equalsBase(Domain o) {
        if (predicate.getId() != o.getPredicate().getId()) {
            return false;
        }
        if (arguments.size() != o.getArguments().size()) {
            return false;
        }
        for (int i = 0; i < arguments.size(); ++i) {
            if (arguments.get(i).isEmpty() || o.getArguments().get(i).isEmpty()) {
                return false;
            }
            if (arguments.get(i).getValue().getId() != o.getArguments().get(i).getValue().getId()) {
                return false;
            }
        }
        return true;
    }

    public boolean equalsSolve(Domain slave) {
        boolean success = false;
        if (slave.getPredicate().getId() == predicate.getId() && slave.isAntc() != antc) {
            success = true;
            for (int i = 0; i < slave.getPredicate().getRange(); ++i) {
                if (slave.get(i).isEmpty() || arguments.get(i).isEmpty()
                        || slave.get(i).getValue().getId() != arguments.get(i).getValue().getId()
//                        || this.isDestFor(i, slave)
//                        || slave.isDestFor(i, this)
                        || (slave.get(i).isTSet() && arguments.get(i).isTSet()
                        && slave.get(i).getT().getId() == arguments.get(i).getT().getId())) {
                    success = false;
                    break;
                }

//                    if (isDestFor(i, slave) || slave.isDestFor(i, this)) {
//                        success = false;
//                        break;
//                    } else {

//                if (get(i).isVSet() && slave.get(i).isVSet() && get(i).getV().isRelativeFor(slave.get(i).getV())) {
//                    success = false;
//                    break;
//                }

//TODO: Разобраться с isDestFor для предикатов. Использовать для операций с БД добавленные правила вместо
//                    }
            }
        }
        return success;
    }

    public boolean contains(TVariable t) {
        for (TVariable x : getTVariables(true)) {
            if (x.getId() == t.getId()) {
                return true;
            }
        }
        return false;
    }

//    public boolean isDestFor(Domain d) {
//        return mind.getSources().containsKey(this) && mind.getSources().createCVar(this).contains(d);
//    }
//
//    public void setDestFor(Domain d) {
//        if (!mind.getDestinations().containsKey(d)) {
//            mind.getDestinations().put(d, new HashSet<>());
//        }
//        if (!mind.getSources().containsKey(this)) {
//            mind.getSources().put(this, new HashSet<>());
//        }
//        mind.getDestinations().createCVar(d).createTVar(this);
//        mind.getSources().createCVar(this).createTVar(d);
//    }

    public boolean isDestFor(int index, Domain d) {
        if (index < arguments.size() && ((arguments.get(index).isTSet() && !arguments.get(index).getT().isEmpty()) || arguments.get(index).isVSet())) {
            TValue v = arguments.get(index).isVSet() ? arguments.get(index).getV() : arguments.get(index).getT().getCurrent();
            for (TValue.Solve s : v.getSolves()) {
                if (s.getIndex() == index
                        && s.getDst().getId() == id
                        && s.getSrc().getId() == d.getId()) {
                    return true;
                }
            }
        }
        return false;

    }

//    public boolean isDest() {
//        for (TVariable t : getTVariables(false)) {
//            if (!t.isEmpty() && t.getDstSolves() != null && /*contains(t)) {*/ t.getDstSolves().contains(this)) {
//                return true;
//            }
//        }
//        return false;
//        //
//        // return mind.getSources().containsKey(this) && !mind.getSources().createCVar(this).isEmpty();
//    }

    public List<TVariable> getTVariables(boolean full) {
        return Tools.getTVariables(arguments, full);
    }

    public List<TValue> getTValues(boolean full) {
        return Tools.getTValues(arguments, full);
    }

    public List<Function> getFunctions() {
        List<Function> list = new ArrayList<>();
        for (Argument a : arguments) {
            if (a.isFSet()) {
                list.add(a.getF());
            }
        }
        return list;
    }

    private boolean isEqualsArguments(List<Argument> params) {
        return isEqualsArguments(arguments, params);
    }

    private boolean isEqualsArguments(List<Argument> solves, List<Argument> params) {
        for (int i = 0; i < predicate.getRange(); ++i) {
            if (!solves.get(i).isEmpty() && !params.get(i).isEmpty() && solves.get(i).getValue().getId() != params.get(i).getValue().getId()) {
                return false;
            }
        }
        return true;
    }

    public List<Argument> convertArguments() throws ParametersIncompleteException {
        return convertArguments(arguments);
    }

    public List<Argument> convertArguments(List<Argument> args) throws ParametersIncompleteException {
        List<Argument> list = new ArrayList<>();
        for (int i = 0; i < predicate.getRange(); ++i) {
            try {
                list.add(new Argument(args.get(i).getValue()));
            } catch (Exception x) {
                throw new ParametersIncompleteException("Incomplete args: " + predicate.toString() + " : " + i);
                //System.out.println(i);
            }
        }
        return list;
    }

    public boolean isClosed() {
        if (user.getMind().getClosedDomains().containsKey(this)) {
            for (List<Argument> list : user.getMind().getClosedDomains().get(this)) {
                if (isEqualsArguments(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setClosed() {
        if (!user.getMind().getClosedDomains().containsKey(this)) {
            user.getMind().getClosedDomains().put(this, new HashSet<>());
        }
        if (!isClosed()) {
            try {
                user.getMind().getClosedDomains().get(this).add(convertArguments());
            } catch (ParametersIncompleteException e) {
//                e.printStackTrace();
            }
        }
    }


    public boolean isUsed() {
        if (user.getMind().getUsedDomains().containsKey(this)) {
            for (List<Argument> list : user.getMind().getUsedDomains().get(this)) {
                if (isEqualsArguments(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setUsed() {
        if (!user.getMind().getUsedDomains().containsKey(this)) {
            user.getMind().getUsedDomains().put(this, new HashSet<>());
        }
        if (!isUsed()) {
            try {
                user.getMind().getUsedDomains().get(this).add(convertArguments());
            } catch (ParametersIncompleteException e) {
//                e.printStackTrace();
            }
        }
    }

    public boolean isExcluded(List<Argument> args) {
        if (user.getMind().getExcludedDomains().containsKey(this)) {
            for (List<Argument> list : user.getMind().getExcludedDomains().get(this)) {
                if (isEqualsArguments(args, list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isExcluded() {
        if (user.getMind().getExcludedDomains().containsKey(this)) {
            for (List<Argument> list : user.getMind().getExcludedDomains().get(this)) {
                if (isEqualsArguments(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setExcluded(List<Argument> args) {
        if (!user.getMind().getExcludedDomains().containsKey(this)) {
            user.getMind().getExcludedDomains().put(this, new HashSet<>());
        }
        if (!isExcluded(args)) {
            try {
                user.getMind().getExcludedDomains().get(this).add(convertArguments(args));
            } catch (ParametersIncompleteException e) {
//                e.printStackTrace();
            }
        }
    }

    public void setExcluded() {
        if (!user.getMind().getExcludedDomains().containsKey(this)) {
            user.getMind().getExcludedDomains().put(this, new HashSet<>());
        }
        if (!isExcluded()) {
            try {
                user.getMind().getExcludedDomains().get(this).add(convertArguments());
            } catch (ParametersIncompleteException e) {
//                e.printStackTrace();
            }
        }
    }

    public boolean isProduced() {
        if (user.getMind().getProducedDomains().containsKey(this)) {
            for (List<Argument> list : user.getMind().getProducedDomains().get(this)) {
                if (isEqualsArguments(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setProduced(List<Argument> args) {
        if (!user.getMind().getProducedDomains().containsKey(this)) {
            user.getMind().getProducedDomains().put(this, new HashSet<>());
        }
        if (!isProduced(args)) {
            try {
                user.getMind().getProducedDomains().get(this).add(convertArguments(args));
            } catch (ParametersIncompleteException e) {
//                e.printStackTrace();
            }
        }
    }

    public boolean isProduced(List<Argument> args) {
        if (user.getMind().getProducedDomains().containsKey(this)) {
            for (List<Argument> list : user.getMind().getProducedDomains().get(this)) {
                if (isEqualsArguments(args, list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setProduced() {
        if (!user.getMind().getProducedDomains().containsKey(this)) {
            user.getMind().getProducedDomains().put(this, new HashSet<>());
        }
        if (!isProduced()) {
            try {
                user.getMind().getProducedDomains().get(this).add(convertArguments());
            } catch (ParametersIncompleteException e) {
//                e.printStackTrace();
            }
        }
    }

    public boolean isCalculated() {
        if (user.getMind().getCalculatedDomains().containsKey(this)) {
            for (List<Argument> list : user.getMind().getCalculatedDomains().get(this)) {
                if (isEqualsArguments(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setCalculated() {
        if (!user.getMind().getCalculatedDomains().containsKey(this)) {
            user.getMind().getCalculatedDomains().put(this, new HashSet<>());
        }
        if (!isCalculated()) {
            try {
                user.getMind().getCalculatedDomains().get(this).add(convertArguments());
            } catch (ParametersIncompleteException e) {
//                e.printStackTrace();
            }
        }
    }

    public boolean isStored() {
        return user.getMind().getDatabase().find(this) != null;
    }

    public boolean isStored(List<Argument> args) {
        return user.getMind().getDatabase().find(predicate, antc, args) != null;
    }

    public Domain setStored() {
        return user.getMind().getDatabase().add(this).getDomain();
    }

    public Domain createStored() {
        return user.getMind().getDatabase().add(predicate, antc, isQuery(), arguments).getDomain();
    }

    public boolean isSystem() {
        return Parser.getOp(predicate.getName()) != null;
    }

    public int execSystem() {
        if (isSystem()) {
            return user.getMind().executeSystem(this);
        }
        return -1;
    }

//    public boolean recalculate() throws RuntimeErrorException {
//        boolean occurrs = false;
//        for (Argument a : arguments) {
//            if (a.isFunction() /*&& a.getFunction().isComplete()*/) {
////                a.getFunction().clearResult();
//                if (mind.getCalculator().calculate(a.getFunction()) > 0) {
//                    occurrs = true;
//                }
//            }
//        }
//        return occurrs;
//    }

    public boolean isPairedWith(Domain d) {
        for (TVariable t : getTVariables(false)) {
            if (d.getTVariables(false).contains(t)) {
                return true;
            }
        }
        return false;
    }

    public boolean isQuery() {
        if (right.isQuery()) {
            return true;
        } else {
            for (TVariable t : getTVariables(true)) {
                if (t.isQuery()) {
                    return true;
                }
            }
            for (Argument a : arguments) {
                if (a.isVSet()
                        && user.getMind().getQueryValues().containsKey(a.getV().getTVar())
                        && user.getMind().getQueryValues().get(a.getV().getTVar()).contains(a.getV())) {
                    return true;
                }
            }
            return false;
        }
    }

//    public boolean isComplete() {
//        for (TVariable t : getTVariables(true)) {
//            if (!t.isComplete() || /*!mind.getUsed().contains(t) ||*/ t.isEmpty()) {
//                return false;
//            }
//        }
//        return true;
//    }

//    public boolean isBlocked() {
//        for (TVariable t : getTVariables(true)) {
//            if (t.isBlocked()) {
//                return true;
//            }
//        }
//        return false;
//    }

//    public int getTVarCount() {
//        int cnt = 0;
//        for (int i = 0; i < arguments.size(); ++i) {
//            if (arguments.get(i).isTVariable()) {
//                ++cnt;
//            }
//        }
//        return cnt;
//    }
//
//    public int getCVarCount() {
//        int cnt = 0;
//        for (int i = 0; i < arguments.size(); ++i) {
//            if (!arguments.get(i).isTVariable() && arguments.get(i).isCVariable()) {
//                ++cnt;
//            }
//        }
//        return cnt;
//    }

    public int getVarOrder(int pos) {
        List<Integer> list = new ArrayList<>();
        SortedMap<Integer, Integer> sort = new TreeMap<>();
        int plains = 0;
        for (int i = 0; i < arguments.size(); ++i) {
            int ix = 0;
            if (arguments.get(i).isTSet()) {
                ix = arguments.get(i).getT().getIndex();
            } else if (arguments.get(i).isCVar()) {
                ix = arguments.get(i).getValue().getIndex();
            } else {
                ++plains;
            }
            list.add(ix);
            sort.put(ix, ix);
        }
        int i = sort.firstKey() == 0 ? 0 : 1;
        for (Integer e : sort.keySet()) {
            sort.put(e, i++);
        }
        return plains != arguments.size() ? sort.get(list.get(pos)) + plains : 0;
    }

    @Override
    public boolean equals(Object d) {
        return d != null && d instanceof Domain && ((Domain) d).id == id;
    }

    public boolean isComplete() {
        boolean complete = true;
        for (TVariable t : getTVariables(true)) {
            if (t.isEmpty()) {
                complete = false;
                break;
            }
        }
        return complete;
    }

    public void apply(Domain p) {
        apply(p.getArguments());
    }

    public void apply(List<Argument> list) {
        for (int i = 0; i < predicate.getRange(); ++i) {
            if (arguments.get(i).isTSet()) {
                if (list.get(i).isEmpty()) {
                    arguments.get(i).getT().setValue(null);
                } else if (arguments.get(i).getT().find(list.get(i).getValue()) != null) {
                    arguments.get(i).getT().setValue(list.get(i).getValue());
//                                    mind.getValues().add(parent.get(i).getT(), parent);
                }
            } else if (arguments.get(i).isFSet()) {
                //TODO: Добавить обработку функций
            }
        }
    }

    public Set<Tree> getParentTrees() {
        Set<Tree> set = new HashSet<>();
        for (Tree t = user.getMind().getTrees().getRoot(); t != null; t = t.getNext()) {
            if (t.getSequence().contains(this)) {
                set.add(t);
            }
        }
        return set;
    }

//    public int getValOrder(int i) {
//    }
}

//    public void setQuery() {
//        if (!right.isQuery()) {
//            for (TVariable t : getTVariables(true)) {
//                if (!mind.getQueryValues().containsKey(t.getId())) {
//                    mind.getQueryValues().put(t.getId(), new HashSet<>());
//                }
//                mind.getQueryValues().createCVar(t.getId()).createTVar(t.getValue().getId());
//            }
//        }
//    }

