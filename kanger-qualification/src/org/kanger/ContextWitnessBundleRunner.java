/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextProductionRoundsReference.Declaration;
import org.kanger.ContextWitnessBundle.Bundle;

/** Whole-scope roundtrip controls; derived witnesses remain excluded. */
public final class ContextWitnessBundleRunner {
    private static int checks;
    private static Source source(int id) { return new Source(new UUID(0,id),1,0,id == 0,"base"); }
    private static Mind memory() throws Exception { User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m); return m; }
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Bundle bundle(int id,String text) throws Exception {
        return ContextWitnessBundle.capture(new Declaration(source(id),text),10000);
    }
    private static boolean nativeAnswer(String[] assertions,String restriction) throws Exception {
        Mind mind = memory(); for (String text : assertions) mind.compileLine(text,false,new LinkedList<org.kanger.interfaces.ITerm>());
        return mind.compile(restriction,null,false);
    }
    private static void same(String[] assertions,String[] restrictions) throws Exception {
        List<Bundle> bundles = new ArrayList<>();
        for (int i=0;i<assertions.length;++i) bundles.add(bundle(i,assertions[i]));
        for (String restriction : restrictions) {
            Mind restored = ContextWitnessBundle.restore(bundles,10000);
            require(restored.compile(restriction,null,false) == nativeAnswer(assertions,restriction),
                    "roundtrip preserves qualification: "+Arrays.toString(assertions)+" / "+restriction);
        }
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-bundle-").toString());
            String[] restrictions = {"!@x ~(p(x) && q(x));","!~p(1);","!@x ~p(x);","!@x ~q(x);","!anchor(1);"};
            same(new String[]{"!$x p(x) && q(x);"},restrictions);
            same(new String[]{"!$x p(x);","!$x q(x);"},restrictions);
            same(new String[]{"!$x p(x) && ~q(x);"},restrictions);
            same(new String[]{"!$x $y p(x) && q(y);"},restrictions);
            same(new String[]{"!$x pair(x,x);"},new String[]{"!@x ~pair(x,x);","!~pair(1,John);","!anchor(1);"});
            same(new String[]{"!$x $y pair(x,y);"},new String[]{"!@x ~pair(x,x);","!~pair(1,John);","!anchor(1);"});
            same(new String[]{"!$x p(x) && fixed(1,John);"},new String[]{"!~fixed(1,John);","!~fixed(John,1);","!~fixed(1,1);"});
            same(new String[]{"!$x p(x);","!$x p(x);"},restrictions);
            Bundle shared = bundle(0,"!$x p(x) && q(x);");
            require(shared.witnesses == 1 && shared.atoms.size() == 2
                    && shared.atoms.get(0).arguments.get(0).witness == shared.atoms.get(1).arguments.get(0).witness,
                    "carrier preserves one binder across atoms");
            Bundle independent = bundle(0,"!$x $y p(x) && q(y);");
            require(independent.witnesses == 2 && independent.atoms.get(0).arguments.get(0).witness != independent.atoms.get(1).arguments.get(0).witness,
                    "carrier preserves separate binders");
            for (String excluded : new String[]{"!@x $y edge(x,y);","!$y @x edge(x,y);","!$x (p(x)||q(x));",
                    "!@x $y p(x) -> q(y);","!$x p(x+1);","!p(1);"}) {
                boolean stopped = false;
                try { bundle(0,excluded); } catch (IllegalArgumentException | IllegalStateException expected) { stopped = true; }
                require(stopped,"unsupported witness scope cannot enter carrier: "+excluded);
            }
            boolean stopped = false;
            try { ContextWitnessBundle.restore(Arrays.asList(shared,bundle(0,"!$x r(x);")),10000); }
            catch (IllegalArgumentException expected) { stopped = true; }
            require(stopped,"one exact source occurrence cannot authenticate different declarations");
            stopped = false;
            try { ContextWitnessBundle.restore(Collections.singletonList(shared),1); }
            catch (IllegalStateException expected) { stopped = true; }
            require(stopped,"resource stop cannot return a partly restored Mind");
            require(shared.source.equals(source(0)),"source occurrence remains attached to the complete bundle");
            System.out.println("CONTEXT_WITNESS_BUNDLE_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
