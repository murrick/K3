/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.kanger.primitives;


import org.kanger.Mind;
import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.units.Predicate;

/**
 * @author murray
 */
public class Hypothesis implements Comparable<Hypothesis> {

    private Predicate predicate = null;
    private boolean antc = true;
    //    private boolean deleted = false;
    private boolean query = false;
    private ArgList arguments = new ArgList();
//    private List<Term> solve = new ArrayList<>();
//    private Set<Right> rights = new HashSet<>();

//    private transient long predicateId = -1;
//    private transient List<Long> solveIds = new ArrayList<>();
//    private transient Set<Long> rightsIds = new HashSet<>();
//    private transient Mind mind = null;

    public Hypothesis() {
    }

//    public Hypotese(IUser user) {
//        this.user = user;
//    }

//    public ByteBuffer pack() {
//        ByteBuffer packet = new ByteBuffer()
//                .putLong(predicateId)
//                .putByte(antc ? 1 : 0)
//                .putInt(solve.size());
//        for (Term t : solve) {
//            packet.putLong(t.getId());
//        }
//        packet.putInt(rights.size());
//        for (Right r : rights) {
//            packet.putLong(r.getId());
//        }
//        return packet.createMarked();
//    }
//
//    public Hypothesis apply(ByteBuffer packet) throws OutOfBufferException {
//        predicateId = packet.getLong();
//        antc = packet.getByte() != 0;
//        int cnt = packet.getInt();
//        for (int i = 0; i < cnt; ++i) {
//            solveIds.add(packet.getLong());
//        }
//        cnt = packet.getInt();
//        for (int i = 0; i < cnt; ++i) {
//            rightsIds.add(packet.getLong());
//        }
//        return this;
//    }

//    public void linkExternal(User user) throws Exception {
//        this.user = user;
//        predicate = user.getMind().getPredicates().load(predicateId);
//        for (long id : solveIds) {
//            Term t = user.getMind().getTerms().load(id);
//            solve.add(t);
//        }
//        for (long id : rightsIds) {
//            Right right = user.getMind().getRights().load(id);
//            rights.add(right);
//        }
//    }
//

//    public void delete() {
//        deleted = true;
//    }
//
//    public boolean isDeleted() {
//        return deleted;
//    }
//

    public Predicate getPredicate() throws Exception {
//        if (predicate == null) {
//            predicate = user.getMind().getPredicates().load(predicateId);
//        }
        return predicate;
    }

    public void setPredicate(Predicate predicate) {
        this.predicate = predicate;
//        this.predicateId = predicate.getId();
    }

//    public List<Term> getSolve() throws Exception {
//        if (solve.isEmpty() && !solveIds.isEmpty()) {
//            for (long id : solveIds) {
//                Term t = user.getMind().getTerms().load(id);
//                solve.add(t);
//            }
//        }
//        return solve;
//    }

    public ArgList getArguments() {
        return arguments;
    }


//    public Set<Right> getRights() throws Exception {
////        if (rights.isEmpty() && !rightsIds.isEmpty()) {
////            for (long id : rightsIds) {
////                Right right = user.getMind().getRights().load(id);
////                rights.add(right);
////            }
////        }
//        return rights;
//    }

    public boolean isAntc() {
        return antc;
    }

    public void setAntc(boolean antc) {
        this.antc = antc;
    }

    public boolean isQuery() {
        return query;
    }

    public void setQuery(boolean query) {
        this.query = query;
    }

//    public void addParams(Mind mind, Collection params) throws Exception {
//        for (Object p : params) {
//            if (p instanceof Argument) {
//                solve.add(((Argument) p).getValue(mind));
//            } else if (p instanceof Term) {
//                solve.add((Term) p);
//            } else {
//                solve.add(mind.getTerms().add(p));
//            }
//        }
//    }


    @Override
    public String toString() {
        String line = "";

        try {
            int i, j;
            int cnum[] = new int[getPredicate().getRange()];
            int cptr[] = new int[getPredicate().getRange()];

            int ccnt = 0;
            line += (antc ? "" : String.format("%c", Enums.NOT));
            String tmp = getPredicate().getName() + "(";
            for (i = 0; i < getPredicate().getRange(); ++i) {
                if (!getArguments().get(i).isEmpty(null) && getArguments().get(i).getValue(null).isCVariable()) {
                    String qnt = "";
                    int id = Integer.parseInt(getArguments().get(i).getValue(null).toString().substring(1));
                    for (j = 0; j < ccnt; ++j) {
                        if (cnum[j] == id) {
                            break;
                        }
                    }
                    if (j == ccnt) {
                        cnum[ccnt] = id;
                        id = cptr[ccnt++] = i;
                        qnt = String.format("%c%s", Enums.PQN, cVarName(id));
                        line += qnt + " ";
                    } else {
                        id = cptr[j];
                        qnt = String.format("?%s", cVarName(id));
                    }
                    tmp += qnt.substring(1);
                } else if (!getArguments().get(i).isEmpty(null)) {
                    tmp += getArguments().get(i).getValue(null).toString();
                }
                if (i + 1 < getPredicate().getRange()) {
                    tmp += ",";
                }
            }
            tmp += ");";
            line += tmp;
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return line;
    }

    private String cVarName(int id) {
        switch (id) {
            case 0:
                return "x";
            case 1:
                return "y";
            case 2:
                return "z";
            default:
                return "z" + (id + 1);
        }
    }

//    public List<Term> getCVariables() {
//        List<Term> list = new ArrayList<>();
//        for (Term t : solve) {
//            if (t.isCVariable()) {
//                list.add(t);
//            }
//        }
//        return list;
//    }
//    @Override
//    public int hashCode() {
//        StringBuffer buffer = new StringBuffer();
//        buffer.append(this.predicate.getId());
//        buffer.append(this.isAntc());
//        for (Term t : solve) {
//            buffer.append(t.getId());
//        }
//        return buffer.toString().hashCode();
//    }
//
//    @Override
//    public boolean equals(Object o) {
//        if ((o instanceof Hypotese)
//                && ((Hypotese) o).getPredicate().getId() == predicate.getId()
//                && ((Hypotese) o).isAntc() == isAntc()
//                && ((Hypotese) o).getSolves().size() == solve.size()) {
//            for (int i = 0; i < solve.size(); ++i) {
//                if (solve.get(i) != null
//                        && ((Hypotese) o).getSolves().get(i) != null
//                        && ((Hypotese) o).getSolves().get(i).getId() != solve.get(i).getId()) {
//                    return false;
//                }
//                return true;
//            }
//        }
//        return false;
//    }

    @Override
    public int compareTo(Hypothesis o) {
        try {
            return getPredicate().getName().compareTo(o.getPredicate().getName());
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return 0;
        }
    }

    public UnitType getUnitType() {
        return UnitType.HYPOTHESE;
    }

    public int getHash(Mind mind) {
        int hash = 3;
        hash = 47 * hash + (antc ? 1 : 0);
        hash = 47 * hash + (int) (predicate.getId() ^ (predicate.getId() >>> 32));
        //TODO: ---
        hash = 47 * hash + arguments.getHash(mind);
//        hash = 47 * hash + arguments.hashCode(); //.getHash(mind);
        return hash;
    }

}
