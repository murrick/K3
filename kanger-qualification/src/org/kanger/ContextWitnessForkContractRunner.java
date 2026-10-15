/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

/** Pending native consistency contract; deliberately red until native witness identity is preserved. */
public final class ContextWitnessForkContractRunner {
    private static int checks, failures;
    private static void check(boolean value,String label) {
        ++checks; if (!value) { ++failures; System.out.println("FAIL: "+label); }
    }
    private static Mind mind() throws Exception {
        User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind); return mind;
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-fork-contract-").toString());
        String rules = "!@x p(x,1) -> q(x,1); !@x p(x,1) -> r(x,1); !@x q(x,1) && r(x,1) -> out(x,1);";
        for (String root : new String[]{"!$x p(x,1);", "!p(1,1);", "!$x q(x,1) && r(x,1);"}) {
            Mind m = mind(); check(m.compile(root+rules,null,false),"consistent base: "+root);
            check(!m.compile("!@x ~out(x,1);",null,false),"reject universal denial: "+root);
        }
        Mind reversed = mind();
        check(!reversed.compile("!@x ~out(x,1);"+rules+"!$x p(x,1);",null,false),"reject fork with denial inserted first");
        Mind unary = mind();
        check(!unary.compile("!$x p(x,1); !@x p(x,1)->q(x,1); !@x q(x,1)->out(x,1); !@x ~out(x,1);",null,false),
                "reject unary existential chain denial");
        for (boolean reverse : new boolean[]{false,true}) {
            Mind direct = mind();
            check(direct.compile(reverse ? "!@x ~out(x,1);" : "!$x out(x,1);",null,false),"direct consistent first input");
            check(!direct.compile(reverse ? "!$x out(x,1);" : "!@x ~out(x,1);",null,false),
                    "reject direct existential/universal conflict, reverse="+reverse);
        }
        for (String independent : new String[]{
                "!$x out(x,1); !$y ~out(y,1);",
                "!$x out(x,1); !@x ~out(x,2);",
                "!$x out(x,1); !@x other(x,1);"})
            check(mind().compile(independent,null,false),"independent witness/fixed/predicate control: "+independent);
        System.out.println("CONTEXT_WITNESS_FORK_CONTRACT_"+(failures == 0 ? "PASS" : "FAIL")+" checks="+checks+" failures="+failures);
        System.exit(failures == 0 ? 0 : 1);
    }
}
