/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Predicate;
import org.kanger.units.Term;

/** Conversion, fresh value reads, name replacement, hydration and callback behavior. */
public final class FreshPredicateNameRunner {
    private static int checks;
    private static void check(boolean result) {
        if (!result) throw new AssertionError("check " + checks);
        checks++;
    }
    private static final class MutableTerm extends Term {
        Object value; int reads; RuntimeException failure;
        @Override public Object getValue() {
            reads++;
            if (failure != null) throw failure;
            return value;
        }
    }
    private static final class Rendered {
        int reads; String value; RuntimeException failure;
        @Override public String toString() {
            reads++;
            if (failure != null) throw failure;
            return value;
        }
    }
    public static void main(String[] ignored) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("predicate-name-").toString());
        Mind mind = new Mind(new User());
        MutableTerm name = new MutableTerm(); name.setId(42);
        name.value = new String("alpha");
        Predicate predicate = new Predicate((ITerm)name,2);
        check("alpha".equals(predicate.getName(mind))); check(name.reads==1);
        name.value="beta";
        check("beta".equals(predicate.getName(mind))); check(name.reads==2);
        name.value=""; check("".equals(predicate.getName(mind)));
        name.value="имя ☃"; check("имя ☃".equals(predicate.getName(mind)));
        name.value=null; check("null".equals(predicate.getName(mind)));
        name.value=17; check("17".equals(predicate.getName(mind)));
        name.value=true; check("true".equals(predicate.getName(mind)));
        name.value=new StringBuilder("builder"); check("builder".equals(predicate.getName(mind)));
        char[] array={'a','b'}; name.value=array;
        check(array.toString().equals(predicate.getName(mind)));
        Rendered rendered=new Rendered(); rendered.value="rendered"; name.value=rendered;
        check("rendered".equals(predicate.getName(mind))); check(rendered.reads==1);
        rendered.value=null;
        check("null".equals(predicate.getName(mind))); check(rendered.reads==2);
        RuntimeException failure=new IllegalStateException("render failure"); rendered.failure=failure;
        try { predicate.getName(mind); throw new AssertionError("missing render failure"); }
        catch(RuntimeException actual) { check(actual==failure); }
        check(rendered.reads==3);
        name.failure=failure;
        try { predicate.getName(mind); throw new AssertionError("missing value failure"); }
        catch(RuntimeException actual) { check(actual==failure); }
        check(rendered.reads==3);
        Term replacement=(Term)mind.getTerms().add("replacement");
        predicate.setName(replacement);
        check("replacement".equals(predicate.getName(mind)));
        check("replacement".equals(predicate.getName(null)));
        Term lazy=(Term)mind.getTerms().add("lazy");
        predicate.setPersistentNameId(lazy.getId());
        check("lazy".equals(predicate.getName(mind)));
        predicate.setName(replacement); check("replacement".equals(predicate.getName(mind)));
        predicate.setPersistentNameId(Long.MAX_VALUE);
        try { predicate.getName(mind); throw new AssertionError("missing hydration failure"); }
        catch(NullPointerException expected) { check(true); }
        // Identity is part of this candidate's boundary, unlike direct-return.
        for (String text : new String[] {new String("alpha"), new String(""), "имя ☃", "\uD800", "\u0000"}) {
            MutableTerm identity = new MutableTerm(); identity.setId(77); identity.value = text;
            Predicate named = new Predicate(identity, 1);
            String first = named.getName(mind), second = named.getName(mind);
            Object source = text;
            String referenceFirst = source + "", referenceSecond = source + "";
            check(first.equals(referenceFirst));
            check(second.equals(referenceSecond));
            check((first == text) == (referenceFirst == text));
            check((first == second) == (referenceFirst == referenceSecond));
            check(identity.reads == 2);
        }
        MutableTerm mutable = new MutableTerm(); mutable.setId(88);
        StringBuilder builder = new StringBuilder("one"); mutable.value = builder;
        Predicate changing = new Predicate(mutable, 1);
        String saved = changing.getName(mind); builder.append("two");
        check("one".equals(saved)); check("onetwo".equals(changing.getName(mind)));
        check(mutable.reads == 2);
        System.out.println("FRESH_PREDICATE_NAME_BOUNDARIES_OK checks="+checks);
    }
}
