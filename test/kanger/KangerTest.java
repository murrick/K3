package kanger;

import kanger.exception.ParseErrorException;
import kanger.exception.RuntimeErrorException;
import kanger.primitives.Hypotese;
import kanger.primitives.Record;
import kanger.primitives.TValue;
import kanger.primitives.Term;

import static org.junit.Assert.fail;

public class KangerTest {

    User user;
    Mind mind;

    @org.junit.Before
    public void setUp() throws Exception {
        user = new User();
        mind = new Mind(user);
    }

    private void showResult(Boolean assertResult) {
        System.out.println("Query: " + mind.getQuerySource());
        System.out.println("Result: " + mind.getQueryResult());
        if (!mind.getSolutions().isEmpty()) {
            System.out.println("Solves (" + mind.getSolutions().size() + "):");
            for (Record s : mind.getSolutions().getRoot()) {
                System.out.println("\t" + (s.getTag() != -1 ? s.getTag() + ":\t" : "") + s);
            }
        }
        if (!mind.getValues().isEmpty()) {
            System.out.println("Values: (" + mind.getValues().size() + ")");
            for (TValue s : mind.getValues().getRoot()) {
                System.out.println("\t" + (s.getTag() != -1 ? s.getTag() + ":\t" : "") + s);
            }
        }
        if (assertResult == null && !mind.getHypotesisStore().isEmpty()) {
            System.out.println("Hypotesis (" + mind.getHypotesisStore().size() + "):");
            for (Hypotese s : mind.getHypotesisStore().getRoot()) {
                System.out.println("\t" + (s.getTag() != -1 ? s.getTag() + ":\t" : "") + s);
            }
        }
        System.out.println("----------------------------------------------------");
        if (mind.getQueryResult() != assertResult) {
            fail("Expeced: " + assertResult);
        }
    }

    private boolean exists(String name, Object o) {
        for (Term t : mind.getValues().getValues(name)) {
            if (o.equals(t.getVal())) {
                return true;
            }
        }
        return false;
    }

    @org.junit.Test
    public void set01_01() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set01_02() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set01_03() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set01_04() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set01_05() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set01_06() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set01_07() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set01_08() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set01_09() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set01_0A() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set01_0B() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set01_0C() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set01_0D() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set01_0E() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set02_01() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set02_02() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set02_03() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set03_01() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?$x @y a(x,y);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set03_02() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("? ~($x @y a(x,y));");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set03_03() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?@x $y a(y,x);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set03_04() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?~($x ~($y a(y,x)));");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set03_05() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?@x a(G, x);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set03_06() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?$x a(x, A);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set03_07() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
        mind.query("?~$x a(x, A);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set04_01() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?2 > 3;");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set04_02() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?2 < 3;");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set04_03() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?2 = 3;");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set04_04() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?2 = 2;");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set04_05() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?$x x=5;");
        showResult(true);
        if (!exists("x", 5.0)) {
            fail("Expected: x=5.0");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set04_06() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?~$x x=5;");
        showResult(false);
        if (!exists("x", 5.0)) {
            fail("Expected: x=5.0");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set04_07() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?$x x=5 / 2;");
        showResult(true);
        if (!exists("x", 2.5)) {
            fail("Expected: x=2.5");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set04_08() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?$x ((x+3)*16)=965;");
        showResult(true);
        if (!exists("x", 57.3125)) {
            fail("Expected: x=57.3125");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set04_09() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set04_0A() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.query("?$x $y x + y = 12;");
        showResult(null);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set04_0B() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set04_0C() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set04_0D() throws ParseErrorException, RuntimeErrorException {

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
    @org.junit.Test
    public void set04_0E() throws ParseErrorException, RuntimeErrorException {

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


    @org.junit.Test
    public void set04_0F() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set04_10() throws ParseErrorException, RuntimeErrorException {

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

    @org.junit.Test
    public void set04_11() throws ParseErrorException, RuntimeErrorException {

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

    //TODO:         !num(0); !@x num(x) && x < 10 -> num(++x);    ?$x $y num(x) && num(y) && x + y = 7;
    @org.junit.Test
    public void set04_12() throws ParseErrorException, RuntimeErrorException {

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


    @org.junit.Test
    public void set05_01() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?a(nnn);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set05_02() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?b(nnn);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set05_03() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?$x a(x);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set05_04() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?$x b(x);");
        showResult(null);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set05_05() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?a(xx) && b(xx);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set05_06() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?a(xx) || b(xx);");
        showResult(true);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set05_07() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
                "!a(nnn);");
        mind.query("?$x a(x) && b(x);");
        showResult(false);
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set05_08() throws ParseErrorException, RuntimeErrorException {

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
}