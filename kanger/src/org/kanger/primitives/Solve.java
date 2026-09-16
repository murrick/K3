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
import org.kanger.compiler.Parser;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.Enums;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;
import org.kanger.storage.ByteBuffer;
import org.kanger.units.*;

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 20.05.15.
 * <p>
 * Описатель варианта решения предиката
 */
public class Solve {

    private static final long serialVersionUID = 196402070001L;

    protected boolean antc = true;                                  // ! или ?
    protected int range = 0;                                        // количество параметров
    protected Predicate predicate = null;                           // Ссылка на описатель предиката
    protected ArgumentsList arguments = new ArgumentsList();        // Параметры

    protected transient long predicateId = -1;

    public Solve() {
    }

    public Solve(Predicate pred, boolean antc, ArgumentsList args) {
        setPredicate(pred);
        setAntc(antc);
        getArguments().addAll(args);
    }

    public Predicate getPredicate(Mind mind) throws Exception {
        if (predicate == null) {
            predicate = mind.getPredicates().get(predicateId);
        }
        return predicate;
    }

    public void setPredicate(Predicate predicate) {
        this.predicateId = predicate.getId();
        this.predicate = predicate;
        this.range = predicate.getRange();
    }

    public ArgumentsList getArguments() {
        return arguments;
    }

    public boolean isAntc() {
        return antc;
    }

    public void setAntc(boolean antc) {
        this.antc = antc;
    }

    protected String formatParam(IMind mind, IArgument t, boolean asRight) throws Exception {
        String s = "";
        if (t.getType() == ArgumentType.FUNCTION) {
            s += ((Function) t.getObject(mind)).toString(mind, asRight);
        } else if (t.getType() == ArgumentType.TVARIABLE) {
            s += ((TVariable) t.getObject(mind)).toString(mind);
        } else if (t.getType() == ArgumentType.TVALUE) {
            s += ((TValue) t.getObject(mind)).toString(mind);
        } else if (t.getType() == ArgumentType.FVALUE) {
            s += ((FValue) t.getObject(mind)).toString(mind);
        } else if (!t.isEmpty((Mind) mind)) {
            if (asRight && t.getValue(mind).isCVariable()) {
                s += ((Term) t.getValue(mind)).getName((Mind) mind).getValue();
            } else {
                s += t.getValue(mind).toString();
            }
        } else {
            s += "_";
        }
        return s;
    }

    public String toString(Mind mind) {
        return toString(mind, arguments, false);
    }

    public String toString(Mind mind, ArgumentsList arguments, boolean asRight) {
        try {
            String s = String.format("%c", antc ? Enums.ANT : Enums.SUC);

            if (asRight) {
                char name = 'x';
                List<ITerm> cVars = new ArrayList<>();
                for (ITerm t : arguments.getCVariables(mind)) {
                    if (t.isCVariable()) {
                        ((Term) t).setName(mind.getTerms().add(name + ""));
                        if (++name == 'z' + 1) {
                            name = 'a';
                        }
                        if (!cVars.contains(t)) {
                            cVars.add(t);
                        }
                    }
                }
                for (ITerm t : cVars) {
                    s += "$" + ((Term) t).getName(mind) + " ";
                }
            }

            Parser.Op op = Parser.getOp(getPredicate(mind).getName(mind), getRange());

            if (op == null) {
                op = Parser.getOp(getPredicate(mind).getName(mind), 0);
            }

            if (op == null) {
                s += getPredicate(mind).getName(mind) + "(";
                int i = 0;
                for (IArgument t : arguments) {
                    s += formatParam(mind, t, asRight);
                    if (i + 1 != getRange()) {
                        s += (char) Enums.COMMA;
                    }
                    ++i;
                }
                s += ")";
            } else if (op.getRange() == 1) {
                if (op.isPost()) {
                    s += formatParam(mind, arguments.get(0), asRight) + op.getName();
                } else {
                    s += op.getName() + formatParam(mind, arguments.get(0), asRight);
                }
            } else {
                for (int i = 0; i < op.getRange(); ++i) {
                    s += formatParam(mind, arguments.get(i), asRight);
                    if (i + 1 < op.getRange()) {
                        if (i == 0) {
                            s += " " + op.getName() + " ";
                        } else {
                            s += (char) Enums.COMMA;
                        }
                    }
                }
            }

            return s + ";";
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return "";
        }
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (antc ? 1 : 0);
        hash = 47 * hash + (int) (predicateId ^ (predicateId >>> 32));
        for (IArgument a : arguments) {
            hash = 47 * hash + (int) (a.getId() ^ (a.getId() >>> 32));
        }
        return hash;
    }

    @Override
    public boolean equals(Object d) {
        if (d != null) {
            if (((Solve) d).getPredicateId() == predicateId && ((Solve) d).getRange() == range) {
                for (int i = 0; i < arguments.size(); ++i) {
                    if (arguments.get(i).getId() != ((Solve) d).getArguments().get(i).getId()) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public long getPredicateId() {
        return predicateId;
    }

    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = range;
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(predicateId)
                .putInt(range)
                .putByte(antc ? 1 : 0)
                .append(arguments.pack());
        return packet.createMarked();
    }

    public Solve apply(ByteBuffer packet) throws Exception {
        predicateId = packet.getLong();
        range = packet.getInt();
        antc = packet.getByte() != 0;
        try {
            packet.mark();
            arguments = new ArgumentsList().apply(packet);
        } finally {
            packet.release();
        }
        return this;
    }

    public int getHash(IMind mind) {
        int hash = 3;
        hash = 47 * hash + (antc ? 1 : 0);
        hash = 47 * hash + (int) (predicateId ^ (predicateId >>> 32));
        hash = 47 * hash + arguments.getHash((Mind) mind);
        return hash;
    }

    public Collection<Long> getTerms(Mind mind, boolean total) throws Exception {
        Set<Long> terms = new HashSet<>();
        if (total) {
            terms.add(getPredicate(mind).getNameId());
        }
        terms.addAll(arguments.getTerms(mind, total));
        return terms;
    }

    public Map<String, Object> createMap(IMind mind) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("predicate_id", predicateId);
        map.put("predicate", getPredicate((Mind) mind).getName(mind));
        map.put("range", range);
        map.put("antc", antc);
        map.put("arguments", arguments.createMap(mind));
        map.put("origin", toString((Mind) mind));
        return map;
    }

    public Solve applyMap(Map<String, Object> map) throws Exception {
        predicateId = Long.parseLong(map.get("predicate_id") + "");
        range = Integer.parseInt(map.get("range") + "");
        antc = Boolean.parseBoolean(map.get("antc") + "");
        arguments.applyMap((List<Map<String, Object>>) map.get("arguments"));
        predicate = null;
        return this;
    }
}
