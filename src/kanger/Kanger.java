package kanger;

import kanger.exception.ParseErrorException;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.IRunnable;
import kanger.primitives.Hypotese;
import kanger.primitives.Record;
import kanger.primitives.TValue;
import kanger.primitives.Term;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 */
public class Kanger {
    private static Mind mindRoot = new Mind(new User());
//    private static Mind mind;

    private static List<IRunnable> set01 = new ArrayList<IRunnable>() {
        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set01_01");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                                "!@x a(x) -> ~n(x); " +
                                "!a(nnn); " +
                                "!b(ooo); " +
                                "!d(v);");
                        mind.query("?a(nnn);");
                        showResult(o, mind, true);
                        Record s = new Record(mind.getUser(), true, "a", "nnn");
                        if (mind.getSolutions().isEmpty() || !mind.getSolutions().contains(s)) {
                            fail(o + " Expected: " + s.toString());
                        }
                        System.out.println("OK");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.release(mind);
                    }
                    return mind;
                }
            });
        }

        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set01_02");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                                "!@x a(x) -> ~n(x); " +
                                "!a(nnn); " +
                                "!b(ooo); " +
                                "!d(v);");
                        mind.query("?n(nnn);");
                        showResult(o, mind, false);
                        Record s = new Record(mind.getUser(), false, "n", "nnn");
                        if (mind.getSolutions().isEmpty() || !mind.getSolutions().contains(s)) {
                            fail(o + " Expected: " + s.toString());
                        }
                        System.out.println("OK");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.release(mind);
                    }
                    return mind;
                }
            });
        }

        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set01_03");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                                "!@x a(x) -> ~n(x); " +
                                "!a(nnn); " +
                                "!b(ooo); " +
                                "!d(v);");
                        mind.query("?a(xx);");
                        showResult(o, mind, null);
                        if (!mind.getHypotesisStore().isEmpty()) {
                            Hypotese s = new Hypotese(mind.getUser(), false, "c", "xx");
                            if (!mind.getHypotesisStore().contains(s)) {
                                fail(o + " Expected: " + s.toString());
                            }
                            s = new Hypotese(mind.getUser(), false, "b", "xx");
                            if (!mind.getHypotesisStore().contains(s)) {
                                fail(o + " Expected: " + s.toString());
                            }
                            s = new Hypotese(mind.getUser(), false, "d", "xx");
                            if (!mind.getHypotesisStore().contains(s)) {
                                fail(o + " Expected: " + s.toString());
                            }
                            s = new Hypotese(mind.getUser(), true, "n", "xx");
                            if (!mind.getHypotesisStore().contains(s)) {
                                fail(o + " Expected: " + s.toString());
                            }
//            s = new Hypotese(mind.getUser(), true, "a", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail(o + " Expected: " + s.toString());
//            }
//            s = new Hypotese(mind.getUser(), false, "a", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail(o + " Expected: " + s.toString());
//            }
                            if (mind.getHypotesisStore().getRoot().size() != 4) {
                                fail(o + " Expected 4 hypotesis");
                            }
                            System.out.println("OK");
                        } else {
                            fail(o + " Expected 4 hypotesis");
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.release(mind);
                    }
                    return mind;
                }
            });
        }

        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set01_04");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                                "!@x a(x) -> ~n(x); " +
                                "!a(nnn); " +
                                "!b(ooo); " +
                                "!d(v);");
                        mind.query("?b(xx);");
                        showResult(o, mind, null);
                        if (!mind.getHypotesisStore().isEmpty()) {
                            Hypotese s = new Hypotese(mind.getUser(), false, "c", "xx");
                            if (!mind.getHypotesisStore().contains(s)) {
                                fail(o + " Expected: " + s.toString());
                            }
                            s = new Hypotese(mind.getUser(), true, "a", "xx");
                            if (!mind.getHypotesisStore().contains(s)) {
                                fail(o + " Expected: " + s.toString());
                            }
                            s = new Hypotese(mind.getUser(), false, "d", "xx");
                            if (!mind.getHypotesisStore().contains(s)) {
                                fail(o + " Expected: " + s.toString());
                            }
//            s = new Hypotese(mind.getUser(), true, "b", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail(o + " Expected: " + s.toString());
//            }
//            s = new Hypotese(mind.getUser(), false, "b", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail(o + " Expected: " + s.toString());
//            }
                            if (mind.getHypotesisStore().getRoot().size() != 3) {
                                fail(o + " Expected 3 hypotesis");
                            }
                            System.out.println("OK");
                        } else {
                            fail(o + " Expected 3 hypotesis");
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.release(mind);
                    }
                    return mind;
                }
            });
        }

        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set01_05");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                                "!@x a(x) -> ~n(x); " +
                                "!a(nnn); " +
                                "!b(ooo); " +
                                "!d(v);");
                        mind.query("?c(xx);");
                        showResult(o, mind, null);
                        if (!mind.getHypotesisStore().isEmpty()) {
                            Hypotese s = new Hypotese(mind.getUser(), true, "b", "xx");
                            if (!mind.getHypotesisStore().contains(s)) {
                                fail(o + " Expected: " + s.toString());
                            }
                            s = new Hypotese(mind.getUser(), true, "a", "xx");
                            if (!mind.getHypotesisStore().contains(s)) {
                                fail(o + " Expected: " + s.toString());
                            }
                            s = new Hypotese(mind.getUser(), false, "d", "xx");
                            if (!mind.getHypotesisStore().contains(s)) {
                                fail(o + " Expected: " + s.toString());
                            }
//            s = new Hypotese(mind.getUser(), true, "c", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail(o + " Expected: " + s.toString());
//            }
//            s = new Hypotese(mind.getUser(), false, "c", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail(o + " Expected: " + s.toString());
//            }
                            if (mind.getHypotesisStore().getRoot().size() != 3) {
                                fail(o + " Expected 3 hypotesis");
                            }
                            System.out.println("OK");
                        } else {
                            fail(o + " Expected 3 hypotesis");
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.release(mind);
                    }
                    return mind;
                }
            });
        }

        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set01_06");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                                "!@x a(x) -> ~n(x); " +
                                "!a(nnn); " +
                                "!b(ooo); " +
                                "!d(v);");
                        mind.query("?d(xx);");
                        showResult(o, mind, null);
                        if (!mind.getHypotesisStore().isEmpty()) {
                            Hypotese s = new Hypotese(mind.getUser(), true, "b", "xx");
                            if (!mind.getHypotesisStore().contains(s)) {
                                fail(o + " Expected: " + s.toString());
                            }
                            s = new Hypotese(mind.getUser(), true, "a", "xx");
                            if (!mind.getHypotesisStore().contains(s)) {
                                fail(o + " Expected: " + s.toString());
                            }
                            s = new Hypotese(mind.getUser(), true, "c", "xx");
                            if (!mind.getHypotesisStore().contains(s)) {
                                fail(o + " Expected: " + s.toString());
                            }
//            s = new Hypotese(mind.getUser(), true, "d", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail(o + " Expected: " + s.toString());
//            }
//            s = new Hypotese(mind.getUser(), false, "d", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail(o + " Expected: " + s.toString());
//            }
                            if (mind.getHypotesisStore().getRoot().size() != 3) {
                                fail(o + " Expected 3 hypotesis");
                            }
                            System.out.println("OK");
                        } else {
                            fail(o + " Expected 3 hypotesis");
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.release(mind);
                    }
                    return mind;
                }
            });
        }

        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set01_07");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                                "!@x a(x) -> ~n(x); " +
                                "!a(nnn); " +
                                "!b(ooo); " +
                                "!d(v);");
                        mind.query("?n(xx);");
                        showResult(o, mind, null);
                        if (!mind.getHypotesisStore().isEmpty()) {
                            Hypotese s = new Hypotese(mind.getUser(), true, "a", "xx");
                            if (!mind.getHypotesisStore().contains(s)) {
                                fail(o + " Expected: " + s.toString());
                            }
//            s = new Hypotese(mind.getUser(), true, "n", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail(o + " Expected: " + s.toString());
//            }
//            s = new Hypotese(mind.getUser(), false, "n", "xx");
//            if (!mind.getHypotesisStore().contains(s)) {
//                fail(o + " Expected: " + s.toString());
//            }
                            if (mind.getHypotesisStore().getRoot().size() != 1) {
                                fail(o + " Expected 1 hypotesis");
                            }
                            System.out.println("OK");
                        } else {
                            fail(o + " Expected 1 hypotesis");
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.release(mind);
                    }
                    return mind;
                }
            });
        }

        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set01_08");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                                "!@x a(x) -> ~n(x); " +
                                "!a(nnn); " +
                                "!b(ooo); " +
                                "!d(v);");
                        mind.query("?$x c(x);");
                        showResult(o, mind, true);
                        Term term = mind.getTerms().add("ooo");
                        if (!mind.getValues().getValues("x").contains(term)) {
                            fail(o + " Expected: " + term);
                        }
                        term = mind.getTerms().add("nnn");
                        if (!mind.getValues().getValues("x").contains(term)) {
                            fail(o + " Expected: " + term);
                        }
                        if (mind.getValues().getValues("x").size() != 2) {
                            fail(o + " Expected 2 solves");
                        }
                        System.out.println("OK");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.release(mind);
                    }
                    return mind;
                }
            });
        }


        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set01_09");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                                "!@x a(x) -> ~n(x); " +
                                "!a(nnn); " +
                                "!b(ooo); " +
                                "!d(v);");
                        mind.query("?$x d(x);");
                        showResult(o, mind, true);
                        Term term = mind.getTerms().add("ooo");
                        if (!mind.getValues().getValues("x").contains(term)) {
                            fail(o + " Expected: " + term);
                        }
                        term = mind.getTerms().add("nnn");
                        if (!mind.getValues().getValues("x").contains(term)) {
                            fail(o + " Expected: " + term);
                        }
                        term = mind.getTerms().add("v");
                        if (!mind.getValues().getValues("x").contains(term)) {
                            fail(o + " Expected: " + term);
                        }
                        if (mind.getValues().getValues("x").size() != 3) {
                            fail(o + " Expected 3 solves");
                        }
                        System.out.println("OK");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.release(mind);
                    }
                    return mind;
                }
            });
        }


        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set01_0A");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                                "!@x a(x) -> ~n(x); " +
                                "!a(nnn); " +
                                "!b(ooo); " +
                                "!d(v);");
                        mind.query("?a(nn) -> b(nn);");
                        showResult(o, mind, true);
        /*
         Term term = mind.getTerms().add("nn");
         if (!mind.getValues().getValues("x").contains(term)) {
         fail(o + " Expected: " + term);
         }
         if (mind.getValues().getValues("x").size() != 1) {
         fail(o + "Expected1 solve");
         }
         */
                        System.out.println("OK");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.release(mind);
                    }
                    return mind;
                }
            });
        }


        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set01_0B");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                                "!@x a(x) -> ~n(x); " +
                                "!a(nnn); " +
                                "!b(ooo); " +
                                "!d(v);");
                        mind.query("?a(nn) -> c(nn);");
                        showResult(o, mind, true);
        /*
         Term term = mind.getTerms().add("nn");
         if (!mind.getValues().getValues("x").contains(term)) {
         fail(o + " Expected: " + term);
         }
         if (!mind.getValues().getValues("y").contains(term)) {
         fail(o + " Expected: " + term);
         }
         if (mind.getValues().getValues("x").size() != 1 || mind.getValues().getValues("y").size() != 1) {
         fail(o + "Expected2 solve");
         }
         */
                        System.out.println("OK");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.release(mind);
                    }
                    return mind;
                }
            });
        }


        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set01_0C");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                                "!@x a(x) -> ~n(x); " +
                                "!a(nnn); " +
                                "!b(ooo); " +
                                "!d(v);");
                        mind.query("?a(nn) -> d(nn);");
                        showResult(o, mind, true);
        /*
         Term term = mind.getTerms().add("nn");
         if (!mind.getValues().getValues("x").contains(term)) {
         fail(o + "Expectedx: " + term);
         }
         if (!mind.getValues().getValues("y").contains(term)) {
         fail(o + "Expectedy: " + term);
         }
         if (!mind.getValues().getValues("z").contains(term)) {
         fail(o + "Expectedz: " + term);
         }
         if (mind.getValues().getValues("x").size() != 1 || mind.getValues().getValues("y").size() != 1 || mind.getValues().getValues("z").size() != 1) {
         fail(o + "Expected3 solve");
         }
         */
                        System.out.println("OK");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.release(mind);
                    }
                    return mind;
                }
            });
        }


        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set01_0D");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                                "!@x a(x) -> ~n(x); " +
                                "!a(nnn); " +
                                "!b(ooo); " +
                                "!d(v);");
                        mind.query("?$x a(x) && d(x);");
                        showResult(o, mind, true);
                        if (!mind.getValues().isEmpty()) {
                            Term term = mind.getTerms().add("nnn");
                            if (!mind.getValues().getValues("x").contains(term)) {
                                fail(o + " Expected x: " + term);
                            }
                            if (mind.getValues().getValues("x").size() != 1) {
                                fail(o + " Expected 1 solve");
                            }
                            System.out.println("OK");
                        } else {
                            fail(o + " Expected 1 solve");
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.release(mind);
                    }
                    return mind;
                }
            });
        }


        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set01_0E");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
                                "!@x a(x) -> ~n(x); " +
                                "!a(nnn); " +
                                "!b(ooo); " +
                                "!d(v);");
                        mind.query("?$x a(x) || d(x);");
                        showResult(o, mind, true);
                        Term term = mind.getTerms().add("nnn");
                        if (!mind.getValues().getValues("x").contains(term)) {
                            fail(o + " Expected: " + term);
                        }
                        term = mind.getTerms().add("ooo");
                        if (!mind.getValues().getValues("x").contains(term)) {
                            fail(o + " Expected: " + term);
                        }
                        term = mind.getTerms().add("v");
                        if (!mind.getValues().getValues("x").contains(term)) {
                            fail(o + " Expected: " + term);
                        }
                        if (mind.getValues().getValues("x").size() != 3) {
                            fail(o + " Expected 3 solves");
                        }
                        System.out.println("OK");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.release(mind);
                    }
                    return mind;
                }
            });
        }
    };


    private static List<IRunnable> set02 = new ArrayList<IRunnable>() {
        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set02_01");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x));");
                        mind.query("? (a(z) && c(z)) -> d(z);");
                        showResult(o, mind, true);
                        Record s = new Record(mind.getUser(), false, "a", "z");
                        if (!mind.getSolutions().contains(s)) {
                            fail("Expected: " + s.toString());
                        }
                        s = new Record(mind.getUser(), false, "c", "z");
                        if (!mind.getSolutions().contains(s)) {
                            fail("Expected: " + s.toString());
                        }
                        s = new Record(mind.getUser(), true, "d", "z");
                        if (!mind.getSolutions().contains(s)) {
                            fail("Expected: " + s.toString());
                        }
                        System.out.println("OK");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.commit(mind);
                    }
                    return mind;

                }
            });
        }

        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set02_02");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x));");
                        mind.query("?b(z) -> d(z);");
                        showResult(o, mind, null);
                        Hypotese s = new Hypotese(mind.getUser(), true, "c", "z");
                        if (!mind.getHypotesisStore().contains(s)) {
                            fail("Expected: " + s.toString());
                        }
//                        s = new Hypotese(mind.getUser(), true, "a", "z");
//                        if (!mind.getHypotesisStore().contains(s)) {
//                            fail("Expected: " + s.toString());
//                        }
//                        s = new Hypotese(mind.getUser(), true, "e", "z");
//                        if (!mind.getHypotesisStore().contains(s)) {
//                            fail("Expected: " + s.toString());
//                        }
//                        s = new Hypotese(mind.getUser(), false, "f", "z");
//                        if (!mind.getHypotesisStore().contains(s)) {
//                            fail("Expected: " + s.toString());
//                        }
                        if (mind.getHypotesisStore().size() != 1) {
                            fail("Expected 1 hypotesis");
                        }
                        System.out.println("OK");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.commit(mind);
                    }
                    return mind;

                }
            });
        }

        {
            add(new IRunnable() {
                @Override
                public Object run(Object o) {
                    System.out.println("set02_03");
                    Mind mind = new Mind(mindRoot);
                    try {
                        mind.compile("!@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x)); !e(z);");
                        mind.query("?$x f(x);");
                        showResult(o, mind, null);
                        Hypotese s = new Hypotese(mind.getUser(), true, "a", "z");
                        if (!mind.getHypotesisStore().contains(s)) {
                            fail("Expected: " + s.toString());
                        }
                        s = new Hypotese(mind.getUser(), true, "b", "z");
                        if (!mind.getHypotesisStore().contains(s)) {
                            fail("Expected: " + s.toString());
                        }
//                        s = new Hypotese(mind.getUser(), true, "f", "z");
//                        if (!mind.getHypotesisStore().contains(s)) {
//                            fail("Expected: " + s.toString());
//                        }
                        if (mind.getHypotesisStore().size() != 2) {
                            fail("Expected 2 hypotesis");
                        }
                        System.out.println("OK");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        System.out.println("====================================================");
                        mindRoot.commit(mind);
                    }
                    return mind;

                }
            });
        }

    };

    public static void main(String[] args) throws ParseErrorException, RuntimeErrorException {

        Mind mind = new Mind(mindRoot);

//        new LibraryStrings(mind);
//        new LibraryMath(mind);

//        mind.setText(new StringBuffer("!num(0);\r" +
//                        "!@x num(x), x < 20 -> num(++x);\r"));
//        mind.setText(new StringBuffer(
//                "!@x $y father(y,x);\n" +
//                        "!@x ~father(x,x);\n" +
//                        "!@x (male(x) || female(x)) && (~male(x) || ~female(x));\n" +
//                        "!@x ($y daughter(x,y)) -> female(x), child(x,y);\n" +
//                        "!@x ($y son(x,y)) -> male(x), child(x,y);\n" +
//                        "!@x @y father(x,y) -> male(x), child(y,x);\n" +
//                        "!@x @y child(y,x) -> (male(y) -> son(y,x)), (female(y) -> daughter(y,x));\n" +
//                        "!@x @y child(x,y) -> (male(y) -> father(y,x)), (female(y) -> mother(y,x));\n" +
//                        "!father(John, Tom);\n" +
//                        "!daughter(Sarah, John);\n" +
//                        "!age(John, 37);\n" +
//                        "!age(Tom, 12);\n" +
//                        "!age(Sarah, 4);" +
//                        "!@x @y @a @b age(x,a), age(y,b), a > b -> older(x,y);\n" +
//                        "!@x @y @a @b age(x,a), age(y,b), a < b -> younger(x,y);\n"
//        ));
//        mind.compile();
//        mind.setText(new StringBuffer(
//                "!@x $y father(y,x); " +
//                "!@x ~father(x,x);"
//        ));
//        mind.compile();
//        mind.setText(new StringBuffer(
//                " * это комментарий\r" +
//                        "!num(0); *** и это тоже ;;;\r" +
//                        "!@x (num(x), x < 3) -> num(++x);\r"
//        ));
//        mind.setText(new StringBuffer(
//                "!@x child(x) -> ((male(x) -> boy(x)), (female(x) -> girl(x)));\n" +
//                "!@x @y fath(x,y) -> (child(y), male(x), native(x,y));\n" +
//                "*!@x @y daug(x,y) -> (fath(y,x), child(x), female(x));\n" +
//                "*!@x @y son(x,y) -> (fath(y,x), child(x), male(x));\n" +
//                "*!@x @y (fath(x,y), male(y)) } son(y,x);\n" +
//                "*!@x @y (fath(x,y), female(y)) -> daug(y,x);\n" +
//                "!@x boy(x) -> (male(x), child(x));\n" +
//                "!@x girl(x) -> (female(x), child(x));\n" +
//                "*!@x ~fath(x,x);\n" +
//                "*!@x ~daug(x,x);\n" +
//                "*!@x ~son(x,x);\n" +
//                "*!@x $y fath(y,x);\n" +
//                "!@x ~(male(x), female(x));\n" +
//                "!@x male(x) || female(x);\n"));

//        mind.setText(new StringBuffer("=createTVar(a,b) {createTVar = a+b;};"));
//        if (args.length > 0 && new File(args[0]).exists()) {
//            if (args[0].endsWith(".e")) {
//                Screen.loadCompiledFile(mind, args[0]);
//            } else {
//                Screen.loadSourceFile(mind, args[0]);
//            }
//        }
//
//        mind.compile();

//        try {
//            mind.compile("!@x num(x), x < 20 -> num(++x);");
//            mind.compile("!num(0);");
////            mind.compile("!num(0);");
////            mind.compile("!@x num(x), x < 10 -> num(++x);");
//        } catch (ParseErrorException e) {
//            e.printStackTrace();
//        } catch (RuntimeErrorException e) {
//            e.printStackTrace();
//        }

//        try {
//            mind.compile("!$x a(x,ttt);");
//            mind.compile("!$x ~a(x,ttt);");
//            mind.compile("!@x b(x,ttt);");
//            mind.compile("!@x ~c(x,ttt);");
//        } catch (ParseErrorException e) {
//            e.printStackTrace();
//        } catch (RuntimeErrorException e) {
//            e.printStackTrace();
//        }

//        try {
//            mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); "
//                    + "!a(mmm); "
//                    + "!a(nnn); "
//                    + "!b(ooo); "
//                    + "!d(v); "
////                    + "!d(nn); "
//                    + "!@x a(x) -> ~n(x); ");

//            mind.mark();
//            mind.compile("!@x $y father(y,x);\n" +
//                    "!@x $y mother(y,x);\n" +
//                    "!@x ~father(x,x);\n" +
//                    "!@x ~mother(x,x);\n" +
////                            "!@x (male(x) || female(x));" +
////                            "!@x ~(male(x) && female(x));" +
////                            "!@x ($y daughter(x,y)) -> female(x) && child(x,y);\n" +
////                            "!@x ($y son(x,y)) -> male(x) && child(x,y);\n" +
//                    "!@x @y father(x,y) -> male(x) && child(y,x);\n" +
//                    "!@x @y mother(x,y) -> female(x) && child(y,x);\n" +
//                    "!@x @y child(x,y) -> father(y,x) || mother(y,x);\n" +
////                    "!@x @y child(y,x) -> (male(y) -> son(y,x)) && (female(y) -> daughter(y,x));\n" +
////                    "!@x @y child(x,y) -> (male(y) -> father(y,x)) && (female(y) -> mother(y,x));\n" +
//                    "!father(John, Tom);\n" +
////                            "!daughter(Sarah, John);\n" +
////                    "!age(John, 37);\n" +
////                    "!age(Tom, 12);\n" +
////                    "!age(Sarah, 4);" +
////                    "!@x @y @a @b age(x,a), age(y,b), a > b -> older(x,y);\n" +
////                    "!@x @y @a @b age(x,a), age(y,b), a < b -> younger(x,y);\n"
//                    "");
//
////                System.out.println(mind.getLog().getCurrent(LogMode.ALL).getRecord());
//                mind.commit();
//
//        } catch (ParseErrorException ex) {
//            Logger.getLogger(Kanger.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (RuntimeErrorException ex) {
//            Logger.getLogger(Kanger.class.getName()).log(Level.SEVERE, null, ex);
//        }


////
//        try {
//            mind.compile(
//                    "!num(0); "
//                            + "!@x num(x) && x < 10 && x > -10 -> num(++x);");
//
//
//        } catch (ParseErrorException ex) {
//            Logger.getLogger(Kanger.class.getName()).log(Level.SEVERE, null, ex);
//        } catch (RuntimeErrorException ex) {
//            Logger.getLogger(Kanger.class.getName()).log(Level.SEVERE, null, ex);
//        }

//        try {

//            mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
//                    "!@x a(x) -> ~n(x); " +
//                    "!a(nnn); " +
//                    "!b(ooo); " +
//                    "!d(v);");


//        mind.compile("!@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x)); !e(z);");
//        mind.query("?$x f(x);");

//        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
//                "!@x a(x) -> ~n(x); " +
//                "!a(nnn); " +
//                "!b(ooo); " +
//                "!d(v);");
//        mind.query("?n(nnn);");

//            mind.compile(
//                    "!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
//                            "!a(mmm); " +
//                            "!a(nnn); " +
//                            "!b(ooo); " +
//                            "!d(v); " +
//                            "!@x a(x) -> ~n(x); " +
//                    "!@x (a(x) || b(x)); " +
//                            "!@y ~(a(y) && b(y));" +
//                            "!a(nn);" +
//                    ""
//            );


//        mind.compile("" +
//                "!@x ~p(x,x);" +
//                "!@x $y p(y,x);" +
//                "!@x @y p(x, y) -> c(y, x);" +
//                "!@x @y d(x, y) -> c(x, y), p(y,x), f(x);" +
//                "!@x @y s(x, y) -> c(x, y), p(y,x), m(x);" +
////                "!@x @y c(x, y) -> (s(x, y) || d(x, y));" +
//                "!p(J,T);" +
//                "!d(M,J);" +
//                "!@x (m(x) || f(x)), ~(m(x) && f(x));" +
//                "");


        mind.compile(
                "!@x ~father(x,x);" +
                        "!@x $y father(y,x);" +
                        "!@x @y father(x,y) -> male(x) && child(y,x) && (male(y) -> son(y,x)) && (female(y) -> daughter(y,x));" +
                        "!@x @y daughter(x,y) -> child(x,y) && female(x);" +
                        "!@x (male(x) || female(x)), ~(male(x) && female(x));" +
                        "!father(John,Tom);" +
                        "!daughter(Mary,John);" +
                        "");



//        mind.compile("!@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x)); !e(z);");
//        mind.query("?$x f(x);");

//        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
//        mind.query("?@x $y a(y,x);");

//
//        Mind mind = new Mind(mindRoot);
//        mind.compile("!@x ~a(x,x); !@x $y a(y,x);");
//        mind.query("?$x @y a(x,y);");

//
////            mind.compile("!@x (a(x) || b(x)) -> (c(x) -> d(x)) && (e(x) -> f(x));");
////            mind.query("? (a(z) && c(z)) -> d(z);");
////            mind.query("?b(z) -> d(z);");
////            mind.query("?$x f(x);");
//

//        mind.compile("!num(0); !@x num(x) && x < 10 -> num(++x);");
//        mind.query("?$x $y num(x) && num(y) && x + y = 7;");

//        mind.compile("!(@x a(x) -> b(x)), (@y b(y) -> c(y)), (@z c(z) -> d(z)); " +
//                "!@x a(x) -> ~n(x); " +
//                "!a(nnn); " +
//                "!b(ooo); " +
//                "!d(v);");
//        mind.query("?n(nnn);");

//        Screen.session(mind);

////            mind.compile("!@x (a(x) || b(x)) && ~(a(x) && b(x));" +
////                    "!a(nnn);" +
////                    ""
////            );
//
////            mind.query("?$x num(x);");
//
////            mind.compile("!@x x in 0..10 -> num(x);"
////            mind.query("?$x num(x);"
//                            ""
//            );
//
//            new Linker(mind).link(true);

//            mind = mindRoot;


//        for (int i = 0; i < set01.size(); ++i) {
//            Mind mind = (Mind) set01.get(i).run("set01_" + String.format("%02X", i + 1));
//////            Screen.session(mind);
//        }
////
//        for (int i = 0; i < set02.size(); ++i) {
//            Mind mind = (Mind) set02.get(i).run("set02_" + String.format("%02X", i + 1));
//////            Screen.session(mind);
//        }


//        Mind mind = (Mind) set01.get(9 - 1).run("set02_09");
        Screen.session(mind);

//            mind = new Mind(mindRoot); set01_04(); mindRoot.release();

//            mindRoot.getLog().commit(mind.getLog());

//        } catch (ParseErrorException e) {
//            e.printStackTrace();
//        } catch (RuntimeErrorException e) {
//            e.printStackTrace();
//        }


//        mind.compileLine[jhjij("!@x ~a(x) || b(x);");
//        mind.compileLine("!@x ~a(x) -> b(x);");
//        mind.compileLine("!@x ~a(x), b(x);");
//        mind.compileLine("!@x $y ~a(x,y), f(y, aaa) -> b(x,y);");
//        Screen.showRights(mind);
//        Compiler c = new Compiler(mind);;
//        c.compileLine(new StringBuffer("!@(x) a(b);"), 0);
    }

    private static void fail(String msg) throws RuntimeErrorException {
        throw new RuntimeErrorException("FAIL: " + msg);
    }

    private static boolean exists(Mind mind, String name, Object o) {
        for (Term t : mind.getValues().getValues(name)) {
            if (o.equals(t.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static void showResult(Object o, Mind mind, Boolean assertResult) throws RuntimeErrorException {
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
        if (!(mind.getQueryResult() + "").equals(assertResult + "")) {
            fail(o + " Expected: " + assertResult);
        }
    }

}

// проверка
