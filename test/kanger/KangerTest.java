package kanger;

import kanger.exception.ParseErrorException;
import kanger.exception.RuntimeErrorException;
import kanger.primitives.Hypotese;
import kanger.primitives.Solution;
import kanger.primitives.TMeaning;

import static org.junit.Assert.*;

public class KangerTest {

    Mind mind;

    @org.junit.Before
    public void setUp() throws Exception {
        mind = new Mind();
    }

    private void showResult(Boolean assertResult) {
        if (mind.getQueryResult() != assertResult) {
            fail(mind.getQuerySource());
        } else {
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
        }
    }

    @org.junit.Test
    public void main() throws ParseErrorException, RuntimeErrorException {

        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); "
                + "!a(mmm); "
                + "!a(nnn); "
                + "!b(ooo); "
                + "!d(v); "
                + "!@x a(x) -> ~n(x); ");


        mind.query("?a(nnn);");
        showResult(true);
        mind.query("?a(nn);");
        showResult(null);
        mind.query("?b(nn);");
        showResult(null);
        mind.query("?c(nn);");
        showResult(null);
        mind.query("?d(nn);");
        showResult(null);
        mind.query("?$x c(x);");
        showResult(true);
        mind.query("?a(nn) -> b(nn);");
        showResult(true);
        mind.query("?a(nn) -> c(nn);");
        showResult(true);
        mind.query("?a(nn) -> d(nn);");
        showResult(true);
        mind.query("?$x a(x) && d(x);");
        showResult(true);
        mind.query("?$x a(x) || d(x);");
        showResult(true);

        mind.clear();
        System.out.println("DIGITS");
        System.out.println("====================================================");
        mind.query("?$x x=17;");
        showResult(true);
        mind.query("?$x x=17-9;");
        showResult(true);
        mind.query("?$x 12=x-9;");
        showResult(true);
        mind.query("?$x $y (12+y)*2=256, x=5*y;");
        showResult(true);


        mind.clear();
        mind.query("!num(0);");
        showResult(true);
        mind.query("!@x num(x) && x < 10 -> num(++x);");
        showResult(true);

//        mind.compile("!num(0);");
//        mind.compile("!@x num(x) && x < 10 -> num(++x);");

//        Screen.showBase(mind, false, null);

        mind.query("?$x num(x);");
        showResult(true);

    }
}