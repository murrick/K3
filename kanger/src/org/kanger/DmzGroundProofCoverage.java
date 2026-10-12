/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;

/** Read-only typed unit-route audit against a native rejected operation.
 * Reconstructed substitutions are expectations, never native cut certificates.
 */
final class DmzGroundProofCoverage {
    static final class Result {
        final boolean supported, current, covered, truncated;
        final boolean complete = false;
        final int expectedSteps;
        final List<String> gaps;
        Result(boolean supported, boolean current, boolean truncated, int expected, List<String> gaps) {
            this.supported=supported; this.current=current; this.truncated=truncated; expectedSteps=expected;
            this.gaps=Collections.unmodifiableList(new ArrayList<String>(gaps));
            covered=supported && current && !truncated && gaps.isEmpty();
        }
    }
    private final DmzCollisionProofGuard guard;
    private final DmzCurrentGroundProofInventory inventory;
    private final int limit;
    private DmzGroundProofCoverage(TerminalSupportCapture capture, Mind target, int budget) throws Exception {
        guard=DmzCollisionProofGuard.beforeOperation(capture,target);
        inventory=DmzCurrentGroundProofInventory.capture(target,budget,true); limit=budget;
    }
    static DmzGroundProofCoverage beforeOperation(TerminalSupportCapture capture, Mind target, int budget) throws Exception {
        return new DmzGroundProofCoverage(capture,target,budget);
    }
    Result audit(DmzProvisionalCollisionProof proof, Mind target) throws Exception {
        boolean current=guard.isCurrent(proof,target);
        List<String> gaps=new ArrayList<String>(inventory.gaps);
        if (!current) gaps.add("proof-baseline-not-current");
        if (!inventory.eligible || !current)
            return new Result(inventory.eligible,current,inventory.truncated || proof!=null && proof.truncated,0,gaps);
        // The operation must have restored the exact pre-operation typed/source boundary.
        if (!inventory.isCurrent(target)) {
            gaps.add("typed-inventory-not-current"); return new Result(true,false,false,0,gaps);
        }
        DmzSourcedProofGraph expected=inventory.graph, actual=proof.graph;
        List<TerminalSupportCapture.Ground> relevant=new ArrayList<TerminalSupportCapture.Ground>();
        relevant.add(proof.event.left.nodes.get(proof.event.left.root).ground);
        relevant.add(proof.event.right.nodes.get(proof.event.right.root).ground);
        int work=0, count=0;
        boolean changed;
        do {
            changed=false;
            for (DmzObservedProofGraph.Step step:expected.observed.steps) {
                if (++work>limit) return new Result(true,true,true,count,gaps);
                if (!has(relevant,step.application.ground)) continue;
                for (int p:step.premises) {
                    TerminalSupportCapture.Ground atom=expected.observed.nodes.get(p).ground;
                    if (!has(relevant,atom)) { relevant.add(atom); changed=true; }
                }
            }
        } while(changed);
        for (TerminalSupportCapture.Ground atom:relevant) {
            boolean found=false;
            for (DmzObservedProofGraph.Node node:expected.observed.nodes) {
                if (++work>limit) return new Result(true,true,true,count,gaps);
                found |= node.ground.equivalent(atom);
            }
            if (!found) gaps.add("root-outside-typed-closure");
        }
        for (int i=0;i<expected.observed.steps.size();++i) {
            DmzObservedProofGraph.Step step=expected.observed.steps.get(i);
            if (!has(relevant,step.application.ground)) continue;
            DmzSourcedProofGraph.Step sources=expected.steps.get(i);
            if (step.application.rule<0) {
                List<DmzReplayProvenance.Binding> found=new ArrayList<DmzReplayProvenance.Binding>();
                for (int j=0;j<actual.observed.steps.size();++j) {
                    DmzObservedProofGraph.Step observed=actual.observed.steps.get(j);
                    for (int p=0;p<observed.premises.size();++p) {
                        if (++work>limit) return new Result(true,true,true,count,gaps);
                        if (observed.application.supports.get(p).primary && actual.observed.nodes.get(observed.premises.get(p)).ground.equivalent(step.application.ground))
                            found.addAll(actual.steps.get(j).primarySources.get(p));
                    }
                }
                if (!found.containsAll(sources.primarySources.get(0))) gaps.add("missing-typed-primary-alternative:"+step.application.ground.predicate);
                continue;
            }
            ++count;
            // Source alternatives may be spread across observations; each exact source must have the exact substitution.
            for (DmzReplayProvenance.Binding source:sources.ruleSources) {
                boolean found=false;
                for (int j=0;j<actual.observed.steps.size();++j) {
                    if (++work>limit) return new Result(true,true,true,count,gaps);
                    DmzObservedProofGraph.Step observed=actual.observed.steps.get(j);
                    if (!actual.steps.get(j).ruleSources.contains(source)
                            || !step.application.ground.equivalent(observed.application.ground)
                            || observed.premises.size()!=1 || !expected.observed.nodes.get(step.premises.get(0)).ground.equivalent(
                                actual.observed.nodes.get(observed.premises.get(0)).ground)) continue;
                    if (bindings(step.application.bindings,observed.application.bindings)
                            && (!step.application.supports.get(0).primary
                                || observed.application.supports.get(0).primary && actual.steps.get(j).primarySources.get(0).containsAll(sources.primarySources.get(0))))
                        { found=true; break; }
                }
                if (!found) gaps.add("missing-typed-native-application:"+source.nativeRule+":"+step.application.ground.predicate);
            }
        }
        return new Result(true,true,false,count,gaps);
    }
    private static boolean bindings(List<TerminalSupportCapture.Binding> expected,List<TerminalSupportCapture.Binding> actual) {
        if (expected.size()!=actual.size()) return false;
        Set<Long> seen=new HashSet<Long>();
        for (TerminalSupportCapture.Binding binding:actual)
            if (binding.nativeVariable<0 || !seen.add(binding.nativeVariable)) return false;
        for (TerminalSupportCapture.Binding wanted:expected) {
            boolean found=false;
            for (TerminalSupportCapture.Binding binding:actual)
                found |= binding.nativeVariable==wanted.nativeVariable && binding.value.semanticallyEquals(wanted.value);
            if (!found) return false;
        }
        return true;
    }
    private static boolean has(List<TerminalSupportCapture.Ground> atoms,TerminalSupportCapture.Ground atom) {
        for (TerminalSupportCapture.Ground existing:atoms) if (existing.equivalent(atom)) return true;
        return false;
    }
}
