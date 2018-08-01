package kanger.primitives;

import kanger.Mind;
import kanger.compiler.Operation;
import kanger.compiler.Parser;
import kanger.enums.Enums;
import kanger.stores.SolutionsStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Solution {
    private Predicate predicate = null;
    private Boolean antc = null;
    private List<Term> parameters = new ArrayList<>();

    private Right right = null;
    private List<Solution> causes = new ArrayList<>();

    public Solution(Domain d, Mind mind) {
        predicate = d.getPredicate();
        antc = d.isAntc();
        for (Argument a : d.getArguments()) {
            parameters.add(a.getValue());
        }

//        right = d.getRight();
//        for(Domain x : d.getCauses()) {
//            Solution s = mind.getSolutions().add(d);
//        }
    }

    public Predicate getPredicate() {
        return predicate;
    }

    public void setPredicate(Predicate predicate) {
        this.predicate = predicate;
    }

    public List<Term> getParameters() {
        return parameters;
    }

    public Boolean isAntc() {
        return antc;
    }

    public void setAntc(Boolean antc) {
        this.antc = antc;
    }

    public List<Solution> getCauses() {
        return causes;
    }

    public Right getRight() {
        return right;
    }

    public void setRight(Right right) {
        this.right = right;
    }

//    public String toString() {
//        String s = String.format("%c", antc ? Enums.ANT : Enums.SUC);
//        Operation op = Parser.getOp(predicate.getName());
//        if (op == null) {
//            s += predicate.getName() + "(";
//            int i = 0;
//            for (Term t : parameters) {
//                s += t.toString();
//                if (i + 1 != predicate.getRange()) {
//                    s += (char) Enums.COMMA;
//                }
//                ++i;
//            }
//            s += ")";
//        } else if (op.getRange() == 1) {
//            if (op.isPost()) {
//                s += parameters.get(0).toString() + op.getName();
//            } else {
//                s += op.getName() + parameters.get(0).toString();
//            }
//        } else {
//            try {
//                for (int i = 0; i < op.getRange(); ++i) {
//                    s += parameters.get(i);
//                    if (i + 1 < op.getRange()) {
//                        s += " " + op.getName() + " ";
//                    }
//                }
//            } catch (IndexOutOfBoundsException ex) {
//                System.out.print(ex);
//            }
//        }
//
//        return s + ";";
//    }
//

    @Override
    public String toString() {
        int i, j;
        int cnum[] = new int[predicate.getRange()];
        int cptr[] = new int[predicate.getRange()];

        int ccnt = 0;

        String line = antc == null ? "" : String.format("%c", antc ? Enums.ANT : Enums.SUC);
        Operation op = Parser.getOp(predicate.getName());
        String tmp = "";
        String post = "";
        String mid = "";
        int range = 0;
        if (op == null) {
            tmp = predicate.getName() + "(";
            post = ")";
        } else if (op.getRange() == 1) {
            if (op.isPost()) {
                post = op.getName();
            } else {
                tmp = op.getName() + " ";
            }
        } else {
            mid = " " + op.getName() + " ";
            range = op.getRange();
        }
        for (i = 0; i < predicate.getRange(); ++i) {
            if (parameters.get(i) != null && parameters.get(i).isCVar()) {
                String qnt = "";
                int id = Integer.parseInt(parameters.get(i).toString().substring(1));
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
            } else if (parameters.get(i) != null) {
                tmp += parameters.get(i).toString();
            }
            if (i + 1 < predicate.getRange()) {
                tmp += ",";
            }

            if (i + 1 < range) {
                tmp += mid;
            }

        }
        {
            tmp += post + ";";
            line += tmp;
        }
        return line;
    }

    @Override
    public boolean equals(Object o) {
        if ((o instanceof Solution)
                && ((Solution) o).getPredicate().getId() == predicate.getId()
                && ((Solution) o).isAntc() == isAntc()
                && ((Solution) o).getParameters().size() == parameters.size()) {
            for (int i = 0; i < parameters.size(); ++i) {
                if (parameters.get(i) != null
                        && ((Solution) o).getParameters().get(i) != null
                        && ((Solution) o).getParameters().get(i).getId() != parameters.get(i).getId()) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }
}
