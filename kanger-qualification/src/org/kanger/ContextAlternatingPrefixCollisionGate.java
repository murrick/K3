/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

/** Required behavior, intentionally RED until native alternating-prefix collision is fixed. */
public final class ContextAlternatingPrefixCollisionGate {
    private static int checks, missing;
    private static void observe(String label, Mind mind) throws Exception {
        for (org.kanger.interfaces.IRule item : mind.getRules()) {
            org.kanger.units.Rule rule = (org.kanger.units.Rule)item;
            if (rule.isDeleted(mind)) continue;
            for (java.util.List<org.kanger.units.Domain> branch : rule.getTree()) {
                for (org.kanger.units.Domain domain : branch) {
                    int unbound = 0;
                    for (org.kanger.interfaces.IArgument argument : domain.getArguments()) {
                        if (argument.getType() == org.kanger.enums.ArgumentType.TVARIABLE
                                && argument.isEmpty(mind)) unbound++;
                    }
                    System.out.println("OBSERVATION " + label + " rule=" + rule.getId()
                            + " stored=" + rule.isStored() + " generated=" + rule.isGenerated()
                            + " complete=" + domain.isComplete() + " unbound=" + unbound);
                }
            }
        }
    }
    private static void check(String label, String first, String second, boolean expected) throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile(first, null, false)) throw new AssertionError("Initial row rejected: " + label);
        boolean actual = mind.compile(second, null, false);
        checks++;
        if (actual != expected) { missing++; observe(label, mind); }
        System.out.println(label + " expected=" + expected + " actual=" + actual);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", java.nio.file.Files.createTempDirectory("prefix-collision-").toString());
        String fixed = "!@a $u @b rel(a,u,b);";
        String dependent = "!@a @b $u rel(a,u,b);";
        String restriction = "!@a @u ~(rel(a,u,1)&&rel(a,u,2));";
        check("alternating", fixed, restriction, false);
        check("reverse", restriction, fixed, false);
        check("alpha", "!@x $z @y rel(x,z,y);", restriction, false);
        check("reordered-literals", fixed, "!@a @u ~(rel(a,u,2)&&rel(a,u,1));", false);
        check("unrelated-horn-in-inventory", fixed+" !@x left(x)->right(x);", restriction, false);
        check("dependent-control", dependent, restriction, true);
        check("dependent-reverse", restriction, dependent, true);
        check("ground-a-control", fixed, "!@u ~(rel(1,u,1)&&rel(1,u,2));", false);
        check("exists-forall-control", "!$u @b edge(u,b);", "!@u ~(edge(u,1)&&edge(u,2));", false);
        check("named-negative-control", fixed, "!~rel(1,1,1);", true);
        check("universal-unit", "!@x p(x);", "!@y ~p(y);", false);
        check("universal-unit-reverse", "!@y ~p(y);", "!@x p(x);", false);
        check("universal-constraint", "!@a @b pair(a,b);",
                "!@a ~(pair(a,1)&&pair(a,2));", false);
        check("universal-constraint-reverse", "!@a ~(pair(a,1)&&pair(a,2));",
                "!@a @b pair(a,b);", false);
        check("universal-ground-negative", "!@x p(x);", "!~p(1);", false);
        check("ground-positive-universal-negative", "!p(1);", "!@y ~p(y);", false);
        check("independent-argument-control", "!@x pair(x,1);", "!@y ~pair(y,2);", true);
        check("independent-predicate-control", "!@x p(x);", "!@y ~q(y);", true);
        System.out.println("ALTERNATING_PREFIX_COLLISION_GATE checks=" + checks + " missing=" + missing);
        if (missing != 0) System.exit(1);
    }
}
