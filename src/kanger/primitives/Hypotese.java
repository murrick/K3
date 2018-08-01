/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package kanger.primitives;

import kanger.enums.Enums;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author murray
 */
public class Hypotese {

    private Predicate predicate = null;
    private List<Term> solve = new ArrayList<>();
    private boolean antc = true;
    private Set<Right> rights = new HashSet<>();
    private boolean deleted = false;

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

    @Override
    public String toString() {
        int i, j;
        int cnum[] = new int[predicate.getRange()];
        int cptr[] = new int[predicate.getRange()];

        int ccnt = 0;

        String line = (antc ? "" : String.format("%c",Enums.NOT));
        String tmp = predicate.getName() + "(";
        for (i = 0; i < predicate.getRange(); ++i) {
            if (solve.get(i) != null && solve.get(i).isCVar()) {
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

}
