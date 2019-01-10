package kanger;

import kanger.exception.ParseErrorException;
import kanger.exception.RuntimeErrorException;
import kanger.storage.Data;
import kanger.storage.Index;
import kanger.units.Term;

import java.io.Externalizable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 */
public class Kanger {

    private static String text = "* Базовые правила для родственных отношений\n" +
            "\n" +
            "!@x $y parent(y,x);\n" +
            "* У всех есть отцы\n" +
            "\n" +
            "!@x ~parent(x,x);\n" +
            "* Никто не может быть собственным отцом.\n" +
            "\n" +
            "!@x (male(x) || female(x)) && ~(male(x) && female(x));\n" +
            "* Каждый является мужчиной или женщиной.\n" +
            "\n" +
            "!@x @y parent(x,y) ->\n" +
            "    child(y,x),\n" +
            "    (male(x) -> father(x,y)),\n" +
            "    (female(x) -> mother(x,y));\n" +
            "\n" +
            "!@x @y child(x,y) ->\n" +
            "    parent(y,x),\n" +
            "    (male(x) -> son(x,y)),\n" +
            "    (female(x) -> daughter(x,y));\n" +
            "\n" +
            "!@x @y father(x,y) -> male(x), parent(x,y);\n" +
            "!@x @y mother(x,y) -> female(x), parent(x,y);\n" +
            "\n" +
            "!@x @y daughter(x,y) -> female(x), child(x,y);\n" +
            "!@x @y son(x,y) -> male(x), child(x,y);\n" +
            "* Дочь всегда женского пола, сын – мужского.\n" +
            "\n" +
            "* База данных. Утверждения.\n" +
            "\n" +
            "!father(John, Tom);\n" +
            "!daughter(Sarah, John);\n" +
            "\n" +
            "* Возраст\n" +
            "\n" +
            "!age(John, 37);\n" +
            "!age(Tom, 12);\n" +
            "!age(Sarah, 4);\n" +
            "\n";

    public static void main(String[] args) throws ParseErrorException, RuntimeErrorException {

        User user = new User();
        Mind mind = new Mind(user);


        try {

//            try (Data d = new Data()){
//                d.open("test.data");
//                d.set(-1, new Term( "One", user));
//                long offset01 = d.getCurrentOffset();
//                d.set(-1, new Term("Two", user));
//                long offset02 = d.getCurrentOffset();
////
//                Object o1 = d.get(offset01);
//                Object o2 = d.get(offset02);
////                System.out.println(o1);
////                System.out.println(o2);
//
//                for (Externalizable e : d) {
//                    System.out.println(e);
//                }
//
//                d.remove(offset01);
//
//                System.out.println("--------------");
//                for (Externalizable e : d) {
//                    System.out.println(e);
//                }
//            } catch (ClassNotFoundException e) {
//                e.printStackTrace();
//            }

            try (Index x = new Index()) {
                x.open("test.index");

                for (Index.IndexOne o : x) {
                    System.out.println(o);
                }
                System.out.println("R:" + x.getReadCounter());
                System.out.println("W:" + x.getWriteCounter());

                x.set(1, 0001);
                x.set(2, 0002);
                x.set(9, 9879);
                x.set(5, 9872);
                x.set(8, 9873);
                x.set(7, 0007);
                x.set(6, 9875);
                x.set(4, 9876);
                x.set(3, 9877);

                Iterator<Index.IndexOne> z = x.iterator();
                while (z.hasNext()) {
                    System.out.println(z.next());
                }


                System.out.println("R:" + x.getReadCounter());
                System.out.println("W:" + x.getWriteCounter());
                System.out.println("--------------");

                x.dropReadCounter();
                x.dropWriteCounter();

                x.set(1, 1000001);
                x.set(23, 9877);
                x.set(2, 9877);
                x.remove(6);

                for (Index.IndexOne o : x) {
                    System.out.println(o);
                }
                System.out.println("R:" + x.getReadCounter());
                System.out.println("W:" + x.getWriteCounter());

                for(int i=0; i< 100; ++i) {
                    List<Long> keys = new ArrayList<>();
                    keys.addAll(x.getOne(7).getData());
                    keys.add(i + 0x7700L);
                    x.set(7, keys);
                }

                for (Index.IndexOne o : x) {
                    System.out.println(o);
                }
                System.out.println("R:" + x.getReadCounter());
                System.out.println("W:" + x.getWriteCounter());


            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.exit(0);

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


//        mind.compile(
//                "!@x ~father(x,x);" +
//                        "!@x $y father(y,x);" +
//                        "!@x @y father(x,y) -> male(x) && child(y,x) && (male(y) -> son(y,x)) && (female(y) -> daughter(y,x));" +
//                        "!@x @y daughter(x,y) -> child(x,y) && female(x);" +
//                        "!@x (male(x) || female(x)), ~(male(x) && female(x));" +
//                        "!father(John,Tom);" +
//                        "!daughter(Mary,John);" +
//                        "");


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

//        mind.compile(text);
        Screen.session(user);

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
//            set01.get(i).run("set01_" + String.format("%02X", i + 1));
////            Screen.session(mind);
//        }
//
//        for (int i = 0; i < set02.size(); ++i) {
//            set02.get(i).run("set02_" + String.format("%02X", i + 1));
////            Screen.session(mind);
//        }
//

//        Mind mind = (Mind) set01.get(9 - 1).run("set02_09");

//        mind.compile("!a(A,12); !a(B,37);");
//        mind.query("?$x $y a(x, y) && y >= 12;");

//        mind.compile(text);
//        Screen.session(mind);

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

    private static boolean exists(Mind mind, String name, Object o) {
        for (Term t : mind.getValues().getValues(name)) {
            if (o.equals(t.getValue())) {
                return true;
            }
        }
        return false;
    }

//    private static void showResult(Object o, Mind mind, Boolean assertResult) throws RuntimeErrorException {
//        System.out.println("Query: " + mind.getQuerySource());
//        System.out.println("Result: " + mind.getQueryResult());
//        if (mind.getSolutions().size() > 0) {
//            System.out.println("Solves (" + mind.getSolutions().size() + "):");
//            int i = 0;
//            for (Record log : mind.getSolutions().getRoot()) {
//                System.out.println(String.format("\tSolution %03d: %s", ++i, log.toString()));
//            }
//        }
//        if (mind.getValues().size() > 0) {
////            mind.getValues().normalize();
//            System.out.println("Values (" + mind.getValues().size() + "):");
//            int i = 0;
//            for (Set<TValue> log : mind.getValues().getRoot().values()) {
//                String s = String.format("\tValue %03d: ", ++i);
//                String list = "";
//                for (TValue v : log) {
//                    if (!list.isEmpty()) {
//                        list += ", ";
//                    }
//                    list += v.toString();
//                }
//                System.out.println(s + list);
//            }
//        }
//        if (assertResult == null && !mind.getHypotesisStore().isEmpty()) {
//            System.out.println("Hypothesis (" + mind.getHypotesisStore().size() + "):");
//            for (int i = 0; i < mind.getHypotesisStore().getRoot().size(); ++i) {
//                System.out.printf("\t%3d:\t%s\n", i + 1, mind.getHypotesisStore().getRoot().toArray(new Hypotese[]{})[i].toString());
//            }
//        }
//        System.out.println("----------------------------------------------------");
//        if (!(mind.getQueryResult() + "").equals(assertResult + "")) {
//            fail(o + " Expected: " + assertResult);
//        }
//    }

}

// проверка
