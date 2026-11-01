/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

/** Required behavior, intentionally RED until native alternating-prefix collision is fixed. */
public final class ContextAlternatingPrefixCollisionGate {
    private static int checks, missing;
    private static void check(String label, String first, String second, boolean expected) throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile(first, null, false)) throw new AssertionError("Initial row rejected: " + label);
        boolean actual = mind.compile(second, null, false);
        checks++;
        if (actual != expected) missing++;
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
        check("dependent-control", dependent, restriction, true);
        check("dependent-reverse", restriction, dependent, true);
        check("ground-a-control", fixed, "!@u ~(rel(1,u,1)&&rel(1,u,2));", false);
        check("exists-forall-control", "!$u @b edge(u,b);", "!@u ~(edge(u,1)&&edge(u,2));", false);
        check("named-negative-control", fixed, "!~rel(1,1,1);", true);
        System.out.println("ALTERNATING_PREFIX_COLLISION_GATE checks=" + checks + " missing=" + missing);
        if (missing != 0) System.exit(1);
    }
}
