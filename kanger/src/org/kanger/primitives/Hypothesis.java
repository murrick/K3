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
 */

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.kanger.primitives;


import org.kanger.Mind;
import org.kanger.QueryTaint;
import org.kanger.QueryTaintCarrier;
import org.kanger.QueryTaintSolve;
import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IPredicate;
import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Predicate;
import org.kanger.units.Rule;

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class Hypothesis implements IHypothesis {

    private IPredicate predicate = null;
    private boolean antc = true;
    private boolean query = false;
    private ArgumentsList arguments = new ArgumentsList();

    public Hypothesis() {
    }

    public Hypothesis(IRule r, IMind mind) throws Exception {
        antc = !r.isAntc();
        predicate = r.getPredicate();
        arguments.addAll(((ArgumentsList) r.getArguments()).convertBase(mind));
        if (r instanceof Rule && mind instanceof Mind) {
            Domain source = ((Rule) r).getDomain();
            QueryTaint.recordHypothesis(source, (Mind) mind, this);
            QueryTaintCarrier.recordHypothesis(source, (Mind) mind, this);
            QueryTaintSolve.recordHypothesis(source, (Mind) mind, this);
        }
    }

    public Hypothesis(Solve s, Mind mind) throws Exception {
        antc = !s.isAntc();
        predicate = s.getPredicate(mind);
        arguments.addAll(s.getArguments().convertBase(mind));
        if (s instanceof Domain) {
            QueryTaint.recordHypothesis((Domain) s, mind, this);
            QueryTaintCarrier.recordHypothesis((Domain) s, mind, this);
            QueryTaintSolve.recordHypothesis((Domain) s, mind, this);
        }
    }

    @Override
    public IPredicate getPredicate() throws Exception {
        return predicate;
    }

    public void setPredicate(Predicate predicate) {
        this.predicate = predicate;
    }

    @Override
    public ArgumentsList getArguments() {
        return arguments;
    }

    @Override
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

    /**
     * Public hypothesis rendering is assertion-ready because WHEN hypotheses
     * are user-selectable candidate statements, not executable queries.
     */
    public String toString(IMind mind) {
        return render(mind, true);
    }

    /**
     * Returns the historical internal polarity rendering used to describe the
     * hypothesis object itself without materializing it as an assertion.
     */
    public String toInternalString(IMind mind) {
        return render(mind, false);
    }

    /**
     * Materializes this hypothesis as a statement that can be accepted into
     * the current KANGER context.
     *
     * <p>An antecedent hypothesis is already an assertion. A succedent
     * hypothesis represents the negation of that proposition; assertion form
     * therefore moves it to the antecedent and negates the predicate. Any
     * existential C-variable quantifiers must become universal under that
     * negation: {@code ?$x p(x)} becomes {@code !@x ~p(x)}.</p>
     */
    public String toAssertionString(IMind mind) {
        return render(mind, true);
    }

    private String render(IMind mind, boolean assertion) {
        String line = "";

        try {
            int i, j;
            int cnum[] = new int[getPredicate().getRange()];
            int cptr[] = new int[getPredicate().getRange()];

            int ccnt = 0;
            line += String.format("%c",
                    assertion ? Enums.ANT : (antc ? Enums.ANT : Enums.SUC));
            char cQuantifier = assertion && !antc ? Enums.AQN : Enums.PQN;
            String tmp = (assertion && !antc ? String.format("%c", Enums.NOT) : "")
                    + getPredicate().getName(mind) + "(";
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
                        qnt = String.format("%c%s", cQuantifier, cVarName(id));
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
            System.err.println(new Date());
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

    public UnitType getUnitType() {
        return UnitType.HYPOTHESE;
    }

    public int getHash(Mind mind) {
        int hash = 3;
        hash = 47 * hash + (antc ? 1 : 0);
        hash = 47 * hash + (int) (predicate.getId() ^ (predicate.getId() >>> 32));
        hash = 47 * hash + arguments.getHash(mind);
        return hash;
    }

    public boolean containsTerm(long id, IMind mind) throws Exception {
        Set<Long> terms = new HashSet<>();
        terms.add(((Predicate) getPredicate()).getNameId());
        terms.addAll(arguments.getTerms(mind, true));
        return terms.contains(id);
    }

    public boolean containsPredicate(long id) {
        return predicate.getId() == id;
    }

    public Map<String, Object> createMap(IMind mind) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("antc", antc);
        map.put("query", query);
        map.put("predicate", ((Predicate) getPredicate()).createMap(mind));
        map.put("arguments", getArguments().createMap(mind));
        map.put("origin", toString(mind));
        return map;
    }
}
