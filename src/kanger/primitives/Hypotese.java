/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package kanger.primitives;


import java.util.*;
import kanger.*;
import kanger.enums.*;

/**
 *
 * @author murray
 */
public class Hypotese {

    private Predicate predicate = null;
    private List<Term> solve = new ArrayList<>();
    private Set<Right> rights = new HashSet<>();
    private boolean antc = true;
    private boolean deleted = false;
    private boolean query = false;

    public Hypotese(User user, boolean antc, Object predicate, Object... params) {
        this.antc = antc;
        if (predicate instanceof Predicate) {
            this.predicate = (Predicate) predicate;
        } else {
            this.predicate = user.getMind().getPredicates().add(predicate.toString(), params.length);
        }
        for (Object p : params) {
            if (p instanceof Term) {
                solve.add((Term) p);
            } else {
                solve.add(user.getMind().getTerms().add(p));
            }
        }
    }

    public Hypotese(boolean antc, Predicate predicate, List<Argument> arg) {
        this.predicate = predicate;
        this.antc = antc;
        for (Argument a : arg) {
            this.solve.add(a.getValue());
        }
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

    @Override
    public String toString() {
        int i, j;
        int cnum[] = new int[predicate.getRange()];
        int cptr[] = new int[predicate.getRange()];

        int ccnt = 0;

        String line = (antc ? "" : String.format("%c",Enums.NOT));
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
                    qnt = String.format("%cx%d", Enums.PQN, id + 1);
                    line += qnt + " ";
                } else {
                    id = cptr[j];
                    qnt = String.format("?x%d", id + 1);
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

    @Override
    public boolean equals(Object o) {
        if ((o instanceof Hypotese)
                && ((Hypotese) o).getPredicate().getId() == predicate.getId()
                && ((Hypotese) o).isAntc() == isAntc()
                && ((Hypotese) o).getSolve().size() == solve.size()) {
            for (int i = 0; i < solve.size(); ++i) {
                if (solve.get(i) != null
                        && ((Hypotese) o).getSolve().get(i) != null
                        && ((Hypotese) o).getSolve().get(i).getId() != solve.get(i).getId()) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }

}
