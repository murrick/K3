/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.IContextResults;
import org.kanger.interfaces.IRule;
import org.kanger.units.Rule;

/** Typed unit-closure coverage and independent source alternatives. */
public final class DmzCurrentGroundProofInventoryRunner {
    private static int checks;
    private interface Attempt { void run() throws Exception; }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home", Files.createTempDirectory("dmz-ground-proofs-").toString());
            binary(false); binary(true); aliases(); patterns(); negativePatterns(); substitutions(); pending(false); pending(true);
            orphan(); restrictions(); refusals();
            System.out.println("DMZ_CURRENT_GROUND_PROOF_INVENTORY_PASS checks=" + checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static Mind root() throws Exception {
        User user = new User(); Mind q = new Mind(user); user.setCurrentMind(q); return q;
    }
    private static IContextResults.Revision pin(long revision) { return new IContextResults.Revision(UUID.randomUUID(), revision); }
    private static void accept(Mind q, IContextResults.Revision pin, long id, String text) throws Exception {
        require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q, pin, id, DmzReplayProvenance.Authority.EXTERNAL, text)),
                "native sourced input: " + text);
    }
    private static TerminalSupportCapture.Ground ground(String atom) throws Exception {
        return DmzGroundReplay.parseIdentity("!" + atom + ";").ground();
    }
    private static void count(DmzCurrentGroundProofInventory inventory, String atom, int expected) throws Exception {
        DmzCurrentGroundProofInventory.Proofs proofs = inventory.proofs(ground(atom), 10000);
        require(!proofs.truncated && proofs.witnesses.size() == expected,
                "typed witness count for " + atom + ": expected " + expected + ", actual " + proofs.witnesses.size());
        for (DmzProofWitnesses.Witness witness : proofs.witnesses)
            require(DmzProofWitnesses.compatible(witness, new HashMap<UUID, Long>()), "all witness pins coherent");
    }
    private static void nativeAnswer(Mind q, String atom, Boolean expected) throws Exception {
        String state = DmzObservationStateFingerprint.capture(q);
        try (TechnicalMindTransaction query = TechnicalMindTransaction.beginIsolated(q)) {
            require(Objects.equals(expected, query.mind().query("?" + atom + ";", null, false)), "native answer for " + atom);
        }
        require(state.equals(DmzObservationStateFingerprint.capture(q)) && !q.hasPendingTransactions(), "native comparison preserves Q");
    }
    private static DmzCurrentGroundProofInventory inventory(Mind q, boolean pending) throws Exception {
        String state = DmzObservationStateFingerprint.capture(q);
        DmzCurrentGroundProofInventory inventory = DmzCurrentGroundProofInventory.capture(q, 10000, pending);
        require(inventory.eligible && !inventory.truncated && !inventory.complete && inventory.isCurrent(q), "eligible scoped typed closure: " + inventory.gaps);
        require(state.equals(DmzObservationStateFingerprint.capture(q)), "inventory is read-only"); return inventory;
    }
    private static void binary(boolean negative) throws Exception {
        Mind q = root(); IContextResults.Revision rules = pin(12), a = pin(7), b = pin(20);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            accept(q, rules, 0, "!@x @y father(x,y) -> child(y,x);");
            accept(q, rules, 1, "!@x @y father(x,y) -> parent(x,y);");
            accept(q, rules, 2, "!@x @y parent(x,y) -> child(y,x);");
            accept(q, rules, 3, "!@x @y mother(x,y) -> child(y,x);");
            if (!negative) {
                accept(q, a, 0, "!father(John,Tom);"); accept(q, b, 0, "!mother(John,Tom);");
            } else accept(q, a, 0, "!~child(Tom,John);");
            DmzCurrentGroundProofInventory model = inventory(q, false);
            count(model, negative ? "~father(John,Tom)" : "child(Tom,John)", negative ? 2 : 3);
            count(model, negative ? "~mother(John,Tom)" : "father(John,Tom)", 1);
            count(model, negative ? "~parent(John,Tom)" : "mother(John,Tom)", 1);
            count(model, "child(John,Tom)", 0);
            nativeAnswer(q, "child(Tom,John)", !negative);
            nativeAnswer(q, "father(John,Tom)", !negative);
            require(model.isCurrent(q), "native comparisons keep exact model boundary current");
            DmzCurrentGroundProofInventory small = DmzCurrentGroundProofInventory.capture(q, 1, false);
            require(!small.eligible && small.truncated && small.graph == null, "partial closure never exposes a proof graph");
            require(model.proofs(ground(negative ? "~father(John,Tom)" : "child(Tom,John)"), 1).truncated,
                    "witness enumeration limit remains explicit");
        }
    }
    private static void aliases() throws Exception {
        Mind q = root(); IContextResults.Revision rules = pin(12), otherRules = pin(13), a = pin(7), b = pin(20);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            accept(q, rules, 0, "!@x @y father(x,y) -> child(y,x);");
            DmzReplayProvenance.replayRule(q, otherRules, 0, DmzReplayProvenance.Authority.EXTERNAL,
                    "!@x @y father(x,y) -> child(y,x);");
            accept(q, a, 0, "!father(John,Tom);");
            DmzReplayProvenance.replayRule(q, b, 0, DmzReplayProvenance.Authority.EXTERNAL, "!father(John,Tom);");
            DmzCurrentGroundProofInventory model = inventory(q, false);
            count(model, "father(John,Tom)", 2); count(model, "child(Tom,John)", 4);
            Set<String> pairs = new HashSet<String>();
            for (DmzProofWitnesses.Witness witness : model.proofs(ground("child(Tom,John)"), 10000).witnesses)
                pairs.add(witness.source.context + ":" + witness.premises.get(0).source.context);
            require(pairs.size() == 4, "each independent production/donor source pair retained");
            nativeAnswer(q, "child(Tom,John)", true);
        }
    }
    private static void patterns() throws Exception {
        Mind q = root(); IContextResults.Revision source = pin(1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            String[] statements = {
                "!@x repeated(x,x) -> echo(x);", "!repeated(John,John);", "!repeated(Mary,Tom);",
                "!@x pair(x,Tom) -> tagged(x);", "!pair(John,Tom);", "!pair(Mary,Sarah);",
                "!@x carry(x) -> delivered(x);", "!carry([John,Tom]);",
                "!@x @y triple(x,7,y) -> swapped(y,x);", "!triple(John,7,[Mary,Sarah]);",
                "!ready();",
                "!@x @y a(x,y) -> b(y,x);", "!@x @y b(x,y) -> a(y,x);", "!a(John,Tom);"
            };
            for (int i = 0; i < statements.length; ++i) accept(q, source, i, statements[i]);
            DmzCurrentGroundProofInventory model = inventory(q, false);
            for (String atom : new String[]{"echo(John)", "tagged(John)", "delivered([John,Tom])",
                    "swapped([Mary,Sarah],John)", "ready()", "a(John,Tom)", "b(Tom,John)"}) {
                count(model, atom, 1); nativeAnswer(q, atom, true);
            }
            count(model, "echo(Mary)", 0); count(model, "tagged(Mary)", 0);
            require(model.isCurrent(q), "permutations/constants/repeated variables do not mutate native state");
        }
    }
    private static void negativePatterns() throws Exception {
        String[][] cases={
            {"!@x pair(x,Tom) -> tagged(x);","!~tagged(John);","~pair(John,Tom)","pair(John,Tom)"},
            {"!@x repeated(x,x) -> echo(x);","!~echo(John);","~repeated(John,John)","repeated(John,John)"},
            {"!@x colored(x,red) -> taggedColor(x,blue);","!colored(John,red);","taggedColor(John,blue)","taggedColor(John,blue)"}
        };
        for (int i=0; i<cases.length; ++i) {
            Mind q=root(); IContextResults.Revision source=pin(1);
            try (DmzReplayProvenance journal=DmzReplayProvenance.begin(); TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
                accept(q,source,0,cases[i][0]); accept(q,source,1,cases[i][1]);
                count(inventory(q,false),cases[i][2],1); nativeAnswer(q,cases[i][3],i==2);
            }
        }
    }

    private static void substitutions() throws Exception {
        Mind q = root(); IContextResults.Revision source=pin(1);
        try (DmzReplayProvenance journal=DmzReplayProvenance.begin(); TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            accept(q,source,0,"!@x @y ~(pair(x,y),pair(y,x));");
            accept(q,source,1,"!pair(John,Tom);");
            accept(q,source,2,"!@x @y ~pair(x,y) -> negative(x,y);");
            DmzCurrentGroundProofInventory model=inventory(q,false);
            count(model,"~pair(Tom,John)",2);
            List<DmzProofWitnesses.Witness> proofs=model.proofs(ground("~pair(Tom,John)"),10000).witnesses;
            List<TerminalSupportCapture.Binding> first=proofs.get(0).graph.observed.steps.get(proofs.get(0).step).application.bindings;
            List<TerminalSupportCapture.Binding> second=proofs.get(1).graph.observed.steps.get(proofs.get(1).step).application.bindings;
            require(first.size()==2 && second.size()==2 && !first.get(0).value.semanticallyEquals(second.get(0).value),
                    "equal conclusion/premise/source retain different substitutions");
            DmzProofWitnesses.NoGood one=DmzProofWitnesses.NoGood.fromTyped(proofs.get(0));
            DmzSourcedProofGraph branch=model.graph.restrict(Collections.singletonList(one));
            DmzProofWitnesses.Result remaining=DmzProofWitnesses.enumerate(branch,proofs.get(0).node,10000);
            require(!remaining.truncated && remaining.witnesses.size()==1,"typed exclusion keeps the other substitution with equal endpoints and source");
            int downstream=-1;
            for(int i=0;i<branch.observed.nodes.size();++i) if(branch.observed.nodes.get(i).ground.equivalent(ground("negative(Tom,John)")))downstream=i;
            require(downstream>=0 && DmzProofWitnesses.enumerate(model.graph,downstream,10000).witnesses.size()==2
                && DmzProofWitnesses.enumerate(branch,downstream,10000).witnesses.size()==1,"typed no-good propagates to dependent continuation while other substitution survives");
            DmzProofWitnesses.Witness kept=remaining.witnesses.get(0);
            require(!one.matches(kept.graph,kept.node,kept.step,kept.source,kept.premises),"surviving substitution differs exactly");
            require(DmzProofWitnesses.enumerate(model.graph,proofs.get(0).node,10000).witnesses.size()==2,"unrestricted graph intact");
            DmzProofWitnesses.NoGood two=DmzProofWitnesses.NoGood.fromTyped(proofs.get(1));
            require(DmzProofWitnesses.enumerate(branch.restrict(Collections.singletonList(two)),proofs.get(0).node,10000).witnesses.isEmpty(),
                "both explicit typed exclusions remove both substitutions");
            List<TerminalSupportCapture.ApplicationSources> rebuilt=new ArrayList<TerminalSupportCapture.ApplicationSources>();
            for(int i=model.graph.observed.steps.size()-1;i>=0;--i) {
                TerminalSupportCapture.Application app=model.graph.observed.steps.get(i).application;
                List<TerminalSupportCapture.Binding> relocated=new ArrayList<TerminalSupportCapture.Binding>();
                for(TerminalSupportCapture.Binding binding:app.bindings) relocated.add(new TerminalSupportCapture.Binding(
                    binding.variable+1000,binding.nativeVariable,"renamed",binding.rendering,binding.value));
                Collections.reverse(relocated);
                TerminalSupportCapture.Application copy=new TerminalSupportCapture.Application(app.mind,app.rule,app.ruleOrigin,
                    app.conclusion,app.ground,relocated,app.supports);
                rebuilt.add(new TerminalSupportCapture.ApplicationSources(copy,model.graph.steps.get(i).ruleSources,model.graph.steps.get(i).primarySources));
            }
            DmzSourcedProofGraph portable=DmzSourcedProofGraph.build(rebuilt).restrict(Collections.singletonList(one));
            int portableRoot=-1;
            for(int i=0;i<portable.observed.nodes.size();++i) if(portable.observed.nodes.get(i).ground.equivalent(ground("~pair(Tom,John)")))portableRoot=i;
            require(portableRoot>=0 && DmzProofWitnesses.enumerate(portable,portableRoot,10000).witnesses.size()==1,
                "typed pattern ignores graph indices, capture indices, names and binding order");
            try {DmzProofWitnesses.NoGood.fromTyped(proofs.get(0).premises.get(0));throw new AssertionError("primary exclusion accepted");}
            catch(IllegalArgumentException expected) {++checks;}
            nativeAnswer(q,"pair(Tom,John)",false);
        }
    }

    private static void pending(boolean commit) throws Exception {
        Mind q = root(); IContextResults.Revision rules = pin(12), facts = pin(7);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            accept(q, rules, 0, "!@x @y father(x,y) -> child(y,x);");
            String baseline = DmzObservationStateFingerprint.capture(q);
            DmzCurrentGroundProofInventory provisional;
            try (TechnicalMindTransaction tx = TechnicalMindTransaction.beginIsolated(q)) {
                accept(tx.mind(), facts, 0, "!father(John,Tom);");
                DmzCurrentGroundProofInventory refused = DmzCurrentGroundProofInventory.capture(tx.mind(), 10000, false);
                require(!refused.eligible && refused.graph == null && refused.gaps.contains("pending-source-not-authorized"), "pending facts require explicit permission");
                provisional = inventory(tx.mind(), true);
                require(provisional.provisional && provisional.proofs(ground("child(Tom,John)"), 10000).provisional, "pending proof stays visibly provisional");
                count(provisional, "child(Tom,John)", 1);
                if (commit) require(tx.commit(), "owned fixture child commits");
            }
            DmzCurrentGroundProofInventory settled = inventory(q, false);
            require(!settled.provisional, "settled parent has no pending proof status");
            count(settled, "child(Tom,John)", commit ? 1 : 0);
            if (!commit) require(baseline.equals(DmzObservationStateFingerprint.capture(q)), "rollback preserves original Q");
            require(journal.settlementSnapshot().pending == 0, "child settlement releases pending sources");
        }
    }
    private static void orphan() throws Exception {
        Mind q = root(); IContextResults.Revision source = pin(1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            accept(q, source, 0, "!@x @y father(x,y) -> child(y,x);");
            accept(q, source, 1, "!father(John,Tom);");
            DmzCurrentGroundProofInventory original = inventory(q, false);
            IRule fact = null;
            for (IRule rule : q.getRules()) if ("!father(John,Tom);".equals(rule.getOrigin())) fact = rule;
            require(fact != null, "primary donor visible"); ((Rule) fact).setDeleted(true, q);
            require(!original.isCurrent(q), "changed native boundary invalidates captured proofs");
            reject(() -> original.proofs(ground("child(Tom,John)"), 10000));
            DmzCurrentGroundProofInventory orphan = DmzCurrentGroundProofInventory.capture(q, 10000, false);
            require(!orphan.eligible && orphan.graph == null && orphan.gaps.contains("generated-cache-without-reconstructed-support"),
                    "orphan generated cache cannot seed its own proof");
            ((Rule) fact).setDeleted(false, q); count(inventory(q, false), "child(Tom,John)", 1);
        }
    }
    private static void restrictions() throws Exception {
        Mind q = root(); IContextResults.Revision source = pin(1);
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
            accept(q, source, 0, "!@x p(x) -> derived(x);"); accept(q, source, 1, "!p(1);");
            DmzCurrentGroundProofInventory model = inventory(q, false);
            TerminalSupportCapture.Ground result = ground("derived(1)");
            DmzProofWitnesses.Witness witness = model.proofs(result, 10000).witnesses.get(0);
            try (DmzTerminalRestriction restriction = DmzTerminalRestriction.begin(q, witness)) {
                require(!model.isCurrent(q), "new active restriction invalidates unrestricted inventory");
                reject(() -> model.proofs(result, 10000));
                DmzCurrentGroundProofInventory refused = DmzCurrentGroundProofInventory.capture(q, 10000, false);
                require(!refused.eligible && refused.graph == null && refused.gaps.contains("active-restriction-not-qualified"),
                        "general inventory cannot bypass active branch restrictions");
            }
            count(inventory(q, false), "derived(1)", 1);
        }
    }

    private static void refusals() throws Exception {
        for (String statement : new String[]{"!@x @y p(x) -> q(y);", "!@x p(x) -> q([x]);",
                "!@x @y p(x,y), r(x,y) -> q(x,y);", "!ready() -> awake();"}) {
            Mind q = root();
            try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
                accept(q, pin(1), 0, statement);
                DmzCurrentGroundProofInventory model = DmzCurrentGroundProofInventory.capture(q, 10000, false);
                require(!model.eligible && model.graph == null && !model.gaps.isEmpty(), "unsupported rule shape is refused: " + statement);
            }
        }
        Mind unlabeled = root(); require(unlabeled.compile("!father(John,Tom);", null, false), "unlabelled native ground");
        try (DmzReplayProvenance journal = DmzReplayProvenance.begin()) {
            require(!DmzCurrentGroundProofInventory.capture(unlabeled, 10000, false).eligible, "missing source provenance fails closed");
        }
        for (boolean revisions : new boolean[]{false, true}) {
            Mind q = root(); IContextResults.Revision one = pin(1);
            try (DmzReplayProvenance journal = DmzReplayProvenance.begin(); TerminalSupportCapture capture = TerminalSupportCapture.begin(q)) {
                accept(q, one, 0, "!father(John,Tom);");
                accept(q, revisions ? new IContextResults.Revision(one.getContextId(), 2) : one, 0, "!father(Mary,Sarah);");
                DmzCurrentGroundProofInventory model = DmzCurrentGroundProofInventory.capture(q, 10000, false);
                require(!model.eligible && model.gaps.contains(revisions ? "inconsistent-context-revisions" : "source-rule-bound-to-different-inputs"),
                        "source scope cannot float revision or reuse one source rule for different atoms");
            }
        }
    }
    private static void reject(Attempt attempt) throws Exception {
        try { attempt.run(); throw new AssertionError("Expected typed proof refusal"); }
        catch (IllegalArgumentException | IllegalStateException expected) { ++checks; }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); ++checks;
    }
}
