package kanger.test;

import kanger.Mind;
import kanger.User;
import kanger.exception.ParseErrorException;
import kanger.exception.RuntimeErrorException;
import kanger.primitives.Hypotese;
import kanger.primitives.Record;
import kanger.primitives.TValue;
import kanger.primitives.Term;

import java.util.Set;

public class KangerTest {

    User user;
    Mind mind;

    public KangerTest(User user) {
        this.user = user;
        this.mind = user.getMind();
    }

    public void setUp() throws Exception {
        user = new User();
        mind = new Mind(user);
    }

    private void showResult(Boolean assertResult) throws RuntimeErrorException {
        System.out.println("Query: " + mind.getQuerySource());
        System.out.println("Result: " + mind.getQueryResult());
        if (mind.getSolutions().size() > 0) {
            System.out.println("Solves (" + mind.getSolutions().size() + "):");
            int i = 0;
            for (Record log : mind.getSolutions().getRoot()) {
                System.out.println(String.format("\tSolution %03d: %s", ++i, log.toString()));
            }
        }
        if (mind.getValues().size() > 0) {
//            mind.getValues().normalize();
            System.out.println("Values (" + mind.getValues().size() + "):");
            int i = 0;
            for (Set<TValue> log : mind.getValues().getRoot().values()) {
                String s = String.format("\tValue %03d: ", ++i);
                String list = "";
                for (TValue v : log) {
                    if (!list.isEmpty()) {
                        list += ", ";
                    }
                    list += v.toString();
                }
                System.out.println(s + list);
            }
        }
        if (assertResult == null && !mind.getHypotesisStore().isEmpty()) {
            System.out.println("Hypothesis (" + mind.getHypotesisStore().size() + "):");
            for (int i = 0; i < mind.getHypotesisStore().getRoot().size(); ++i) {
                System.out.printf("\t%3d:\t%s\n", i + 1, mind.getHypotesisStore().getRoot().toArray(new Hypotese[]{})[i].toString());
            }
        }
        System.out.println("----------------------------------------------------");
        if (!(mind.getQueryResult() + "").equals(assertResult + "")) {
            fail("Expected: " + assertResult);
        }
    }

    private boolean exists(String name, Object o) {
        for (Term t : mind.getValues().getValues(name)) {
            if (o.equals(t.getValue())) {
                return true;
            }
        }
        return false;
    }
    private static boolean exists(Mind mind, String name, Object o) {
        for (Term t : mind.getValues().getValues(name)) {
            if (o.equals(t.getValue())) {
                return true;
            }
        }
        return false;
    }


public void set_01_01() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(nnn);");
        showResult(true);
        Record s = new Record(user, true, "a", "nnn");
        if (!mind.getSolutions().contains(s)) {
            fail("Expected: " + s.toString());
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_01_02() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?n(nnn);");
        showResult(false);
        Record s = new Record(user, false, "n", "nnn");
        if (!mind.getSolutions().contains(s)) {
            fail("Expected: " + s.toString());
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_01_03() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(xx);");
        showResult(null);
        if (!mind.getHypotesisStore().isEmpty()) {
            Hypotese s = new Hypotese(user, false, "c", "xx");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
            s = new Hypotese(user, false, "b", "xx");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
            s = new Hypotese(user, false, "d", "xx");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
            s = new Hypotese(user, true, "n", "xx");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
//            s = new Hypotese(user, true, "a", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
//            s = new Hypotese(user, false, "a", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
            if (mind.getHypotesisStore().getRoot().size() != 4) {
                fail("Expected 4 hypotesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 4 hypotesis");
        }
    }

public void set_01_04() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?b(xx);");
        showResult(null);
        if (!mind.getHypotesisStore().isEmpty()) {
            Hypotese s = new Hypotese(user, false, "c", "xx");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
            s = new Hypotese(user, true, "a", "xx");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
            s = new Hypotese(user, false, "d", "xx");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
//            s = new Hypotese(user, true, "b", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
//            s = new Hypotese(user, false, "b", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
            if (mind.getHypotesisStore().getRoot().size() != 3) {
                fail("Expected 3 hypotesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 3 hypotesis");
        }
    }

public void set_01_05() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?c(xx);");
        showResult(null);
        if (!mind.getHypotesisStore().isEmpty()) {
            Hypotese s = new Hypotese(user, true, "b", "xx");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
            s = new Hypotese(user, true, "a", "xx");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
            s = new Hypotese(user, false, "d", "xx");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
//            s = new Hypotese(user, true, "c", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
//            s = new Hypotese(user, false, "c", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
            if (mind.getHypotesisStore().getRoot().size() != 3) {
                fail("Expected 3 hypotesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 3 hypotesis");
        }
    }

public void set_01_06() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?d(xx);");
        showResult(null);
        if (!mind.getHypotesisStore().isEmpty()) {
            Hypotese s = new Hypotese(user, true, "b", "xx");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
            s = new Hypotese(user, true, "a", "xx");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
            s = new Hypotese(user, true, "c", "xx");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
//            s = new Hypotese(user, true, "d", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
//            s = new Hypotese(user, false, "d", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
            if (mind.getHypotesisStore().getRoot().size() != 3) {
                fail("Expected 3 hypotesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 3 hypotesis");
        }
    }

public void set_01_07() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?n(xx);");
        showResult(null);
        if (!mind.getHypotesisStore().isEmpty()) {
            Hypotese s = new Hypotese(user, true, "a", "xx");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
//            s = new Hypotese(user, true, "n", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
//            s = new Hypotese(user, false, "n", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
            if (mind.getHypotesisStore().getRoot().size() != 1) {
                fail("Expected 1 hypotesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 2 hypotesis");
        }
    }

public void set_01_08() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?$x c(x);");
        showResult(true);
        Term term = mind.getTerms().add("ooo");
        if (!mind.getValues().getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        term = mind.getTerms().add("nnn");
        if (!mind.getValues().getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        if (mind.getValues().getValues("x").size() != 2) {
            fail("Expected 2 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_01_09() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?$x d(x);");
        showResult(true);
        Term term = mind.getTerms().add("ooo");
        if (!mind.getValues().getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        term = mind.getTerms().add("nnn");
        if (!mind.getValues().getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        term = mind.getTerms().add("v");
        if (!mind.getValues().getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        if (mind.getValues().getValues("x").size() != 3) {
            fail("Expected 3 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_01_0A() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(nn) -> b(nn);");
        showResult(true);
        /*
        Term term = mind.getTerms().add("nn");
        if (!mind.getValues().getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        if (mind.getValues().getValues("x").size() != 1) {
            fail("Expected 1 solve");
        }
        */
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_01_0B() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(nn) -> c(nn);");
        showResult(true);
        /*
        Term term = mind.getTerms().add("nn");
        if (!mind.getValues().getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        if (!mind.getValues().getValues("y").contains(term)) {
            fail("Expected: " + term);
        }
        if (mind.getValues().getValues("x").size() != 1 || mind.getValues().getValues("y").size() != 1) {
            fail("Expected 2 solve");
        }
        */
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_01_0C() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(nn) -> d(nn);");
        showResult(true);
        /*
        Term term = mind.getTerms().add("nn");
        if (!mind.getValues().getValues("x").contains(term)) {
            fail("Expected x: " + term);
        }
        if (!mind.getValues().getValues("y").contains(term)) {
            fail("Expected y: " + term);
        }
        if (!mind.getValues().getValues("z").contains(term)) {
            fail("Expected z: " + term);
        }
        if (mind.getValues().getValues("x").size() != 1 || mind.getValues().getValues("y").size() != 1 || mind.getValues().getValues("z").size() != 1) {
            fail("Expected 3 solve");
        }
        */
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_01_0D() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?$x a(x) && d(x);");
        showResult(true);
        if (!mind.getValues().isEmpty()) {
            Term term = mind.getTerms().add("nnn");
            if (!mind.getValues().getValues("x").contains(term)) {
                fail("Expected x: " + term);
            }
            if (mind.getValues().getValues("x").size() != 1) {
                fail("Expected 1 solve");
            }
        } else {
            fail("Expected 1 solve");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_01_0E() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?$x a(x) || d(x);");
        showResult(true);
        Term term = mind.getTerms().add("nnn");
        if (!mind.getValues().getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        term = mind.getTerms().add("ooo");
        if (!mind.getValues().getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        term = mind.getTerms().add("v");
        if (!mind.getValues().getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        if (mind.getValues().getValues("x").size() != 3) {
            fail("Expected 3 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_02_01() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x));");
        mind.query("? (a(z) && c(z)) -> d(z);");
        showResult(true);
        Record s = new Record(user, false, "a", "z");
        if (!mind.getSolutions().contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = s = new Record(user, false, "c", "z");
        if (!mind.getSolutions().contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = s = new Record(user, true, "d", "z");
        if (!mind.getSolutions().contains(s)) {
            fail("Expected: " + s.toString());
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_02_02() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x));");
        mind.query("?b(z) -> d(z);");
        showResult(null);
        if (!mind.getHypotesisStore().isEmpty()) {
            Hypotese s = new Hypotese(user, true, "c", "z");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
//            s = new Hypotese(user, true, "a", "z");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
//            s = new Hypotese(user, true, "e", "z");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
//            s = new Hypotese(user, false, "f", "z");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
            if (mind.getHypotesisStore().size() != 1) {
                fail("Expected 1 hypotesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 1 hypotesis");
        }
    }

public void set_02_03() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x)); !e(z);");
        mind.query("?$x f(x);");
        showResult(null);
        if (!mind.getHypotesisStore().isEmpty()) {
            Hypotese s = new Hypotese(user, true, "a", "z");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
            s = new Hypotese(user, true, "b", "z");
            if (!mind.getHypotesisStore().contains(s)) {
                fail("Expected: " + s.toString());
            }
//            s = new Hypotese(user, false, "f", "z");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail("Expected: " + s.toString());
//            }
            if (mind.getHypotesisStore().size() != 2) {
                fail("Expected 2 hypotesis");
            }
            System.out.println("OK");
            System.out.println("====================================================");
        } else {
            fail("Expected 2 hypotesis");
        }
    }

public void set_03_01() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?$x @y a(x,y);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_03_02() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("? ~($x @y a(x,y));");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_03_03() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?@x $y a(y,x);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_03_04() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?~($x ~($y a(y,x)));");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_03_05() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?@x a(G, x);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_03_06() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?$x a(x, A);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_03_07() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?~$x a(x, A);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_01() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?2 > 3;");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_02() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?2 < 3;");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_03() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?2 = 3;");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_04() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?2 = 2;");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_05() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?$x x=5;");
        showResult(true);
        if (!exists("x", 5.0)) {
            fail("Expected: x=5.0");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_06() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?~$x x=5;");
        showResult(false);
        if (!exists("x", 5.0)) {
            fail("Expected: x=5.0");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_07() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?$x x=5 / 2;");
        showResult(true);
        if (!exists("x", 2.5)) {
            fail("Expected: x=2.5");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_08() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?$x ((x+3)*15)=965;");
        showResult(true);
        if (!exists("x", 61.33333333333333)) {
            fail("Expected: x=61.33333333333333");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_09() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
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

public void set_04_0A() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?$x $y x + y = 12;");
        showResult(null);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_0B() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
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
        if (mind.getValues().getValues("x").size() != 10) {
            //TODO: Потом разберусь
//            fail("Expected 10 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_0C() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
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
        if (mind.getValues().getValues("x").size() != 4) {
            //TODO: Потом разберусь
//            fail("Expected 4 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_0D() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
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
        if (mind.getValues().getValues("x").size() != 4) {
            fail("Expected 4 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    //TODO:         !num(0); !@x num(x) && x < 10 -> num(++x);    ?$x $y num(x) && num(y) && x + y = 7;
public void set_04_0E() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
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
        if (mind.getValues().getValues("x").size() != 8) {
            fail("Expected x 8 solves");
        }
        if (mind.getValues().getValues("y").size() != 8) {
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


public void set_04_0F() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x x in 0..10 -> num(x);");
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
        if (mind.getValues().getValues("x").size() != 10) {
            //TODO: Потом разберусь
//            fail("Expected 10 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_10() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x x in 0..10 -> num(x);");
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
        if (mind.getValues().getValues("x").size() != 4) {
            //TODO: Потом разберусь
//            fail("Expected 4 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_11() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x x in 0..10 -> num(x);");
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
        if (mind.getValues().getValues("x").size() != 4) {
            fail("Expected 4 solves");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_04_12() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x x in 0..10 -> num(x);");
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
        if (mind.getValues().getValues("x").size() != 8) {
            fail("Expected x 8 solves");
        }
        if (mind.getValues().getValues("y").size() != 8) {
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

public void set_04_14() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
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
        if (mind.getValues().getValues("x").size() != 4) {
            fail("Expected x 4 solves");
        }
        if (mind.getValues().getValues("y").size() != 4) {
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

public void set_04_13() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x x in 0..10 -> num(x);");
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
        if (mind.getValues().getValues("x").size() != 4) {
            fail("Expected x 4 solves");
        }
        if (mind.getValues().getValues("y").size() != 4) {
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

public void set_05_01() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?a(nnn);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_05_02() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?b(nnn);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_05_03() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?$x a(x);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_05_04() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?$x b(x);");
        showResult(null);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_05_05() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?a(xx) && b(xx);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_05_06() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?a(xx) || b(xx);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_05_07() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?$x a(x) && b(x);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_05_08() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
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

public void set_06_01() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!a(A,12); !a(B,37);");
        mind.query("?$x $y a(x, y) && y > 12;");
        showResult(true);
        Record s = new Record(user, true, "a", "B", 37.0);
        if (!mind.getSolutions().contains(s)) {
            fail("Expected: " + s.toString());
        }
        if (!exists("x", "B")) {
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

public void set_06_02() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!a(A,12); !a(B,37);");
        mind.query("?$x $y a(x, y) && y >= 12;");
        showResult(true);
        Record s = new Record(user, true, "a", "A", 12.0);
        if (!mind.getSolutions().contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = new Record(user, true, "a", "B", 37.0);
        if (!mind.getSolutions().contains(s)) {
            fail("Expected: " + s.toString());
        }
        if (!exists("x", "A")) {
            fail("Expected x: A");
        }
        if (!exists("y", 12.0)) {
            fail("Expected y: 37");
        }
        if (!exists("x", "B")) {
            fail("Expected x: B");
        }
        if (!exists("y", 37.0)) {
            fail("Expected y: 37");
        }
        if (mind.getSolutions().size() != 2) {
            fail("Expected 1 solution");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

public void set_06_03() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!a(T,12); !a(S,4); !a(J,37); !f(J,T); !f(J,S);");
        mind.query("?$x $y $z f(x,y) && a(x, z) && z >= 30;");
        showResult(true);
        Record s = new Record(user, true, "f", "J", "T");
        if (!mind.getSolutions().contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = new Record(user, true, "f", "J", "S");
        if (!mind.getSolutions().contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = new Record(user, true, "a", "J", 37.0);
        if (!mind.getSolutions().contains(s)) {
            fail("Expected: " + s.toString());
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    private static void fail(String msg) throws RuntimeErrorException {
        throw new RuntimeErrorException("FAIL: " + msg);
    }


}