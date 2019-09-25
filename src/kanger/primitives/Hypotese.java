/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package kanger.primitives;


import kanger.User;
import kanger.enums.Enums;
import kanger.units.Predicate;
import kanger.units.Right;
import kanger.units.Term;

import java.util.*;

/**
 * @author murray
 */
public class Hypotese implements Comparable<Hypotese> {

    private Predicate predicate = null;
    private List<Term> solve = new ArrayList<>();
    private Set<Right> rights = new HashSet<>();
    private boolean antc = true;
    private boolean deleted = false;
    private boolean query = false;

    private User user = null;

    public Hypotese(User user) {
        this.user = user;
    }

    public void delete() {
        deleted = true;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Predicate getPredicate() {
        return predicate;
    }

    public void setPredicate(Predicate predicate) {
        this.predicate = predicate;
    }

    public List<Term> getSolve() {
        return solve;
    }

    public void setSolve(List<Term> solve) {
        this.solve = solve;
    }

    public Set<Right> getRights() {
        return rights;
    }

//    public void setRight(Right right) {
//        this.right = right;
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

    public void addParams(Collection params) throws Exception {
        for (Object p : params) {
            if (p instanceof Argument) {
                solve.add(((Argument) p).getValue());
            } else if (p instanceof Term) {
                solve.add((Term) p);
            } else {
                solve.add(user.getMind().getTerms().add(p));
            }
        }
    }


    @Override
    public String toString() {
        int i, j;
        int cnum[] = new int[predicate.getRange()];
        int cptr[] = new int[predicate.getRange()];

        int ccnt = 0;
//        String prefix = "";
//        if (tag != -1 && (user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0) {
//            prefix = tag + ":\t";
//        }

        String line = (antc ? "" : String.format("%c", Enums.NOT));
        String tmp = predicate.getName() + "(";
        for (i = 0; i < predicate.getRange(); ++i) {
            if (solve.get(i) != null && solve.get(i).isCVariable()) {
                String qnt = "";
                int id = Integer.parseInt(solve.get(i).toString().substring(1));
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
            } else if (solve.get(i) != null) {
                tmp += solve.get(i).toString();
            }
            if (i + 1 < predicate.getRange()) {
                tmp += ",";
            }
        }
        tmp += ");";
        line += tmp;
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

    public List<Term> getCVariables() {
        List<Term> list = new ArrayList<>();
        for (Term t : solve) {
            if (t.isCVariable()) {
                list.add(t);
            }
        }
        return list;
    }
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
    public int compareTo(Hypotese o) {
        return predicate.getName().compareTo(o.getPredicate().getName());
    }
}
