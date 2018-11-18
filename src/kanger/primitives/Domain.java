package kanger.primitives;

import kanger.Mind;
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
    private Set<Domain> parents = new HashSet<>();              // Родительские домены для БД

    private Mind mind = null;

    public Domain(Mind mind) {
        this.mind = mind;
    }

    public Domain(DataInputStream dis, Mind mind) throws IOException {
        id = dis.readLong();
        mind.getDomainLinks().put(this, dis.readLong());
        int flags = dis.readInt();
        antc = (flags & 0x0001) != 0;
//        used = (flags & 0x0002) != 0;
        long id = dis.readLong();
        predicate = mind.getPredicates().get(id);
        int count = dis.readInt();
        arguments.clear();
        while (count-- > 0) {
            Argument a = new Argument(dis, mind);
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

    public List<Domain> getCauses() {
        List<Domain> list = new ArrayList<>();
        for (TVariable t : getTVariables(true)) {
            if (!t.isEmpty()) {
                for (int i = 0; i < t.getSrcSolves().size(); ++i) {
//                    if(t.getDstSolves().get(i).getId() == getId()) {
                    Domain src = t.getSrcSolves().get(i);
                    list.add(src);
//                    }
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

    public Set<Domain> getParents() {
        return parents;
    }


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
        if ((mind.getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0) {
            suffix = isDest() || isQuery() || isClosed() || isUsed() || isExcluded() || isProduced() || isStored() || isCalculated()
                    ? " " + (isDest() ? "A" : "") +
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
        return index < arguments.size()
                && arguments.get(index).isTSet()
                && !arguments.get(index).getT().isEmpty()
                && arguments.get(index).getT().getDstSolves() != null
                && arguments.get(index).getT().getDstIndex(this) == index
                && arguments.get(index).getT().getSrcSolve(index).getId() == d.getId();

    }

    public boolean isDest() {
        for (TVariable t : getTVariables(false)) {
            if (!t.isEmpty() && t.getDstSolves() != null && /*contains(t)) {*/ t.getDstSolves().contains(this)) {
                return true;
            }
        }
        return false;
        //
        // return mind.getSources().containsKey(this) && !mind.getSources().createCVar(this).isEmpty();
    }

    public List<TVariable> getTVariables(boolean full) {
        return Tools.getTVariables(arguments, full);
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

    private boolean isEqualsArguments(List<Term> params) {
        for (int i = 0; i < predicate.getRange(); ++i) {
            if (arguments.get(i).isEmpty() || arguments.get(i).getValue().getId() != params.get(i).getId()) {
                return false;
            }
        }
        return true;
    }

    private boolean isEqualsArguments(Term[] solves, List<Term> params) {
        for (int i = 0; i < predicate.getRange(); ++i) {
            if (solves[i] == null || solves[i].getId() != params.get(i).getId()) {
                return false;
            }
        }
        return true;
    }

    private List<Term> convertArguments() throws ParametersIncompleteException {
        List<Term> list = new ArrayList<>();
        for (int i = 0; i < predicate.getRange(); ++i) {
            try {
                list.add(arguments.get(i).getValue());
            } catch (Exception x) {
                throw new ParametersIncompleteException("Incomplete args: " + predicate.toString() + " : " + i);
                //System.out.println(i);
            }
        }
        return list;
    }

    public boolean isClosed() {
        if (mind.getClosedDomains().containsKey(this)) {
            for (List<Term> list : mind.getClosedDomains().get(this)) {
                if (isEqualsArguments(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setClosed() {
        if (!mind.getClosedDomains().containsKey(this)) {
            mind.getClosedDomains().put(this, new HashSet<>());
        }
        if (!isClosed()) {
            try {
                mind.getClosedDomains().get(this).add(convertArguments());
            } catch (ParametersIncompleteException e) {
//                e.printStackTrace();
            }
        }
    }


    public boolean isUsed() {
        if (mind.getUsedDomains().containsKey(this)) {
            for (List<Term> list : mind.getUsedDomains().get(this)) {
                if (isEqualsArguments(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setUsed() {
        if (!mind.getUsedDomains().containsKey(this)) {
            mind.getUsedDomains().put(this, new HashSet<>());
        }
        if (!isUsed()) {
            try {
                mind.getUsedDomains().get(this).add(convertArguments());
            } catch (ParametersIncompleteException e) {
//                e.printStackTrace();
            }
        }
    }

    public boolean isExcluded(Term[] args) {
        if (mind.getExcludedDomains().containsKey(this)) {
            for (List<Term> list : mind.getExcludedDomains().get(this)) {
                if (isEqualsArguments(args, list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isExcluded() {
        if (mind.getExcludedDomains().containsKey(this)) {
            for (List<Term> list : mind.getExcludedDomains().get(this)) {
                if (isEqualsArguments(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setExcluded(Term[] args) {
        if (!mind.getExcludedDomains().containsKey(this)) {
            mind.getExcludedDomains().put(this, new HashSet<>());
        }
        
            List<Term> arguments = new ArrayList<>();
            for(Term t : args) {
                arguments.add(t);
            }

        if (!isExcluded(args)) {
            mind.getExcludedDomains().get(this).add(arguments);
        }
    }

    public boolean isProduced() {
        if (mind.getProducedDomains().containsKey(this)) {
            for (List<Term> list : mind.getProducedDomains().get(this)) {
                if (isEqualsArguments(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setProduced() {
        if (!mind.getProducedDomains().containsKey(this)) {
            mind.getProducedDomains().put(this, new HashSet<>());
        }
        if (!isProduced()) {
            try {
                mind.getProducedDomains().get(this).add(convertArguments());
            } catch (ParametersIncompleteException e) {
//                e.printStackTrace();
            }
        }
    }

    public boolean isCalculated() {
        if (mind.getCalculatedDomains().containsKey(this)) {
            for (List<Term> list : mind.getCalculatedDomains().get(this)) {
                if (isEqualsArguments(list)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setCalculated() {
        if (!mind.getCalculatedDomains().containsKey(this)) {
            mind.getCalculatedDomains().put(this, new HashSet<>());
        }
        if (!isCalculated()) {
            try {
                mind.getCalculatedDomains().get(this).add(convertArguments());
            } catch (ParametersIncompleteException e) {
//                e.printStackTrace();
            }
        }
    }

    public boolean isStored() {
        return mind.getDatabase().find(this) != null;
    }

    public Domain setStored() {
        return mind.getDatabase().add(this);
    }


    public boolean isSystem() {
        return Parser.getOp(predicate.getName()) != null;
    }

    public int execSystem() {
        if (isSystem()) {
            return mind.executeSystem(this);
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
            if (t.isEmpty() /*|| !t.getCurrent().isClosed()*/) {
                complete = false;
                break;
            }
        }
        return complete;
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

