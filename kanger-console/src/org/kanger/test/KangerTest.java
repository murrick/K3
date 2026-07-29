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

package org.kanger.test;

import org.kanger.Diagnostics;
import org.kanger.Mind;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.factory.DictionaryFactory;
import org.kanger.factory.PredicateFactory;
import org.kanger.factory.TValueFactory;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IStep;
import org.kanger.primitives.Argument;
import org.kanger.primitives.Hypothesis;
import org.kanger.stores.HypothesisStore;
import org.kanger.stores.SolutionsStore;
import org.kanger.stores.ValuesStore;
import org.kanger.units.Domain;
import org.kanger.units.Predicate;
import org.kanger.units.Rule;
import org.kanger.units.Term;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class KangerTest {

    IMind mind;

    public KangerTest(IMind mind) {
        this.mind = mind;
    }

    public static boolean test(IMind mind, String prefix) throws Exception {
        System.out.println("Init test system...");
        int successCount = 0;
        long startTime = System.currentTimeMillis();
        List<String> fails = new ArrayList<>();
        Map<String, Double> list = new TreeMap<>();
        String dbName = mind.getStorageName();
        try {
            if (mind.isStorageUsed()) {
                mind = mind.closeStorage();
                mind = mind.useStorage("data/auto-test");
                mind = mind = mind.clearWorkspace();
            }

            mind = mind.clearWorkspace();

            // Storage lifecycle operations may return a different root Mind.
            // Bind the test instance only after the final context is selected.
            KangerTest cls = new KangerTest(mind);

            Method setUp = cls.getClass().getDeclaredMethod("setUp");
            setUp.setAccessible(true);
            setUp.invoke(cls);

            for (Method method : cls.getClass().getDeclaredMethods()) {
                if (method.getName().startsWith(prefix)) {
                    list.put(method.getName(), 0.0);
                }
            }

            System.out.println("Done.");
            System.out.println("----------------------------------------------------");

            for (String name : list.keySet()) {
                try {
                    System.out.println("Testing: " + name);
                    long t = System.currentTimeMillis();
                    Diagnostics.resetStorageCounters(cls.mind);
                    if (Diagnostics.isEnabled(cls.mind)) {
                        System.out.println(Diagnostics.snapshot(cls.mind, "before " + name));
                    }
                    Method method = cls.getClass().getDeclaredMethod(name);
                    method.setAccessible(true);
                    try (Diagnostics.Watchdog watchdog = Diagnostics.watch(name, cls.mind)) {
                        method.invoke(cls);
                    }
                    if (Diagnostics.isEnabled(cls.mind)) {
                        System.out.println(Diagnostics.snapshot(cls.mind, "after " + name));
                    }
                    System.out.println("Timing: " + ((System.currentTimeMillis() - t) / 1000.0) + " sec");
                    System.out.println("====================================================");
                    list.put(name, ((System.currentTimeMillis() - t) / 1000.0));
                    ++successCount;
                } catch (Exception e) {
                    fails.add(name);
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                }
            }

            for (Map.Entry<String, Double> e : list.entrySet()) {
                System.out.println(e.getKey() + "\t" + e.getValue() + " sec");
            }
            if (!fails.isEmpty()) {
                System.out.println("====================================================");
                System.out.println("Fails:");
                for (String s : fails) {
                    System.out.println(s);
                }
            }
            System.out.println("====================================================");
            System.out.println(" Timing: " + ((System.currentTimeMillis() - startTime) / 1000.0));
            System.out.println("Success: " + successCount);
            System.out.println("  Fails: " + fails.size());

        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
        } finally {
            //TODO: Включть!
//            mind = mind.clear();
            if (mind.isStorageUsed()) {
//                mind.getUser().close();
                mind.removeStorage(null);
                try {
                    mind = mind.useStorage(dbName);
                } catch (RuntimeErrorException e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                }
            }
        }

        return fails.isEmpty();
    }

    private static void fail(String msg) throws RuntimeErrorException {
        throw new RuntimeErrorException("FAIL: " + msg);
    }

    public void setUp() throws Exception {
//        user = new User();
//        mind = new Mind(user);
    }

//    private static boolean exists(Mind mind, String name, Object o) {
//        for (Term t : mind.getValues().getValues(name)) {
//            if (o.equals(t.getValue())) {
//                return true;
//            }
//        }
//        return false;
//    }

    private boolean exists(String name, Object o) throws Exception {
        for (ITerm t : ((ValuesStore) mind.getValues()).getValues(name)) {
            if (o.equals(t.getValue())) {
                return true;
            }
        }
        return false;
    }

    private void showResult(IMind mind, Boolean assertResult) throws RuntimeErrorException, Exception, ClassNotFoundException {
//        for (Right r : mind.getRights()) {
//            if (!r.isDeleted() && !r.isGenerated() && !r.isQuery() && (!local || r.getMind().getId() == mind.getId())) {
//                System.out.println("Right: " + r.toString());
//            }
//        }

        System.out.println("Query: " + mind.getQueryString());
        System.out.println("Result: " + mind.getQueryResult());
        int size = mind.getSolutions().size();
//        if (local) {
//            size = 0;
//            for (IRule log : mind.getSolutions().getRoot()) {
//                if (log.getMind().getId() == mind.getId()) {
//                    ++size;
//                }
//            }
//        }

        if (size > 0) {
            System.out.println("Solutions (" + size + "):");
            int i = 0;
            for (IRule log : mind.getSolutions()) {
//                if (!local || log.getMind().getId() == mind.getId()) {
                System.out.println(String.format("\tSolution %03d: %s", log.getId(), log.toString()));
//                }
            }
        }

        size = mind.getValues().size();
//        if (local) {
//            size = 0;
//            for (ArgumentsList a : mind.getValues().getRoot()) {
//                if (a.get(0).getMind().getId() == mind.getId()) {
//                    ++size;
//                }
//            }
//        }


        if (size > 0) {
//            mind.getValues().normalize();
            System.out.println("Values (" + size + "):");
            int i = 0;
            for (Map<String, ITerm> row : mind.getValues()) {
                String s = String.format("\tRow %03d: ", ++i);
                for (Map.Entry<String, ITerm> e : row.entrySet()) {
                    if (!s.endsWith(" ")) {
                        s += " ";
                    }
                    s += e.getKey() + "=" + e.getValue();
                }
                System.out.println(s);
            }
        }
        if (assertResult == null && !mind.getHypothesis().isEmpty()) {
            mind.optimizeHypothesis();
            System.out.println("Hypothesis (" + mind.getHypothesis().size() + "):");
            int i = 0;
            for (IHypothesis s : mind.getHypothesis()) {
                System.out.printf("\t%3d:\t%s\n", ++i, ((Hypothesis) s).toString(mind));
            }
        }
        System.out.println("----------------------------------------------------");
        if (!(mind.getQueryResult() + "").equals(assertResult + "")) {
            fail("Expected: " + assertResult);
        }
    }


    private void showResult(Boolean assertResult) throws Exception {
        showResult(mind, assertResult);
    }

    public Hypothesis createHypothesis(IMind mind, boolean antc, Object predicate, Object... params) throws Exception {
        Hypothesis h = new Hypothesis();
        h.setAntc(antc);
        if (predicate instanceof Predicate) {
            h.setPredicate((Predicate) predicate);
        } else {
            h.setPredicate(((PredicateFactory) mind.getPredicates()).add(((DictionaryFactory) mind.getTerms()).add(predicate.toString()), params.length));
        }

        for (Object o : params) {
            ITerm t = ((DictionaryFactory) mind.getTerms()).add(o);
            h.getArguments().add(new Argument(t));
        }
//        if (params[0] instanceof Collection) {
//            h.addParams(mind, (Collection) params[0]);
//        } else {
//            h.addParams(mind, Arrays.asList(params));
//        }
        return h;
    }

    public Domain createRecord(IMind mind, boolean antc, Object predicate, Object... params) throws Exception {
        Domain d = new Domain((Mind) mind);
        d.setAntc(antc);
        if (predicate instanceof Predicate) {
            d.setPredicate((Predicate) predicate);
        } else {
            d.setPredicate(((PredicateFactory) mind.getPredicates()).add(((DictionaryFactory) mind.getTerms()).add(predicate.toString()), params.length));
        }
        for (Object p : params) {
            if (p instanceof Term) {
                d.add(new Argument((ITerm) p));
            } else {
                d.add(new Argument(((DictionaryFactory) mind.getTerms()).add(p)));
            }
        }
        return d;
    }

    public void set_01_01() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(nnn);");
        showResult(true);
        Domain s = createRecord(mind, true, "a", "nnn");
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_01_02() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?n(nnn);");
        showResult(false);
        Domain s = createRecord(mind, false, "n", "nnn");
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_01_03() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(xx);");
        showResult(null);
        if (!mind.getHypothesis().isEmpty()) {
            Hypothesis s = createHypothesis(mind, false, "c", "xx");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, false, "b", "xx");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, false, "d", "xx");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "n", "xx");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
//            s = createHypotese(mind, true, "a", "xx");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
//            s = createHypotese(mind, false, "a", "xx");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
            if (mind.getHypothesis().size() != 4) {
                fail("Expected 4 hypothesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 4 hypothesis");
        }
    }

    public void set_01_04() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?b(xx);");
        showResult(null);
        if (!mind.getHypothesis().isEmpty()) {
            Hypothesis s = createHypothesis(mind, false, "c", "xx");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "a", "xx");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, false, "d", "xx");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
//            s = createHypotese(mind, true, "b", "xx");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
//            s = createHypotese(mind, false, "b", "xx");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
            if (mind.getHypothesis().size() != 3) {
                fail("Expected 3 hypothesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 3 hypothesis");
        }
    }

    public void set_01_05() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?c(xx);");
        showResult(null);
        if (!mind.getHypothesis().isEmpty()) {
            Hypothesis s = createHypothesis(mind, true, "b", "xx");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "a", "xx");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, false, "d", "xx");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
//            s = createHypotese(mind, true, "c", "xx");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
//            s = createHypotese(mind, false, "c", "xx");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
            if (mind.getHypothesis().size() != 3) {
                fail("Expected 3 hypothesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 3 hypothesis");
        }
    }

    public void set_01_06() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?d(xx);");
        showResult(null);
        if (!mind.getHypothesis().isEmpty()) {
            Hypothesis s = createHypothesis(mind, true, "b", "xx");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "a", "xx");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "c", "xx");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
//            s = createHypotese(mind, true, "d", "xx");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
//            s = createHypotese(mind, false, "d", "xx");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
            if (mind.getHypothesis().size() != 3) {
                fail("Expected 3 hypothesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 3 hypothesis");
        }
    }

    public void set_01_07() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?n(xx);");
        showResult(null);
        if (!mind.getHypothesis().isEmpty()) {
            Hypothesis s = createHypothesis(mind, true, "a", "xx");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
//            s = createHypotese(mind, true, "n", "xx");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
//            s = createHypotese(mind, false, "n", "xx");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
            if (mind.getHypothesis().size() != 1) {
                fail("Expected 1 hypothesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 2 hypothesis");
        }
    }

    public void set_01_08() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?$x c(x);");
        showResult(true);
        ITerm term = ((DictionaryFactory) mind.getTerms()).add("ooo");
        if (!((ValuesStore) mind.getValues()).getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        term = ((DictionaryFactory) mind.getTerms()).add("nnn");
        if (!((ValuesStore) mind.getValues()).getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 2) {
            fail("Expected 2 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_01_09() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?$x d(x);");
        showResult(true);
        ITerm term = ((DictionaryFactory) mind.getTerms()).add("ooo");
        if (!((ValuesStore) mind.getValues()).getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        term = ((DictionaryFactory) mind.getTerms()).add("nnn");
        if (!((ValuesStore) mind.getValues()).getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        term = ((DictionaryFactory) mind.getTerms()).add("v");
        if (!((ValuesStore) mind.getValues()).getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 3) {
            fail("Expected 3 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_01_0A() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(nn) -> b(nn);");
        showResult(true);
        /*
        Term term = ((DictionaryFactory) mind.getTerms()).add("nn");
        if (!((ValuesStore) mind.getValues()).getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 1) {
            fail("Expected 1 solve");
        }
        */
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_01_0B() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(nn) -> c(nn);");
        showResult(true);
        /*
        Term term = ((DictionaryFactory) mind.getTerms()).add("nn");
        if (!((ValuesStore) mind.getValues()).getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        if (!mind.getValues().getValues("y").contains(term)) {
            fail("Expected: " + term);
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 1 || ((ValuesStore) mind.getValues()).getValues("y").size() != 1) {
            fail("Expected 2 solve");
        }
        */
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_01_0C() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(nn) -> d(nn);");
        showResult(true);
        /*
        Term term = ((DictionaryFactory) mind.getTerms()).add("nn");
        if (!((ValuesStore) mind.getValues()).getValues("x").contains(term)) {
            fail("Expected x: " + term);
        }
        if (!mind.getValues().getValues("y").contains(term)) {
            fail("Expected y: " + term);
        }
        if (!mind.getValues().getValues("z").contains(term)) {
            fail("Expected z: " + term);
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 1 || ((ValuesStore) mind.getValues()).getValues("y").size() != 1 || mind.getValues().getValues("z").size() != 1) {
            fail("Expected 3 solve");
        }
        */
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_01_0D() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?$x a(x) && d(x);");
        showResult(true);
        if (!mind.getValues().isEmpty()) {
            ITerm term = ((DictionaryFactory) mind.getTerms()).add("nnn");
            if (!((ValuesStore) mind.getValues()).getValues("x").contains(term)) {
                fail("Expected x: " + term);
            }
            if (((ValuesStore) mind.getValues()).getValues("x").size() != 1) {
                fail("Expected 1 solve");
            }
        } else {
            fail("Expected 1 solve");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_01_0E() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?$x a(x) || d(x);");
        showResult(true);
        ITerm term = ((DictionaryFactory) mind.getTerms()).add("nnn");
        if (!((ValuesStore) mind.getValues()).getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        term = ((DictionaryFactory) mind.getTerms()).add("ooo");
        if (!((ValuesStore) mind.getValues()).getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        term = ((DictionaryFactory) mind.getTerms()).add("v");
        if (!((ValuesStore) mind.getValues()).getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 3) {
            fail("Expected 3 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_02_01() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x));");
        mind.query("? (a(z) && c(z)) -> d(z);");
        showResult(true);
        if (mind.getSolutions().size() != 3) {
            fail("Expected 3 solves");
        }
        Domain s = createRecord(mind, false, "a", "z");
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = s = createRecord(mind, false, "c", "z");
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = s = createRecord(mind, true, "d", "z");
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_02_02() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x));");
        mind.query("?b(z) -> d(z);");
        showResult(null);
        if (!mind.getHypothesis().isEmpty()) {
            Hypothesis s = createHypothesis(mind, true, "c", "z");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
//            s = createHypotese(mind, true, "d", "z");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }

//            s = createHypotese(mind, true, "a", "z");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
//            s = createHypotese(mind, true, "e", "z");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
//            s = createHypotese(mind, false, "f", "z");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
            if (mind.getHypothesis().size() != 1) {
                fail("Expected 1 hypothesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 1 hypothesis");
        }
    }

    public void set_02_03() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x)); !e(z);");
        mind.query("?$x f(x);");
        showResult(null);
        if (!mind.getHypothesis().isEmpty()) {
            Hypothesis s = createHypothesis(mind, true, "a", "z");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "b", "z");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
//            s = createHypotese(mind, false, "f", "z");
//            if (!mind.gethypothesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
            if (mind.getHypothesis().size() != 2) {
                fail("Expected 2 hypothesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 2 hypothesis");
        }
    }

    public void set_02_04() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!~b(z);");
        mind.query("? b(z) -> d(z);");
        showResult(null);
        if (mind.getSolutions().size() != 1) {
            fail("Expected 1 solve");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_02_05() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!d(z);");
        mind.query("? b(z) -> d(z);");
        showResult(null);
        if (mind.getSolutions().size() != 1) {
            fail("Expected 1 solve");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_02_06() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!~b(z);");
        mind.query("?$x b(x) -> d(x);");
        showResult(null);
        if (mind.getSolutions().size() != 1) {
            fail("Expected 1 solve");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }


    public void set_02_07() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!d(z);");
        mind.query("?$x b(x) -> d(x);");
        showResult(null);
        if (mind.getSolutions().size() != 1) {
            fail("Expected 1 solve");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_02_08() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x $y x = y, (y = 4 || y = 5);");
        showResult(true);
        if (!exists("x", 5.0)) {
            fail("Expected: x=5.0");
        }
        if (!exists("y", 5.0)) {
            fail("Expected: y=5.0");
        }
        if (!exists("x", 4.0)) {
            fail("Expected: x=4.0");
        }
        if (!exists("y", 4.0)) {
            fail("Expected: y=4.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 2) {
            //TODO: Потом разберусь
            fail("Expected 2 solves for x");
        }
        if (((ValuesStore) mind.getValues()).getValues("y").size() != 2) {
            //TODO: Потом разберусь
            fail("Expected 2 solves for y");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_02_09() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x $y x=y*2, (y=4 || y = 5);");
        showResult(true);
        if (!exists("x", 10.0)) {
            fail("Expected: x=10.0");
        }
        if (!exists("y", 5.0)) {
            fail("Expected: y=5.0");
        }
        if (!exists("x", 8.0)) {
            fail("Expected: x=8.0");
        }
        if (!exists("y", 4.0)) {
            fail("Expected: y=4.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 2) {
            //TODO: Потом разберусь
            fail("Expected 2 solves for x");
        }
        if (((ValuesStore) mind.getValues()).getValues("y").size() != 2) {
            //TODO: Потом разберусь
            fail("Expected 2 solves for y");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_02_0A() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x $y 5=x + y, (y = 4 || y = 5);");
        showResult(true);
        if (!exists("x", 1.0)) {
            fail("Expected: x=1.0");
        }
        if (!exists("y", 4.0)) {
            fail("Expected: y=4.0");
        }
        if (!exists("x", 0.0)) {
            fail("Expected: x=0.0");
        }
        if (!exists("y", 5.0)) {
            fail("Expected: y=5.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 2) {
            //TODO: Потом разберусь
            fail("Expected 2 solves for x");
        }
        if (((ValuesStore) mind.getValues()).getValues("y").size() != 2) {
            //TODO: Потом разберусь
            fail("Expected 2 solves for y");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_02_0B() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x $y 18=x + y, y : 1..3;");
        showResult(true);
        if (!exists("x", 16.0)) {
            fail("Expected: x=16.0");
        }
        if (!exists("y", 2.0)) {
            fail("Expected: y=2.0");
        }
        if (!exists("x", 15.0)) {
            fail("Expected: x=15.0");
        }
        if (!exists("y", 3.0)) {
            fail("Expected: y=3.0");
        }
        if (!exists("x", 17.0)) {
            fail("Expected: x=17.0");
        }
        if (!exists("y", 1.0)) {
            fail("Expected: y=1.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 3) {
            //TODO: Потом разберусь
            fail("Expected 3 solves for x");
        }
        if (((ValuesStore) mind.getValues()).getValues("y").size() != 3) {
            //TODO: Потом разберусь
            fail("Expected 3 solves for y");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_02_0C() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x $y x=18 + y, y : 1..3;");
        showResult(true);
        if (!exists("x", 20.0)) {
            fail("Expected: x=20.0");
        }
        if (!exists("y", 2.0)) {
            fail("Expected: y=2.0");
        }
        if (!exists("x", 21.0)) {
            fail("Expected: x=21.0");
        }
        if (!exists("y", 3.0)) {
            fail("Expected: y=3.0");
        }
        if (!exists("x", 19.0)) {
            fail("Expected: x=19.0");
        }
        if (!exists("y", 1.0)) {
            fail("Expected: y=1.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 3) {
            //TODO: Потом разберусь
            fail("Expected 3 solves for x");
        }
        if (((ValuesStore) mind.getValues()).getValues("y").size() != 3) {
            //TODO: Потом разберусь
            fail("Expected 3 solves for y");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_02_0D() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x x=18 + 2, x=18 + 3;");
        showResult(null);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_02_0E() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x 18=x + 4, 19=x+5;");
        showResult(true);
        if (!exists("x", 14.0)) {
            fail("Expected: x=14.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 1) {
            fail("Expected 1 solve for x");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_02_0F() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x @y yy1(x), yy2(y) -> solve(x,y);" +
                        "!@a @b @c @d xx(a,b,c,d) -> yy2((-b-sqrt(d))/(2*a));" +
                        "!@a @b @c @d xx(a,b,c,d) -> yy1((-b+sqrt(d))/(2*a));" +
                        "!@a @b @c k(a,b,c) -> xx(a,b,c,pow(b,2)-4*a*c);"
//                + "!k(1, -37, 27);"
        );
        mind.query("?$x $y k(1, -37, 27) -> solve(x,y);");
//        mind.query("?$x $y solve(x,y);");
        showResult(true);
        if (!exists("x", 36.2552809045647)) {
            fail("Expected: x=36.2552809045647");
        }
        if (!exists("y", 0.7447190954352969)) {
            fail("Expected: y=0.7447190954352969");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_03_01() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?$x @y a(x,y);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_03_02() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("? ~($x @y a(x,y));");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_03_03() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?@x $y a(y,x);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_03_04() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?~($x ~($y a(y,x)));");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_03_05() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?@x a(G, x);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_03_06() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?$x a(x, A);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_03_07() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?~$x a(x, A);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

//TODO: Неуверен что это корректно

    public void set_03_08() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x ~a(x,x); !@x @y b(x,y) -> a(x,y);");
        mind.query("?$x b(x, x);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_03_09() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x $y a(y,x); !@x @y a(x,y) -> b(x,y);");
        mind.query("?$x b(x, A);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

//    public void set_03_0A() throws Exception {
//        mind = mind.clearWorkspace();
//        mind.compile("!@x ~a(x,x); !@x $y a(y,x); !@x @y b(x,y) -> a(x,y); !@x @y a(x,y) -> b(x,y);");
//        mind.query("?@x $y b(y, x);");
//        showResult(true);
//        System.out.println("OK");
//        System.out.println("====================================================");
//    }

    public void set_04_01() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?2 > 3;");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_02() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?2 < 3;");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_03() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?2 = 3;");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_04() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?2 = 2;");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_05() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x x=5;");
        showResult(true);
        if (!exists("x", 5.0)) {
            fail("Expected: x=5.0");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_06() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?~$x x=5;");
        showResult(false);
        if (!exists("x", 5.0)) {
            fail("Expected: x=5.0");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_07() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x x=5 / 2;");
        showResult(true);
        if (!exists("x", 2.5)) {
            fail("Expected: x=2.5");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_08() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x ((x+3)*15)=965;");
        showResult(true);
        if (!exists("x", 61.33333333333333)) {
            fail("Expected: x=61.33333333333333");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_09() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x $y (12+y)*2=256 && x=5*y;");
        showResult(true);
        if (!exists("y", 116.0)) {
            fail("Expected: y=116.0");
        }
        if (!exists("x", 580.0)) {
            fail("Expected: x=580.0");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_0A() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x $y x + y = 12;");
        showResult(null);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_0B() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!num(0); !@x num(x) && x < 10 -> num(++x);");
        mind.query("?$x num(x);");
        showResult(true);
        if (!exists("x", 0.0)) {
            fail("Expected: x=0.0");
        }
        if (!exists("x", 1.0)) {
            fail("Expected: x=1.0");
        }
        if (!exists("x", 2.0)) {
            fail("Expected: x=2.0");
        }
        if (!exists("x", 3.0)) {
            fail("Expected: x=3.0");
        }
        if (!exists("x", 4.0)) {
            fail("Expected: x=4.0");
        }
        if (!exists("x", 5.0)) {
            fail("Expected: x=5.0");
        }
        if (!exists("x", 6.0)) {
            fail("Expected: x=6.0");
        }
        if (!exists("x", 7.0)) {
            fail("Expected: x=7.0");
        }
        if (!exists("x", 8.0)) {
            fail("Expected: x=8.0");
        }
        if (!exists("x", 9.0)) {
            fail("Expected: x=9.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 10) {
            //TODO: Потом разберусь
//            fail("Expected 10 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_0C() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!num(0); !@x num(x) && x < 10 -> num(++x);");
        mind.query("?$x num(x) && x > 5;");
        showResult(true);
        if (!exists("x", 6.0)) {
            fail("Expected: x=6.0");
        }
        if (!exists("x", 7.0)) {
            fail("Expected: x=7.0");
        }
        if (!exists("x", 8.0)) {
            fail("Expected: x=8.0");
        }
        if (!exists("x", 9.0)) {
            fail("Expected: x=9.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 4) {
            //TODO: Потом разберусь
//            fail("Expected 4 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_0D() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!num(0); !@x num(x) && x < 10 -> num(++x);");
        mind.query("?$x num(x) && x <= 3;");
        showResult(true);
        if (!exists("x", 0.0)) {
            fail("Expected: x=0.0");
        }
        if (!exists("x", 1.0)) {
            fail("Expected: x=1.0");
        }
        if (!exists("x", 2.0)) {
            fail("Expected: x=2.0");
        }
        if (!exists("x", 3.0)) {
            fail("Expected: x=3.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 4) {
            fail("Expected 4 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    //TODO:         !num(0); !@x num(x) && x < 10 -> num(++x);    ?$x $y num(x) && num(y) && x + y = 7;
    public void set_04_0E() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!num(0); !@x num(x) && x < 10 -> num(++x);");
        mind.query("?$x $y num(x) && num(y) && x + y = 7;");
        showResult(true);
        if (!exists("x", 0.0) || !exists("y", 7.0)) {
            fail("Expected: x=0.0, y = 7.0");
        }
        if (!exists("x", 1.0) || !exists("y", 6.0)) {
            fail("Expected: x=1.0, y = 6.0");
        }
        if (!exists("x", 2.0) || !exists("y", 5.0)) {
            fail("Expected: x=2.0, y = 5.0");
        }
        if (!exists("x", 3.0) || !exists("y", 4.0)) {
            fail("Expected: x=3.0, y = 4.0");
        }
        if (!exists("x", 4.0) || !exists("y", 3.0)) {
            fail("Expected: x=4.0, y = 3.0");
        }
        if (!exists("x", 5.0) || !exists("y", 2.0)) {
            fail("Expected: x=5.0, y = 4.0");
        }
        if (!exists("x", 6.0) || !exists("y", 1.0)) {
            fail("Expected: x=6.0, y = 1.0");
        }
        if (!exists("x", 7.0) || !exists("y", 0.0)) {
            fail("Expected: x=7.0, y = 0.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 8) {
            fail("Expected x 8 solves");
        }
        if (((ValuesStore) mind.getValues()).getValues("y").size() != 8) {
            fail("Expected y 8 solves");
        }

        System.out.println("OK");
        System.out.println("====================================================");
//        if (mind.getLog().size() > 0) {
//            for (LogEntry log : mind.getLog().getRoot()) {
//                System.out.println(log.getRecord());
//            }
////            System.out.println();
//        }

    }


    public void set_04_0F() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x x : 0..10 -> num(x);");
        mind.query("?$x num(x);");
        showResult(true);
        if (!exists("x", 0.0)) {
            fail("Expected: x=0.0");
        }
        if (!exists("x", 1.0)) {
            fail("Expected: x=1.0");
        }
        if (!exists("x", 2.0)) {
            fail("Expected: x=2.0");
        }
        if (!exists("x", 3.0)) {
            fail("Expected: x=3.0");
        }
        if (!exists("x", 4.0)) {
            fail("Expected: x=4.0");
        }
        if (!exists("x", 5.0)) {
            fail("Expected: x=5.0");
        }
        if (!exists("x", 6.0)) {
            fail("Expected: x=6.0");
        }
        if (!exists("x", 7.0)) {
            fail("Expected: x=7.0");
        }
        if (!exists("x", 8.0)) {
            fail("Expected: x=8.0");
        }
        if (!exists("x", 9.0)) {
            fail("Expected: x=9.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 10) {
            //TODO: Потом разберусь
//            fail("Expected 10 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_10() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x x : 0..10 -> num(x);");
        mind.query("?$x num(x) && x > 5;");
        showResult(true);
        if (!exists("x", 6.0)) {
            fail("Expected: x=6.0");
        }
        if (!exists("x", 7.0)) {
            fail("Expected: x=7.0");
        }
        if (!exists("x", 8.0)) {
            fail("Expected: x=8.0");
        }
        if (!exists("x", 9.0)) {
            fail("Expected: x=9.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 4) {
            //TODO: Потом разберусь
//            fail("Expected 4 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_11() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x x : 0..10 -> num(x);");
        mind.query("?$x num(x) && x <= 3;");
        showResult(true);
        if (!exists("x", 0.0)) {
            fail("Expected: x=0.0");
        }
        if (!exists("x", 1.0)) {
            fail("Expected: x=1.0");
        }
        if (!exists("x", 2.0)) {
            fail("Expected: x=2.0");
        }
        if (!exists("x", 3.0)) {
            fail("Expected: x=3.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 4) {
            fail("Expected 4 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_04_12() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x x : 0..10 -> num(x);");
        mind.query("?$x $y num(x) && num(y) && x + y = 7;");
        showResult(true);
        if (!exists("x", 0.0) || !exists("y", 7.0)) {
            fail("Expected: x=0.0, y = 7.0");
        }
        if (!exists("x", 1.0) || !exists("y", 6.0)) {
            fail("Expected: x=1.0, y = 6.0");
        }
        if (!exists("x", 2.0) || !exists("y", 5.0)) {
            fail("Expected: x=2.0, y = 5.0");
        }
        if (!exists("x", 3.0) || !exists("y", 4.0)) {
            fail("Expected: x=3.0, y = 4.0");
        }
        if (!exists("x", 4.0) || !exists("y", 3.0)) {
            fail("Expected: x=4.0, y = 3.0");
        }
        if (!exists("x", 5.0) || !exists("y", 2.0)) {
            fail("Expected: x=5.0, y = 4.0");
        }
        if (!exists("x", 6.0) || !exists("y", 1.0)) {
            fail("Expected: x=6.0, y = 1.0");
        }
        if (!exists("x", 7.0) || !exists("y", 0.0)) {
            fail("Expected: x=7.0, y = 0.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 8) {
            fail("Expected x 8 solves");
        }
        if (((ValuesStore) mind.getValues()).getValues("y").size() != 8) {
            fail("Expected y 8 solves");
        }

        System.out.println("OK");
        System.out.println("====================================================");

//        if (mind.getLog().size() > 0) {
//            for (LogEntry log : mind.getLog().getRoot()) {
//                System.out.println(log.getRecord());
//            }
////            System.out.println();
//        }

    }


    public void set_04_13() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x x : 0..10 -> num(x);");
        mind.query("?$x $y num(x) && num(y) && x * y = 12;");
        showResult(true);
        if (!exists("x", 2.0) || !exists("y", 6.0)) {
            fail("Expected: x=2.0, y = 6.0");
        }
        if (!exists("x", 3.0) || !exists("y", 4.0)) {
            fail("Expected: x=2.0, y = 6.0");
        }
        if (!exists("x", 4.0) || !exists("y", 3.0)) {
            fail("Expected: x=2.0, y = 6.0");
        }
        if (!exists("x", 6.0) || !exists("y", 2.0)) {
            fail("Expected: x=2.0, y = 6.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 4) {
            fail("Expected x 4 solves");
        }
        if (((ValuesStore) mind.getValues()).getValues("y").size() != 4) {
            fail("Expected y 4 solves");
        }

        System.out.println("OK");
        System.out.println("====================================================");

//        if (mind.getLog().size() > 0) {
//            for (LogEntry log : mind.getLog().getRoot()) {
//                System.out.println(log.getRecord());
//            }
////            System.out.println();
//        }

    }

    public void set_04_14() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!num(0); !@x num(x) && x < 10 -> num(++x);");
        mind.query("?$x $y num(x) && num(y) && x * y = 12;");
        showResult(true);
        if (!exists("x", 2.0) || !exists("y", 6.0)) {
            fail("Expected: x=2.0, y = 6.0");
        }
        if (!exists("x", 3.0) || !exists("y", 4.0)) {
            fail("Expected: x=2.0, y = 6.0");
        }
        if (!exists("x", 4.0) || !exists("y", 3.0)) {
            fail("Expected: x=2.0, y = 6.0");
        }
        if (!exists("x", 6.0) || !exists("y", 2.0)) {
            fail("Expected: x=2.0, y = 6.0");
        }
        if (((ValuesStore) mind.getValues()).getValues("x").size() != 4) {
            fail("Expected x 4 solves");
        }
        if (((ValuesStore) mind.getValues()).getValues("y").size() != 4) {
            fail("Expected y 4 solves");
        }

        System.out.println("OK");
        System.out.println("====================================================");

//        if (mind.getLog().size() > 0) {
//            for (LogEntry log : mind.getLog().getRoot()) {
//                System.out.println(log.getRecord());
//            }
////            System.out.println();
//        }

    }


    public void set_05_01() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?a(nnn);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_05_02() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?b(nnn);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_05_03() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?$x a(x);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_05_04() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?$x b(x);");
        showResult(null);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_05_05() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?a(xx) && b(xx);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_05_06() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?a(xx) || b(xx);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_05_07() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?$x a(x) && b(x);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_05_08() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?$x a(x) || b(x);");
        showResult(true);
        if (!exists("x", "nnn")) {
            fail("Expected x: nnn");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_06_01() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x $y parent(y,x);" +
                "!@x ~parent(x,x);" +
                "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
                "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
                "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
                "!@x @y father(x,y) -> male(x), parent(x,y);" +
                "!@x @y mother(x,y) -> female(x), parent(x,y);" +
                "!@x @y daughter(x,y) -> female(x), child(x,y);" +
                "!@x @y son(x,y) -> male(x), child(x,y);" +
                "!father(John, Tom);" +
                "!daughter(Sarah, John);" +
                "!age(John, 37);" +
                "!age(Tom, 12);" +
                "!age(Sarah, 4);"
        );
        mind.query("?$x $y age(x, y) && y > 12;");
        showResult(true);
        Domain s = createRecord(mind, true, "age", "John", 37.0);
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        if (!exists("x", "John")) {
            fail("Expected x: B");
        }
        if (!exists("y", 37.0)) {
            fail("Expected y: 37");
        }
        if (mind.getSolutions().size() != 1) {
            fail("Expected 1 solution");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_06_02() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x $y parent(y,x);" +
                "!@x ~parent(x,x);" +
                "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
                "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
                "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
                "!@x @y father(x,y) -> male(x), parent(x,y);" +
                "!@x @y mother(x,y) -> female(x), parent(x,y);" +
                "!@x @y daughter(x,y) -> female(x), child(x,y);" +
                "!@x @y son(x,y) -> male(x), child(x,y);" +
                "!father(John, Tom);" +
                "!daughter(Sarah, John);" +
                "!age(John, 37);" +
                "!age(Tom, 12);" +
                "!age(Sarah, 4);"
        );
        mind.query("?$x $y age(x, y) && y >= 12;");
        showResult(true);
        Domain s = createRecord(mind, true, "age", "Tom", 12.0);
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = createRecord(mind, true, "age", "John", 37.0);
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        if (!exists("x", "Tom")) {
            fail("Expected x: Tom");
        }
        if (!exists("y", 12.0)) {
            fail("Expected y: 12");
        }
        if (!exists("x", "John")) {
            fail("Expected x: John");
        }
        if (!exists("y", 37.0)) {
            fail("Expected y: 37");
        }
        if (mind.getSolutions().size() != 2) {
            fail("Expected 2 solution");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_06_03() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile(
                "!@x $y parent(y,x);" +
                        "!@x ~parent(x,x);" +
                        "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
                        "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
                        "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
                        "!@x @y father(x,y) -> male(x), parent(x,y);" +
                        "!@x @y mother(x,y) -> female(x), parent(x,y);" +
                        "!@x @y daughter(x,y) -> female(x), child(x,y);" +
                        "!@x @y son(x,y) -> male(x), child(x,y);" +
                        "!father(John, Tom);" +
                        "!daughter(Sarah, John);" +
                        "!age(John, 37);" +
                        "!age(Tom, 12);" +
                        "!age(Sarah, 4);"
        );
        mind.query("?$x $y $z father(x,y) && age(x, z) && z >= 30;");
        showResult(true);
        Domain s = createRecord(mind, true, "father", "John", "Tom");
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = createRecord(mind, true, "father", "John", "Sarah");
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = createRecord(mind, true, "age", "John", 37.0);
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        if (mind.getSolutions().size() != 3) {
            fail("Expected 3 solution");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_06_04() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile(
                "!@x $y parent(y,x);" +
                        "!@x ~parent(x,x);" +
                        "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
                        "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
                        "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
                        "!@x @y father(x,y) -> male(x), parent(x,y);" +
                        "!@x @y mother(x,y) -> female(x), parent(x,y);" +
                        "!@x @y daughter(x,y) -> female(x), child(x,y);" +
                        "!@x @y son(x,y) -> male(x), child(x,y);" +
                        "!father(John, Tom);" +
                        "!daughter(Sarah, John);" +
                        "!age(John, 37);" +
                        "!age(Tom, 12);" +
                        "!age(Sarah, 4);" +
                        ""
        );
        mind.query("?$x male(x);");
        showResult(true);
        Domain s = createRecord(mind, true, "male", "John");
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        if (!exists("x", "John")) {
            fail("Expected x: John");
        }
        if (mind.getSolutions().size() != 1) {
            fail("Expected 1 solution");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_06_05() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x $y parent(y,x);" +
                "!@x ~parent(x,x);" +
                "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
                "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
                "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
                "!@x @y father(x,y) -> male(x), parent(x,y);" +
                "!@x @y mother(x,y) -> female(x), parent(x,y);" +
                "!@x @y daughter(x,y) -> female(x), child(x,y);" +
                "!@x @y son(x,y) -> male(x), child(x,y);" +
                "!father(John, Tom);" +
                "!daughter(Sarah, John);" +
                "!age(John, 37);" +
                "!age(Tom, 12);" +
                "!age(Sarah, 4);"
        );
        mind.query("?$x female(x);");
        showResult(true);
        Domain s = createRecord(mind, true, "female", "Sarah");
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        if (!exists("x", "Sarah")) {
            fail("Expected x: Sarah");
        }
        if (mind.getSolutions().size() != 1) {
            fail("Expected 1 solution");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_06_06() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x $y parent(y,x);" +
                "!@x ~parent(x,x);" +
                "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
                "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
                "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
                "!@x @y father(x,y) -> male(x), parent(x,y);" +
                "!@x @y mother(x,y) -> female(x), parent(x,y);" +
                "!@x @y daughter(x,y) -> female(x), child(x,y);" +
                "!@x @y son(x,y) -> male(x), child(x,y);" +
                "!father(John, Tom);" +
                "!daughter(Sarah, John);" +
                "!age(John, 37);" +
                "!age(Tom, 12);" +
                "!age(Sarah, 4);"
        );
        mind.query("?$x child(x, John);");
        showResult(true);
        Domain s = createRecord(mind, true, "child", "Tom", "John");
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = createRecord(mind, true, "child", "Sarah", "John");
        if (!((SolutionsStore) mind.getSolutions()).contains(s)) {
            fail("Expected: " + s.toString());
        }
        if (!exists("x", "Tom")) {
            fail("Expected x: Tom");
        }
        if (!exists("x", "Sarah")) {
            fail("Expected x: Sarah");
        }
        if (mind.getSolutions().size() != 2) {
            fail("Expected 2 solution");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_06_07() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x $y parent(y,x);" +
                "!@x ~parent(x,x);" +
                "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
                "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
                "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
                "!@x @y father(x,y) -> male(x), parent(x,y);" +
                "!@x @y mother(x,y) -> female(x), parent(x,y);" +
                "!@x @y daughter(x,y) -> female(x), child(x,y);" +
                "!@x @y son(x,y) -> male(x), child(x,y);" +
                "!father(John, Tom);" +
                "!daughter(Sarah, John);" +
                "!age(John, 37);" +
                "!age(Tom, 12);" +
                "!age(Sarah, 4);"
        );
        mind.query("?male(Tom);");
        showResult(null);
        if (!mind.getHypothesis().isEmpty()) {
            Hypothesis s = createHypothesis(mind, true, "son", "Tom", "John");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, false, "son", "Tom", "John");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "son", "Tom", "Sarah");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, false, "female", "Tom");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "female", "Tom");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, false, "daughter", "Tom", "John");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "daughter", "Tom", "John");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "daughter", "Tom", "Sarah");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "father", "Tom", "John");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "father", "Tom", "Sarah");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "mother", "Tom", "John");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "mother", "Tom", "Sarah");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            if (mind.getHypothesis().size() != 12) {
                fail("Expected 12 hypothesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 12 hypothesis");
        }
    }

    public void set_06_08() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x $y parent(y,x);" +
                "!@x ~parent(x,x);" +
                "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
                "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
                "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
                "!@x @y father(x,y) -> male(x), parent(x,y);" +
                "!@x @y mother(x,y) -> female(x), parent(x,y);" +
                "!@x @y daughter(x,y) -> female(x), child(x,y);" +
                "!@x @y son(x,y) -> male(x), child(x,y);" +
                "!father(John, Tom);" +
                "!daughter(Sarah, John);" +
                "!age(John, 37);" +
                "!age(Tom, 12);" +
                "!age(Sarah, 4);"
        );
        mind.query("?$x $y age(x,y), y : [10..37];");
        showResult(true);
        if (!exists("x", "John")) {
            fail("Expected x: John");
        }
        if (!exists("x", "Tom")) {
            fail("Expected x: Tom");
        }
        if (!exists("y", 37.0)) {
            fail("Expected x: 37");
        }
        if (!exists("y", 12.0)) {
            fail("Expected x: 12");
        }
        if (mind.getValues().size() != 2) {
            fail("Expected 2 rows");
        }
        System.out.println("OK");
        System.out.println("====================================================");

    }

    public void set_06_09() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x $y parent(y,x);" +
                "!@x ~parent(x,x);" +
                "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
                "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
                "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
                "!@x @y father(x,y) -> male(x), parent(x,y);" +
                "!@x @y mother(x,y) -> female(x), parent(x,y);" +
                "!@x @y daughter(x,y) -> female(x), child(x,y);" +
                "!@x @y son(x,y) -> male(x), child(x,y);" +
                "!father(John, Tom);" +
                "!daughter(Sarah, John);" +
                "!age(John, 37);" +
                "!age(Tom, 12);" +
                "!age(Sarah, 4);"
        );
        mind.query("?$x $y age(x,y), y : [10,37];");
        showResult(true);
        if (!exists("x", "John")) {
            fail("Expected x: John");
        }
        if (!exists("y", 37.0)) {
            fail("Expected x: 37");
        }
        if (mind.getValues().size() != 1) {
            fail("Expected 1 row");
        }
        System.out.println("OK");
        System.out.println("====================================================");

    }

    public void set_06_0A() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x $y parent(y,x);" +
                "!@x ~parent(x,x);" +
                "!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
                "!@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y));" +
                "!@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y));" +
                "!@x @y father(x,y) -> male(x), parent(x,y);" +
                "!@x @y mother(x,y) -> female(x), parent(x,y);" +
                "!@x @y daughter(x,y) -> female(x), child(x,y);" +
                "!@x @y son(x,y) -> male(x), child(x,y);" +
                "!father(John, Tom);" +
                "!daughter(Sarah, John);" +
                "!age(John, 37);" +
                "!age(Tom, 12);" +
                "!age(Sarah, 4);"
        );
        mind.query("?$x male(x) && age(x,12);");
        showResult(null);
        if (!mind.getHypothesis().isEmpty()) {
            Hypothesis s = createHypothesis(mind, true, "male", "Tom");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, false, "daughter", "Tom", "John");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, false, "female", "Tom");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "son", "Tom", "Sarah");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "son", "Tom", "John");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "father", "Tom", "Sarah");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            s = createHypothesis(mind, true, "father", "Tom", "John");
            if (!((HypothesisStore) mind.getHypothesis()).contains(s)) {
                fail("Expected: " + s.toString(mind));
            }
            if (mind.getHypothesis().size() != 7) {
                fail("Expected 7 hypothesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 7 hypothesis");
        }
    }

    public void set_06_0B() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x $y parent(y,x);" +
                "!@x @y ($z parent(z,x) && parent(z,y)) && x != y -> native(x,y);" +
                "!parent(John,Tom);");
        mind.query("?$x native(x,A);");
        showResult(null);
    }

    public void set_06_0C() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x $y parent(y,x);" +
                "!@x @y ($z parent(z,x) && parent(z,y)) && x != y -> native(x,y);" +
                "!parent(John,Tom);");
        mind.query("?$x native(Tom,A);");
        showResult(null);
    }

    public void set_06_0D() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
                "!@x @y father(x,y) -> male(x), parent(x,y);" +
                "!@x @y mother(x,y) -> female(x), parent(x,y);" +
                "!father(John, Tom);" +
                "!female(Sarah);");
        mind.query("?$x father(Sarah,x);");
        showResult(false);
    }

    public void set_06_0E() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
                "!@x @y father(x,y) -> male(x), parent(x,y);" +
                "!@x @y mother(x,y) -> female(x), parent(x,y);" +
                "!father(John, Tom);" +
                "!female(Sarah);");
        mind.query("?$x mother(John,x);");
        showResult(false);
    }

    public void set_b7_1_delete_child() throws Exception {
        Mind current = (Mind) mind.clearWorkspace();
        Term parent = (Term) current.getTerms().add("b7-parent");
        Term child = (Term) current.getTerms().add("b7-child");
        parent.setChild(child);
        child.setParent(parent);

        child.setDeleted(true, current);

        assertCVarLinksEmpty(current, "deleting child");
    }

    public void set_b7_1_delete_parent() throws Exception {
        Mind current = (Mind) mind.clearWorkspace();
        Term parent = (Term) current.getTerms().add("b7-parent");
        Term child = (Term) current.getTerms().add("b7-child");
        parent.setChild(child);
        child.setParent(parent);

        parent.setDeleted(true, current);

        assertCVarLinksEmpty(current, "deleting parent");
    }

    public void set_b7_1_damaged_pair() throws Exception {
        Mind current = (Mind) mind.clearWorkspace();
        Term parent = (Term) current.getTerms().add("b7-parent");
        Term child = (Term) current.getTerms().add("b7-child");
        current.getCvarParents().put(child, parent);

        current.unlinkCVar(parent);

        assertCVarLinksEmpty(current, "unlinking one-sided pair");
    }

    public void set_b7_1_pack_drops_child_and_top() throws Exception {
        Mind current = (Mind) mind.clearWorkspace();
        current.compile("!@x b7_rule(x);");
        Rule rule = (Rule) current.getRules().iterator().next();
        TVariable variable = rule.getTVariables().get(0);
        ITerm name = current.getTerms().add("b7-name");
        ITerm parent = current.getTerms().createCVar(rule, name, null);
        ITerm child = current.getTerms().createCVar(rule, name, parent);
        TValue value = current.getTValues().add(variable, child);
        current.getTValues().set(variable, value);

        IStep oldTop = getTValueTop(current.getTValues());
        if (oldTop == null
                || ((TValue) oldTop.getData(current)).getValue(current).getId() != child.getId()) {
            fail("Test fixture does not reproduce top -> TValue -> child");
        }

        current.pack();

        assertCVarLinksEmpty(current, "packing transient child");
        if (current.getTerms().get(child.getId()) != null) {
            fail("Transient child remains in dictionary after pack");
        }
        if (current.getTValues().get(value.getId()) != null || !current.getTValues().getCurrent().isEmpty()) {
            fail("Transient TValue remains after pack");
        }
        if (getTValueTop(current.getTValues()) != null) {
            fail("TValueFactory.top retains a removed TValue");
        }
    }

    public void set_b7_1_linker_pack_cycles() throws Exception {
        Mind current = (Mind) mind.clearWorkspace();
        current.compile("!@x (male(x) || female(x)) && ~(male(x) && female(x));" +
                "!@x @y father(x,y) -> male(x), parent(x,y);" +
                "!@x @y mother(x,y) -> female(x), parent(x,y);" +
                "!father(John, Tom);" +
                "!female(Sarah);");

        for (int i = 0; i < 5; ++i) {
            current.query("?$x mother(John,x);");
            if (current.getQueryResult() != Boolean.FALSE) {
                fail("Expected false for mother(John,x), cycle " + i);
            }
            assertCVarLinksEmpty(current, "Linker/pack cycle " + i);
        }
    }

    public void set_b7_1_keep_regular_tvalue() throws Exception {
        Mind current = (Mind) mind.clearWorkspace();
        current.compile("!@x b7_variable(x); !b7_fact(kept);");
        Rule variableRule = null;
        for (IRule candidate : current.getRules()) {
            if (!((Rule) candidate).getTVariables().isEmpty()) {
                variableRule = (Rule) candidate;
                break;
            }
        }
        if (variableRule == null) {
            fail("Test fixture has no TVariable");
        }
        TVariable variable = variableRule.getTVariables().get(0);
        ITerm kept = current.getTerms().find("kept");
        TValue value = current.getTValues().add(variable, kept);

        current.pack();

        if (current.getTValues().get(value.getId()) == null) {
            fail("TValue referenced by an active rule was removed");
        }
        if (getTValueTop(current.getTValues()) == null) {
            fail("TValueFactory.top lost a retained TValue");
        }
    }

    private static void assertCVarLinksEmpty(Mind mind, String stage) throws RuntimeErrorException {
        if (!mind.getCvarChilds().isEmpty() || !mind.getCvarParents().isEmpty()) {
            fail("Stale CVar links after " + stage);
        }
    }

    private static IStep getTValueTop(TValueFactory factory) throws Exception {
        Field field = TValueFactory.class.getDeclaredField("top");
        field.setAccessible(true);
        return (IStep) field.get(factory);
    }

    public void set_07_01() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x x : '2018-03-01'..'2018-04-19' '38 hours 40 minutes', x > '2018-03-07';");
        showResult(true);
        if (mind.getValues().size() != 27) {
            fail("Expected 27 values");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_07_02() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?'2018-03-07' : '2018-03-01'..'2018-04-19';");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_07_03() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?er : qwerty;");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_07_04() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?'(.*)er(.*)' : qwerty;");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_07_05() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x x : qwerty;");
        showResult(true);
        if (mind.getValues().size() != 6) {
            fail("Expected 6 values");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_07_06() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x x : qwerty '(.*)er(.*)';");
        showResult(true);
        if (mind.getValues().size() != 2) {
            fail("Expected 2 values");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_07_07() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x x : qwerty '(.*)er(.*)', x=qw;");
        showResult(true);
        if (mind.getValues().size() != 1) {
            fail("Expected 1 values");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_07_08() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x x : qwerty '(.*)er(.*)', x=ty;");
        showResult(true);
        if (mind.getValues().size() != 1) {
            fail("Expected 1 values");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_07_09() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x x : qwerty '(.*)er(.*)', x != ty;");
        showResult(true);
        if (mind.getValues().size() != 1) {
            fail("Expected 1 values");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_07_0A() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x $y index(qwerty) -> index(x), y : x;");
        showResult(true);
        if (mind.getValues().size() != 6) {
            fail("Expected 6 rows");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_07_0B() throws Exception {

        mind = mind.clearWorkspace();
        mind.compile("!age(Tom,12);");
        mind.query("?$x $y age(Tom,y) && x : (y-2)..20;");
        showResult(true);
        if (mind.getValues().size() != 11) {
            fail("Expected 11 rows");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_07_0C() throws Exception {

        mind = mind.clearWorkspace();
        mind.query("?$x x : 1..10  3;");
        showResult(true);
        if (mind.getValues().size() != 4) {
            fail("Expected 4 rows");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }


    public void set_08_01() throws Exception {

        mind = mind.clearWorkspace();
//        mind = new Mind(mind.getUser());

        final int COUNT = 13;

//        Screen.session(mind.getUser());

//        mind.query("!value(1, 7, 7);");

        final Mind a = new Mind(mind);
        final Mind b = new Mind(mind);
        final Mind c = new Mind(mind);

        final CountDownLatch latchEnd = new CountDownLatch(3);
        final CountDownLatch latchStart = new CountDownLatch(3);
        final Object locker = new Object();

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (locker) {
                        latchStart.countDown();
                        locker.wait();
                    }
                    System.out.println("PROCESS 1 STRT: " + a.getRules().size() + "/" + mind.getRules().size());
                    for (int i = 0; i < COUNT; ++i) {
                        Boolean res = a.query("!value(1, " + i + ", " + (1000 + i) + ");");
                    }
                    if (!mind.commit(a)) {
                        System.out.println("PROCESS 1 ROLLED BACK");
                    }
                    System.out.println("PROCESS 1 STOP: " + a.getRules().size() + "/" + mind.getRules().size());
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                } finally {
                    latchEnd.countDown();
                }
            }
        });

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (locker) {
                        latchStart.countDown();
                        locker.wait();
                    }
                    System.out.println("PROCESS 2 STRT: " + b.getRules().size() + "/" + mind.getRules().size());
                    for (int i = 0; i < COUNT; ++i) {
                        Boolean res = b.query("!value(1, " + i + ", " + (2000 + i) + ");");
                    }
                    if (!mind.commit(b)) {
                        System.out.println("PROCESS 2 ROLLED BACK");
                    }
                    System.out.println("PROCESS 2 STOP: " + b.getRules().size() + "/" + mind.getRules().size());
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                } finally {
                    latchEnd.countDown();
                }
            }
        });

        Thread t3 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (locker) {
                        latchStart.countDown();
                        locker.wait();
                    }
                    System.out.println("PROCESS 3 STRT: " + c.getRules().size() + "/" + mind.getRules().size());
                    for (int i = 0; i < COUNT; ++i) {
                        Boolean res = c.query("!value(1, " + i + ", " + (3000 + i) + ");");
                    }
                    if (!mind.commit(c)) {
                        System.out.println("PROCESS 3 ROLLED BACK");
                    }
                    System.out.println("PROCESS 3 STOP: " + c.getRules().size() + "/" + mind.getRules().size());
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                } finally {
                    latchEnd.countDown();
                }
            }
        });

        t1.start();
        t2.start();
        t3.start();

        latchStart.await();

        synchronized (locker) {
            locker.notifyAll();
        }


//        latch.countDown();
//        latch.countDown();
        latchEnd.await();

        System.out.println("PROCESSES STOP: " + mind.getRules().size());

//        mind.commit(a);
//        mind.commit(b);
//        mind.commit(c);

//        Screen.showBase(a, false, null);
//        Screen.showBase(a, false, null);
//        Screen.showBase(b, false, null);
//        Screen.showBase(c, false, null);
//        Screen.showBase(mind, false, null);

//        a.query("?$x $y value(1, x, y);");
//        showResult(a, true, true);
//        b.query("?$x $y value(1, x, y);");
//        showResult(b, true, true);
//        c.query("?$x $y value(1, x, y);");
//        showResult(c, true, true);

        mind.query("?$x $y value(1, x, y);");
        showResult(true);
        if (mind.getValues().size() != COUNT * 3) {
            fail("Expected " + (COUNT * 3) + " rows");
        }

        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_08_02() throws Exception {

        mind = mind.clearWorkspace();
        mind = new Mind(mind.getUser());

        final int COUNT = 164;

//        Screen.session(mind.getUser());

//        mind.query("!value(1, 7, 7);");


        final Mind m1 = new Mind(mind);
        final Mind m2 = new Mind(mind);
        final Mind m3 = new Mind(mind);
        final Mind m4 = new Mind(mind);

        final CountDownLatch latchEnd = new CountDownLatch(4);
        final CountDownLatch latchStart = new CountDownLatch(4);
        final Object locker = new Object();
//
//        System.out.println("PROCESS 1 STRT: " + mind.getRights().size() + "/" + mind.getRights().size());
//        for (int i = 0; i < COUNT; ++i) {
//            Boolean res = mind.query("!value(1, " + i + ", " + (1000 + i) + ");");
//        }
//        System.out.println("PROCESS 1 STOP: " + mind.getRights().size() + "/" + mind.getRights().size());

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (locker) {
                        latchStart.countDown();
                        locker.wait();
                    }
                    System.out.println("PROCESS 1 STRT: " + m1.getRules().size() + "/" + mind.getRules().size());
                    for (int i = 0; i < COUNT; ++i) {
                        Boolean res = m1.query("!value(1, ?, ?);", new Object[]{i, 1000 + i});
                    }
                    m1.query("!value(1, ?, ?);", new Object[]{2, 7000 + 2});
                    if (!mind.commit(m1)) {
                        System.out.println("PROCESS 1 ROLLED BACK");
                    }
                    System.out.println("PROCESS 1 STOP: " + m1.getRules().size() + "/" + mind.getRules().size());
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                } finally {
                    latchEnd.countDown();
                }
            }
        });

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (locker) {
                        latchStart.countDown();
                        locker.wait();
                    }
                    System.out.println("PROCESS 2 STRT: " + m2.getRules().size() + "/" + mind.getRules().size());
                    for (int i = 0; i < COUNT; ++i) {
                        Boolean res = m2.query("!value(1, " + i + ", " + (2000 + i) + ");");
                    }
                    m2.query("!~value(1, " + 2 + ", " + (1000 + 2) + ");");
                    if (!mind.commit(m2)) {
                        System.out.println("PROCESS 2 ROLLED BACK");
                    }
                    System.out.println("PROCESS 2 STOP: " + m2.getRules().size() + "/" + mind.getRules().size());
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                } finally {
                    latchEnd.countDown();
                }
            }
        });

        Thread t3 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (locker) {
                        latchStart.countDown();
                        locker.wait();
                    }
                    System.out.println("PROCESS 3 STRT: " + m3.getRules().size() + "/" + mind.getRules().size());
                    for (int i = 0; i < COUNT; ++i) {
                        Boolean res = m3.query("!value(1, " + i + ", " + (3000 + i) + ");");
                    }
                    m3.query("!value(1, " + 3 + ", " + (1000 + 3) + ");");
                    if (!mind.commit(m3)) {
                        System.out.println("PROCESS 3 ROLLED BACK");
                    }
                    System.out.println("PROCESS 3 STOP: " + m3.getRules().size() + "/" + mind.getRules().size());
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                } finally {
                    latchEnd.countDown();
                }
            }
        });

        Thread t4 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (locker) {
                        latchStart.countDown();
                        locker.wait();
                    }
                    System.out.println("PROCESS 4 STRT: " + m4.getRules().size() + "/" + mind.getRules().size());
                    for (int i = 0; i < COUNT; ++i) {
                        Boolean res = m4.query("!value(1, " + i + ", " + (4000 + i) + ");");
                    }
                    if (!mind.commit(m4)) {
                        System.out.println("PROCESS 4 ROLLED BACK");
                    }
                    System.out.println("PROCESS 4 STOP: " + m4.getRules().size() + "/" + mind.getRules().size());
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                } finally {
                    latchEnd.countDown();
                }
            }
        });

        t2.start();
        t4.start();
        t1.start();
        t3.start();

        latchStart.await();

        synchronized (locker) {
            locker.notifyAll();
        }

//        latch.countDown();
//        latch.countDown();
        latchEnd.await();

        System.out.println("PROCESSES STOP: " + mind.getRules().size());

//        mind.commit(a);
//        mind.commit(b);
//        mind.commit(c);

//        Screen.showBase(mind, false, null);
//        Screen.showBase(a, false, null);
//        Screen.showBase(b, false, null);
//        Screen.showBase(c, false, null);
//        Screen.showBase(mind, false, null);
//        a.query("?$x $y value(1, x, y);");
//        showResult(a, true, true);
//        b.query("?$x $y value(1, x, y);");
//        showResult(b, true, true);
//        c.query("?$x $y value(1, x, y);");
//        showResult(c, true, true);


        mind.query("?$x $y value(1, x, y);");

        showResult(true);
        if (mind.getSolutions().size() != COUNT * 3 + 1) {
            fail("Expected " + (COUNT * 3 + 1) + " solves");
        }

        System.out.println("OK");
        System.out.println("====================================================");
    }

    public void set_08_03() throws Exception {

        mind = mind.clearWorkspace();
        mind = new Mind(mind.getUser());

        final int COUNT = 3;

        final Mind m1 = new Mind(mind);
        final Mind m2 = new Mind(mind);
        final Mind m3 = new Mind(mind);

        final CountDownLatch latchEnd = new CountDownLatch(3);

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("PROCESS 1 START: " + m1.getRules().size() + "/" + mind.getRules().size());
                    for (int i = 0; i < COUNT; ++i) {
                        Boolean res = m1.query("!value(1, ?, ?);", new Object[]{i, 1000 + i});
                    }
                    m1.query("!value(1, 2, 3002);");
                    if (!mind.commit(m1)) {
                        System.out.println("PROCESS 1 ROLLED BACK");
                    }
                    System.out.println("PROCESS 1 STOP: " + m1.getRules().size() + "/" + mind.getRules().size());
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                } finally {
                    latchEnd.countDown();
                }
            }
        });

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("PROCESS 2 START: " + m2.getRules().size() + "/" + mind.getRules().size());
                    for (int i = 0; i < COUNT; ++i) {
                        Boolean res = m2.query("!value(1, ?, ?);", new Object[]{i, 2000 + i});
                    }
                    m2.query("!~value(1, 2, 1002);");
                    if (!mind.commit(m2)) {
                        System.out.println("PROCESS 2 ROLLED BACK");
                    }
                    System.out.println("PROCESS 2 STOP: " + m2.getRules().size() + "/" + mind.getRules().size());
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                } finally {
                    latchEnd.countDown();
                }
            }
        });

        Thread t3 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("PROCESS 3 START: " + m3.getRules().size() + "/" + mind.getRules().size());
                    for (int i = 0; i < COUNT; ++i) {
                        Boolean res = m3.query("!value(1, ?, ?);", new Object[]{i, 3000 + i});
                    }
                    if (!mind.commit(m3)) {
                        System.out.println("PROCESS 3 ROLLED BACK");
                    }
                    System.out.println("PROCESS 3 STOP: " + m3.getRules().size() + "/" + mind.getRules().size());
                } catch (Exception e) {
                    System.err.println(new Date());
                    e.printStackTrace(System.err);
                } finally {
                    latchEnd.countDown();
                }
            }
        });

        t2.start();
        t3.start();
        t1.start();

        latchEnd.await();

        System.out.println("PROCESSES STOP: " + mind.getRules().size());

        Boolean res = mind.query("?$x $y value(1, x, y);");

        showResult(true);
        if (mind.getSolutions().size() != COUNT * 2) {
            fail("Expected " + (COUNT * 2) + " solves");
        }

        System.out.println("OK");
        System.out.println("====================================================");
    }

}
