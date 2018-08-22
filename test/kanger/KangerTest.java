package kanger;

import kanger.exception.ParseErrorException;
import kanger.exception.RuntimeErrorException;
import kanger.primitives.Hypotese;
import kanger.primitives.Solution;
import kanger.primitives.TMeaning;
import kanger.primitives.Term;

import static org.junit.Assert.fail;

public class KangerTest {

    Mind mind;

    @org.junit.Before
    public void setUp() throws Exception {
        mind = new Mind();
    }

    private void showResult(Boolean assertResult) {
            System.out.println("Query: " + mind.getQuerySource());
            System.out.println("Result: " + mind.getQueryResult());
            if (!mind.getSolutions().isEmpty()) {
                System.out.println("Solves:");
                for (Solution s : mind.getSolutions().getRoot()) {
                    System.out.println("\t" + s);
                }
            }
            if (!mind.getValues().isEmpty()) {
                System.out.println("Values:");
                for (TMeaning s : mind.getValues().getRoot()) {
                    System.out.println("\t" + s);
                }
            }
            if (assertResult == null && !mind.getHypotesisStore().isEmpty()) {
                System.out.println("Hypotesis:");
                for (Hypotese s : mind.getHypotesisStore().getRoot()) {
                    System.out.println("\t" + s);
                }
            }
            System.out.println("----------------------------------------------------");
        if (mind.getQueryResult() != assertResult) {
            fail("Expeced: " + assertResult);
        }
    }


    @org.junit.Test
    public void set01() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(nnn);");
        showResult(true);
        Solution s = new Solution(mind, false, "a", "nnn");
        if (!mind.getSolutions().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set02() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?n(nnn);");
        showResult(false);
        Solution s = new Solution(mind, false, "a", "nnn");
        if (!mind.getSolutions().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set03() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(xx);");
        showResult(null);
        Hypotese s = new Hypotese(mind, false, "c", "xx");
        if (!mind.getHypotesisStore().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = new Hypotese(mind, false, "b", "xx");
        if (!mind.getHypotesisStore().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = new Hypotese(mind, false, "d", "xx");
        if (!mind.getHypotesisStore().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = new Hypotese(mind, true, "n", "xx");
        if (!mind.getHypotesisStore().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        if (mind.getHypotesisStore().getRoot().size() != 4) {
            fail("Expected 4 hypotesis");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set04() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?b(xx);");
        showResult(null);
        Hypotese s = new Hypotese(mind, false, "c", "xx");
        if (!mind.getHypotesisStore().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = new Hypotese(mind, true, "a", "xx");
        if (!mind.getHypotesisStore().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = new Hypotese(mind, false, "d", "xx");
        if (!mind.getHypotesisStore().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        if (mind.getHypotesisStore().getRoot().size() != 3) {
            fail("Expected 3 hypotesis");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set05() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?c(xx);");
        showResult(null);
        Hypotese s = new Hypotese(mind, true, "b", "xx");
        if (!mind.getHypotesisStore().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = new Hypotese(mind, true, "a", "xx");
        if (!mind.getHypotesisStore().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = new Hypotese(mind, false, "d", "xx");
        if (!mind.getHypotesisStore().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        if (mind.getHypotesisStore().getRoot().size() != 3) {
            fail("Expected 3 hypotesis");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set06() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?d(xx);");
        showResult(null);
        Hypotese s = new Hypotese(mind, true, "b", "xx");
        if (!mind.getHypotesisStore().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = new Hypotese(mind, true, "a", "xx");
        if (!mind.getHypotesisStore().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        s = new Hypotese(mind, true, "c", "xx");
        if (!mind.getHypotesisStore().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        if (mind.getHypotesisStore().getRoot().size() != 3) {
            fail("Expected 3 hypotesis");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set07() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?n(xx);");
        showResult(null);
        Hypotese s = new Hypotese(mind, true, "a", "xx");
        if (!mind.getHypotesisStore().getRoot().contains(s)) {
            fail("Expected: " + s.toString());
        }
        if (mind.getHypotesisStore().getRoot().size() != 1) {
            fail("Expected 1 hypotesis");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set08() throws ParseErrorException, RuntimeErrorException {

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
    public void set09() throws ParseErrorException, RuntimeErrorException {

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
    public void set0A() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(nn) -> b(nn);");
        showResult(true);
        Term term = mind.getTerms().add("nn");
        if (!mind.getValues().getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        if (mind.getValues().getValues("x").size() != 1) {
            fail("Expected 1 solve");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set0B() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(nn) -> c(nn);");
        showResult(true);
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
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set0C() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?a(nn) -> d(nn);");
        showResult(true);
        Term term = mind.getTerms().add("nn");
        if (!mind.getValues().getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        if (!mind.getValues().getValues("y").contains(term)) {
            fail("Expected: " + term);
        }
        if (!mind.getValues().getValues("z").contains(term)) {
            fail("Expected: " + term);
        }
        if (mind.getValues().getValues("x").size() != 1 || mind.getValues().getValues("y").size() != 1 || mind.getValues().getValues("z").size() != 1) {
            fail("Expected 3 solve");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set0D() throws ParseErrorException, RuntimeErrorException {

        mind.clear();
        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                "!@x a(x) -> ~n(x); " +
                "!a(nnn); " +
                "!b(ooo); " +
                "!d(v);");
        mind.query("?$x a(x) && d(x);");
        showResult(true);
        Term term = mind.getTerms().add("nnn");
        if (!mind.getValues().getValues("x").contains(term)) {
            fail("Expected: " + term);
        }
        if (mind.getValues().getValues("x").size() != 1) {
            fail("Expected 1 solve");
        }
        System.out.println("OK");
        System.out.println("====================================================");
    }

    @org.junit.Test
    public void set0E() throws ParseErrorException, RuntimeErrorException {

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

}