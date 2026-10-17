/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.units.Rule;
import org.kanger.units.Term;
import org.kanger.ContextProductionNativeBinding.*;

/** Native origin countermodels, global existential sharing, exact row provenance and cache exclusion. */
public final class ContextProductionNativeBindingRunner {
    private static int checks;
    private static final UUID CONTEXT = new UUID(0,77);
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    private static Mind mind(String text) throws Exception {
        User u = new User(); Mind m = new Mind(u); u.setCurrentMind(m); require(m.compile(text,null,false),"native fixture"); return m;
    }
    private static ContextProductionInventory inventory(Mind m) throws Exception { return ContextProductionInventory.inspect(m,CONTEXT,8,true,Collections.emptyList(),1000); }
    private static Result bind(Mind m) throws Exception { return ContextProductionNativeBinding.bind(m,inventory(m),4000000,10000); }
    private static Rule primary(Mind m,String fragment) throws Exception {
        for (org.kanger.interfaces.IRule item : m.getRules()) if (!m.getRules().isGenerated(item) && item.getOrigin().contains(fragment)) return (Rule)item;
        throw new AssertionError("No primary "+fragment);
    }
    private static void refused(Result result,String why) { require(!result.bound && result.snapshot == null,why+": "+result.reason); }
    private static void tamper(String original,String replacement,String why) throws Exception {
        Mind m = mind(original); Rule rule = null;
        for (org.kanger.interfaces.IRule item : m.getRules()) if (!m.getRules().isGenerated(item)) { rule = (Rule)item; break; }
        rule.setOrigin(new Term(replacement,m));
        // Capture the NEW metadata: the native comparison, not a stale hash, must detect this mismatch.
        refused(bind(m),why);
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("native-binding-").toString());
            Mind source = mind("!$w p(w,1)&&flag(w,1); !@w p(w,1)->q(w,1); !scalar(John,2);");
            ContextProductionInventory inventory = inventory(source); Result result = ContextProductionNativeBinding.bind(source,inventory,4000000,10000);
            require(result.bound,"whole native primary inventory binds: "+result.reason);
            require(result.snapshot.primary.size() == inventory.primary.size() && result.snapshot.primary.size() == 3,"every primary row has exactly one bound entry");
            require(result.snapshot.excludedGenerated.size() == inventory.generated.size() && inventory.generated.size() >= 3,"generated witness cache is explicitly excluded, not replayed as primary");
            require(result.snapshot.primary.stream().noneMatch(e -> result.snapshot.excludedGenerated.contains(e.source.rule)),"no cache ID becomes a primary source");
            Entry witness = result.snapshot.primary.stream().filter(e -> e.kind == Kind.WITNESS_BUNDLE).findFirst().get();
            require(witness.witnesses() == 1 && witness.bundle.atoms.size() == 2 && witness.clauses.isEmpty(),"one global witness binder owns the entire conjunction");
            require(witness.source.context.equals(CONTEXT) && witness.source.revision == 8 && witness.source.localX
                    && witness.source.view.equals("inventory:"+inventory.fingerprint) && witness.source.rule == primary(source,"$w").getId(),"source identity uses exact native row, role and acquired view");
            Mind restored = ContextWitnessBundle.restore(Arrays.asList(witness.bundle),10000);
            require(!restored.compile("!@x ~(p(x,1)&&flag(x,1));",null,false),"typed bound bundle replay preserves the joint existential witness");
            Entry scalar = result.snapshot.primary.stream().filter(e -> e.statement.contains("scalar")).findFirst().get();
            require(scalar.kind == Kind.CLAUSES && scalar.clauses.size() == 1 && scalar.clauses.get(0).variables.isEmpty(),"ordinary scalar fact remains explicit ground primary evidence");
            require(result.snapshot.primary.stream().filter(e -> e.kind == Kind.CLAUSES).anyMatch(e -> !e.clauses.get(0).variables.isEmpty()),"universal producer has native-checked clause variables");
            Mind independent = mind("!$u $v p(u,1)&&flag(v,1);"); Result separate = bind(independent);
            require(separate.bound && separate.snapshot.primary.get(0).witnesses() == 2,"two native binders remain independent");
            require(ContextWitnessBundle.restore(Arrays.asList(separate.snapshot.primary.get(0).bundle),10000).compile("!@x ~(p(x,1)&&flag(x,1));",null,false),"independent bundle does not acquire false shared-witness evidence");
            tamper("!$w p(w,1)&&flag(w,1);","!$u $v p(u,1)&&flag(v,1);","native shared witness cannot be exported as independent text binders");
            tamper("!$u $v p(u,1)&&flag(v,1);","!$w p(w,1)&&flag(w,1);","native independent witnesses cannot be merged by origin text");
            tamper("!@x p(x,1)->q(x,1);","!@x p(x,1)->~q(x,1);","origin sign mismatch is refused");
            tamper("!@x p(x,1)&&flag(x,1)->q(x,1);","!@x @y p(x,1)&&flag(y,1)->q(x,1);","lost shared universal-variable constraint is refused");
            tamper("!scalar(John,2);","!scalar(Mary,2);","fixed semantic value mismatch is refused");
            tamper("!@x p(x,1)->q(x,1);","!@x p(x,1)->q(x,1); !extra(1);","multiple statements cannot masquerade as one source row");
            tamper("!scalar(1);","","missing origin is not silently skipped");
            Mind alpha = mind("!@x p(x,1)->q(x,1);"); primary(alpha,"@x").setOrigin(new Term("!@z p(z,1)->q(z,1);",alpha));
            require(bind(alpha).bound,"alpha-equivalent universal names remain valid");
            Mind alphaWitness = mind("!$w p(w,1)&&flag(w,1);"); primary(alphaWitness,"$w").setOrigin(new Term("!$z p(z,1)&&flag(z,1);",alphaWitness));
            require(bind(alphaWitness).bound,"alpha-equivalent whole witness bundle remains valid");
            Mind conjunction = mind("!scalar(1)&&tag(2);"); Result ground = bind(conjunction);
            require(ground.bound && ground.snapshot.primary.size() == 1 && ground.snapshot.primary.get(0).clauses.size() == 2,"ground conjunction retains one source across all native clauses");
            Mind signed = mind("!~scalar(John,2);"); Result negative = bind(signed);
            require(negative.bound && !negative.snapshot.primary.get(0).clauses.get(0).literals.get(0).positive,"signed ordinary primary literal retains its sign");
            Mind wide = mind("!scalar(1); !@x a(x,1)&&b(x,1)&&c(x,1)&&d(x,1)->e(x,1);");
            refused(bind(wide),"unsupported later native clause width suppresses any bound prefix");
            Mind function = mind("!@x p(x,1)->q(x+1,1);"); refused(bind(function),"unsupported function argument remains explicit refusal");
            ContextProductionInventory old = inventory(source); require(source.compile("!later(9);",null,false),"source changes after inventory capture");
            refused(ContextProductionNativeBinding.bind(source,old,4000000,10000),"stale inventory cannot authorize native binding");
            refused(ContextProductionNativeBinding.bind(source,inventory(source),1,10000),"work limit publishes no prefix");
            boolean immutable = false; try { result.snapshot.primary.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"bound primary list is immutable");
            immutable = false; try { scalar.clauses.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            require(immutable,"bound native clause evidence is immutable");
            System.out.println("CONTEXT_PRODUCTION_NATIVE_BINDING_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
