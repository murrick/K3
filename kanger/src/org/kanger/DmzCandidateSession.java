/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.kanger.interfaces.DmzConditionalResult;
import org.kanger.interfaces.IContextResults;

/** Internal provider bridge for independent candidate trials against rebuilt Q.
 * The provider must acquire and verify physical inputs before opening this owner.
 * This is not a full multi-declaration source workflow or a public SDK session.
 */
public final class DmzCandidateSession implements AutoCloseable {
    public static final class Participant {
        public final IContextResults.Revision revision;
        public final DmzSourceShape.Observation inputs;
        public Participant(IContextResults.Revision revision,DmzSourceShape.Observation inputs) {
            if(revision==null || inputs==null)throw new NullPointerException();
            if(revision.getCommune()!=null || !revision.getCommuneMembers().isEmpty())
                throw new IllegalArgumentException("Physical source revision required");
            this.revision=revision; this.inputs=inputs;
        }
    }
    public static final class SourceRule {
        public final UUID context;
        public final long rule;
        public SourceRule(UUID context,long rule) {
            if(context==null)throw new NullPointerException("context");
            if(rule<0)throw new IllegalArgumentException("Source-local primary rule required");
            this.context=context; this.rule=rule;
        }
    }
    public static final class Limits {
        public final int preparation,audit,lookup,recognition,projection,application,query,depth,nodes,work;
        public Limits(int preparation,int audit,int lookup,int recognition,int projection,int application,
                int query,int depth,int nodes,int work) {
            for(int phase:new int[]{preparation,audit,lookup,recognition,projection,application,query,nodes})
                if(phase<1 || phase>10000)throw new IllegalArgumentException("Phase allowance must be between 1 and 10000");
            if(depth<1 || depth>16 || work<1 || work>1000000)throw new IllegalArgumentException("Bounded candidate traversal required");
            this.preparation=preparation; this.audit=audit; this.lookup=lookup; this.recognition=recognition;
            this.projection=projection; this.application=application; this.query=query; this.depth=depth; this.nodes=nodes; this.work=work;
        }
        DmzStoredRetractionTransaction.ConditionalQueryLimits traversal() {
            return new DmzStoredRetractionTransaction.ConditionalQueryLimits(recognition,projection,application,query,depth,nodes,work);
        }
    }
    public enum Status { DECLINED_Q, BRANCHING_REQUIRED, REUSED_PRIMARY, PROBED_NEW_INPUT, CONDITIONAL_RESULT }
    /** Detached observation. legacyAnswer retains the narrow nonbranching contract. */
    public static final class Result {
        public final Status status;
        public final SourceRule candidate;
        public final IContextResults.Revision authorityRevision,candidateRevision;
        public final List<IContextResults.Revision> supportRevisions,productionRevisions;
        public final String candidateStatement,queryStatement;
        public final List<SourceRule> supports,productions;
        public final DmzConditionalResult.Answer legacyAnswer;
        public final DmzConditionalResult conditional;
        private Result(DmzStoredRetractionTransaction.CandidateEvaluation evaluation,SourceRule candidate,
                String candidateStatement,String query,List<SourceRule> productions,List<SourceRule> supports,Participant authority,Map<UUID,Participant> sources) {
            status=Status.valueOf(evaluation.status.name()); this.candidate=candidate;
            this.candidateStatement=candidateStatement; queryStatement=query;
            this.supports=Collections.unmodifiableList(new ArrayList<SourceRule>(supports));
            authorityRevision=authority.revision; candidateRevision=sources.get(candidate.context).revision;
            List<IContextResults.Revision> pins=new ArrayList<IContextResults.Revision>();
            for(SourceRule support:supports)pins.add(sources.get(support.context).revision);
            supportRevisions=Collections.unmodifiableList(pins);
            this.productions=Collections.unmodifiableList(new ArrayList<SourceRule>(productions));
            List<IContextResults.Revision> productionPins=new ArrayList<IContextResults.Revision>();
            for(SourceRule production:productions)productionPins.add(sources.get(production.context).revision);
            productionRevisions=Collections.unmodifiableList(productionPins);
            legacyAnswer=answer(evaluation.query);
            conditional=evaluation.conditional==null ? null : evaluation.conditional.toContextResult();
        }
    }
    public enum SequenceDecision { ADMITTED, REUSED_PRIMARY, DECLINED_Q, KEEP_EXISTING, KEEP_CANDIDATE }
    public static final class SequenceStep {
        public final SourceRule input;
        public final SequenceDecision decision;
        public final int variant;
        private SequenceStep(SourceRule input,DmzStoredRetractionTransaction.InputVisitKind kind,int variant) {
            this.input=input;decision=SequenceDecision.valueOf(kind.name());this.variant=variant;
        }
    }
    public static final class SequenceOpinion {
        public final List<SequenceStep> steps;
        public final DmzConditionalResult.Answer answer;
        private SequenceOpinion(List<SequenceStep> steps,DmzStoredRetractionTransaction.QueryResult answer) {
            this.steps=Collections.unmodifiableList(new ArrayList<SequenceStep>(steps));this.answer=answer(answer);
        }
    }
    public static final class SequenceEvent {
        public final int inputIndex;
        public final List<SequenceStep> prefix;
        public final DmzConditionalResult conditional;
        private SequenceEvent(int index,List<SequenceStep> prefix,DmzStoredRetractionTransaction.ConditionalQueryResult value) {
            inputIndex=index;this.prefix=Collections.unmodifiableList(new ArrayList<SequenceStep>(prefix));conditional=value.toContextResult();
        }
    }
    /** Historical ordered-policy result. A stop requires a fresh full invocation, never replay of these steps. */
    public static final class SequenceResult {
        public final IContextResults.Revision authorityRevision;
        public final List<SourceRule> candidates,productions,supports;
        public final List<IContextResults.Revision> candidateRevisions,productionRevisions,supportRevisions;
        public final List<String> candidateStatements,productionStatements,supportStatements;
        public final List<SequenceOpinion> opinions;
        public final List<SequenceEvent> events;
        public final List<SequenceStep> interruptedPrefix;
        public final String queryStatement;
        public final boolean restartRequired,visitLimit;
        public final int visits,maximumVisits,interruptedInput;
        private SequenceResult(SequenceRun run) {
            authorityRevision=run.session.authority.revision;queryStatement=run.query;
            candidates=copy(run.candidates);productions=copy(run.productions);supports=copy(run.supports);
            candidateRevisions=run.session.pins(candidates);productionRevisions=run.session.pins(productions);supportRevisions=run.session.pins(supports);
            candidateStatements=run.session.statements(candidates);productionStatements=run.session.statements(productions);supportStatements=run.session.statements(supports);
            opinions=copy(run.opinions);events=copy(run.events);interruptedPrefix=copy(run.interruptedPrefix);
            restartRequired=run.restartRequired;visitLimit=run.visitLimit;visits=run.visits;maximumVisits=run.maximumVisits;interruptedInput=run.interruptedInput;
        }
    }
    private static <T> List<T> copy(List<T> values) { return Collections.unmodifiableList(new ArrayList<T>(values)); }
    private List<IContextResults.Revision> pins(List<SourceRule> selected) {
        List<IContextResults.Revision> result=new ArrayList<IContextResults.Revision>();
        for(SourceRule input:selected)result.add(external.get(input.context).revision);return copy(result);
    }
    private List<String> statements(List<SourceRule> selected) {
        List<String> result=new ArrayList<String>();
        for(SourceRule input:selected)result.add(declaration(input).statement);return copy(result);
    }
    private static final class SequenceVisitStop extends RuntimeException { private static final long serialVersionUID=1L; }
    private static final class SequenceRun {
        final DmzCandidateSession session;
        final List<SourceRule> candidates,productions,supports;
        final List<DmzSourceShape.Declaration> inputs;
        final String query;
        final Limits limits;
        final int maximumVisits;
        final List<SequenceOpinion> opinions=new ArrayList<SequenceOpinion>();
        final List<SequenceEvent> events=new ArrayList<SequenceEvent>();
        List<SequenceStep> interruptedPrefix=Collections.emptyList();
        int visits,interruptedInput=-1,proofNodes;
        boolean restartRequired,visitLimit;
        SequenceRun(DmzCandidateSession session,List<SourceRule> candidates,List<SourceRule> productions,List<SourceRule> supports,
                List<DmzSourceShape.Declaration> inputs,String query,Limits limits,int maximumVisits) {
            this.session=session;this.candidates=candidates;this.productions=productions;this.supports=supports;
            this.inputs=inputs;this.query=query;this.limits=limits;this.maximumVisits=maximumVisits;
        }
        private void countProof(org.kanger.interfaces.DmzSourcesQuery.Proof proof) {
            if(++proofNodes>limits.projection)throw new IllegalStateException("DMZ answer trees exceed the operation projection budget");
            for(org.kanger.interfaces.DmzSourcesQuery.Proof child:proof.premises)countProof(child);
        }
        void walk(DmzStoredRetractionTransaction workflow,final int index,final List<SequenceStep> prefix) throws Exception {
            if(visits==maximumVisits) {
                visitLimit=true;restartRequired=true;interruptedInput=index;interruptedPrefix=copy(prefix);throw new SequenceVisitStop();
            }
            ++visits;
            if(index==candidates.size()) {
                if(candidates.isEmpty())workflow.auditFixedInputs(limits.audit);
                DmzStoredRetractionTransaction.QueryResult answer=workflow.queryContinuationWithProofs(query,limits.audit,limits.projection);
                for(org.kanger.interfaces.DmzSourcesQuery.Proof proof:answer.proofs)countProof(proof);
                opinions.add(new SequenceOpinion(prefix,answer));return;
            }
            final SourceRule input=candidates.get(index);
            DmzStoredRetractionTransaction.ConditionalQueryResult value=workflow.visitCandidate(session.external.get(input.context).revision,
                    input.rule,inputs.get(index).groundStatement,query,limits.audit,limits.lookup,limits.traversal(),session.journal,
                    new DmzStoredRetractionTransaction.LiveBranchVisitor() {
                        public void visit(DmzStoredRetractionTransaction owner,Mind live,
                                DmzStoredRetractionTransaction.InputVisitKind kind,int variant) throws Exception {
                            List<SequenceStep> next=new ArrayList<SequenceStep>(prefix);next.add(new SequenceStep(input,kind,variant));
                            try(DmzStoredRetractionTransaction child=DmzStoredRetractionTransaction.beginBranchContinuation(owner,live,limits.preparation)) {
                                walk(child,index+1,next);
                            }
                        }
                    });
            if(value!=null) {
                events.add(new SequenceEvent(index,prefix,value));
                if(value.restartRequired || value.retryDiscovery || value.family!=null
                        && value.family.outcome!=DmzStoredRetractionTransaction.ReplayTraversalOutcome.COMPLETE)restartRequired=true;
            }
        }
    }
    private final Thread owner=Thread.currentThread();
    private final Participant authority;
    private final List<Participant> authorityComponents;
    private final Map<UUID,Participant> external;
    private final User user;
    private final Mind root,q;
    private final int preparationBudget;
    private DmzReplayProvenance journal;
    private TerminalSupportCapture capture;
    private String qState;
    private DmzReplayProvenance.SourceCheckpoint qSources;
    private boolean closed;

    /** The provider cannot promote pending physical source work to settled Q authority. */
    public static void requireSettledPhysicalQ(Mind source) {
        if(source==null || source.getTransactionLevel()!=0 || source.hasPendingTransactions())
            throw new IllegalStateException("Settled physical Q without pending children required");
    }
    public static DmzCandidateSession open(Participant authority,List<Participant> external,int preparationBudget) throws Exception {
        return open(authority,Collections.<Participant>emptyList(),external,preparationBudget);
    }
    public static DmzCandidateSession open(Participant authority,List<Participant> components,List<Participant> external,int preparationBudget) throws Exception {
        if(authority==null || components==null || external==null)throw new NullPointerException();
        java.util.Set<UUID> identities=new java.util.HashSet<UUID>();identities.add(authority.revision.getContextId());
        for(Participant component:components)if(component==null || !identities.add(component.revision.getContextId()))
            throw new IllegalArgumentException("Distinct Q components required");
        if(preparationBudget<1 || preparationBudget>10000 || external.size()>64 || components.size()>64)
            throw new IllegalArgumentException("Bounded explicit session inputs required");
        Map<UUID,Participant> selected=new LinkedHashMap<UUID,Participant>();
        for(Participant source:external) {
            if(source==null || identities.contains(source.revision.getContextId())
                    || selected.put(source.revision.getContextId(),source)!=null)
                throw new IllegalArgumentException("Unique external source Contexts required");
        }
        int rows=authority.inputs.inspectedRows;
        for(Participant component:components)rows+=component.inputs.inspectedRows;
        for(Participant source:selected.values())rows+=source.inputs.inspectedRows;
        if(rows>10000)throw new IllegalArgumentException("At most 10000 acquired source rows required");
        DmzCandidateSession session=new DmzCandidateSession(authority,components,selected,preparationBudget);
        try { session.prepare(); return session; }
        catch(Throwable failure) {
            try { session.close(); }catch(Throwable cleanup) { failure.addSuppressed(cleanup); }
            rethrow(failure); throw new AssertionError("unreachable");
        }
    }
    private DmzCandidateSession(Participant authority,List<Participant> components,Map<UUID,Participant> external,int budget) throws Exception {
        this.authority=authority;this.authorityComponents=copy(components); this.external=Collections.unmodifiableMap(external); preparationBudget=budget;
        user=new User(); root=new Mind(user); user.setCurrentMind(root);
        q=Mind.contextConnectionLayer(root); user.setCurrentMind(q);
    }
    private void prepare() throws Exception {
        journal=DmzReplayProvenance.begin(); capture=TerminalSupportCapture.begin();
        try(DmzReplayProvenance.Preparation preparation=DmzReplayProvenance.preparation(q)) {
            List<Participant> all=new ArrayList<Participant>();all.add(authority);all.addAll(authorityComponents);
            for(Participant participant:all)for(DmzSourceShape.Declaration input:participant.inputs.declarations) {
                int priorBindings=journal.snapshot().size();
                if(!Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,participant.revision,input.sourceRule,
                        DmzReplayProvenance.Authority.EXTERNAL,input.statement)))
                    throw new IllegalStateException("Q primary reconstruction refused");
                if(input.checkedGround!=null)input.checkedGround.verifyReplay(q,journal,participant.revision,
                        DmzReplayProvenance.Authority.EXTERNAL,priorBindings);
            }
            preparation.accept();
        }
        // Physical Q caches are observations to verify, never replay inputs or proof seeds.
        List<Participant> authorities=new ArrayList<Participant>();authorities.add(authority);authorities.addAll(authorityComponents);
        for(Participant participant:authorities)if(participant.inputs.unsupportedGenerated!=0)throw new IllegalStateException("Unsupported physical Q generated cache");
        DmzCurrentUnaryProofInventory rebuilt=DmzCurrentUnaryProofInventory.capture(q,preparationBudget,false);
        if(!rebuilt.eligible)throw new IllegalStateException("Reconstructed Q inventory unavailable: "+rebuilt.gaps);
        DmzCurrentGroundProofInventory typed=DmzCurrentGroundProofInventory.capture(q,preparationBudget,false);
        if(!typed.eligible)throw new IllegalStateException("Reconstructed Q typed inventory unavailable: "+typed.gaps);
        for(Participant participant:authorities)for(TerminalSupportCapture.Ground ground:participant.inputs.cachedGrounds) {
            DmzCurrentUnaryProofInventory.Proofs proofs=rebuilt.proofs(ground,Collections.<DmzProofWitnesses.NoGood>emptyList(),preparationBudget);
            if(proofs.truncated || proofs.witnesses.isEmpty())throw new IllegalStateException("Physical Q cache has no complete reconstructed support");
            DmzCurrentGroundProofInventory.Proofs typedProofs=typed.proofs(ground,preparationBudget);
            if(typedProofs.truncated || typedProofs.witnesses.isEmpty())
                throw new IllegalStateException("Physical Q cache has no reconstructed typed support");
        }
        // Validate full sourced Q/cache support before returning a usable session.
        try(DmzStoredRetractionTransaction audit=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,preparationBudget)) {}
        qState=DmzObservationStateFingerprint.capture(q); qSources=DmzReplayProvenance.sourceCheckpoint(q);
    }
    private void requireScope() {
        if(closed || Thread.currentThread()!=owner)throw new IllegalStateException("Active owning DMZ session required");
        if(journal!=null)journal.requireActiveJournal();
        if(capture!=null)capture.requireActive();
    }
    private void requireCurrent() throws Exception {
        requireScope();
        if(!qState.equals(DmzObservationStateFingerprint.capture(q)) || !qSources.isCurrent(q))
            throw new IllegalStateException("Rebuilt Q session boundary changed");
    }
    /** No native owner escapes. Each call starts from the same complete Q. */
    public DmzConditionalResult.Answer queryQ(String statement,int auditBudget) throws Exception {
        requireCurrent();
        try(DmzStoredRetractionTransaction workflow=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,preparationBudget)) {
            return answer(workflow.queryContinuationResult(statement,auditBudget));
        } finally { requireCurrent(); }
    }
    /** Explicit primary grounds only. Supports are prerequisites, not automatically branched inputs. */
    public Result evaluate(SourceRule candidate,List<SourceRule> supports,String query,Limits limits) throws Exception {
        return evaluateWithProductions(candidate,Collections.<SourceRule>emptyList(),supports,query,limits);
    }
    /** Explicit unit productions and grounds are prerequisites to this independent candidate trial. */
    public Result evaluateWithProductions(SourceRule candidate,List<SourceRule> productions,List<SourceRule> supports,
            String query,Limits limits) throws Exception {
        requireCurrent();
        if(candidate==null || productions==null || supports==null || limits==null)throw new NullPointerException();
        if(productions.size()>64 || supports.size()>64 || productions.size()+supports.size()>64)
            throw new IllegalArgumentException("At most 64 explicit production/ground prerequisites required");
        if(query==null || !query.matches("\\?~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
            throw new IllegalArgumentException("Signed unary integer query required");
        DmzSourceShape.Declaration input=ground(candidate);
        List<SourceRule> selected=new ArrayList<SourceRule>(supports);
        List<DmzSourceShape.Declaration> prerequisites=new ArrayList<DmzSourceShape.Declaration>();
        java.util.Set<String> distinct=new java.util.HashSet<String>(); distinct.add(key(candidate));
        List<SourceRule> selectedProductions=new ArrayList<SourceRule>(productions);
        List<DmzSourceShape.Declaration> units=new ArrayList<DmzSourceShape.Declaration>();
        for(SourceRule production:selectedProductions) {
            if(production==null || !distinct.add(key(production)))throw new IllegalArgumentException("Distinct production, support and candidate occurrences required");
            DmzSourceShape.Declaration declaration=declaration(production);
            if(declaration.unitStatement==null)throw new IllegalArgumentException("Acquired signed unary shared-variable implication required");
            units.add(declaration);
        }
        for(SourceRule support:selected) {
            if(support==null || !distinct.add(key(support)))throw new IllegalArgumentException("Distinct support and candidate occurrences required");
            prerequisites.add(ground(support));
        }
        Participant source=external.get(candidate.context);
        try(DmzStoredRetractionTransaction workflow=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,limits.preparation)) {
            for(int i=0;i<selectedProductions.size();++i) {
                SourceRule production=selectedProductions.get(i);
                Boolean accepted=workflow.acceptUnitProduction(journal,external.get(production.context).revision,units.get(i),limits.preparation);
                if(!Boolean.TRUE.equals(accepted))
                    throw new IllegalStateException("Explicit unit production prerequisite was not accepted: "+units.get(i).unitStatement+" outcome="+accepted);
            }
            for(int i=0;i<selected.size();++i) {
                SourceRule support=selected.get(i);
                if(!Boolean.TRUE.equals(workflow.accept(external.get(support.context).revision,support.rule,
                        DmzReplayProvenance.Authority.EXTERNAL,prerequisites.get(i).groundStatement)))
                    throw new IllegalStateException("Explicit candidate support was not accepted");
            }
            return new Result(workflow.evaluateCandidate(source.revision,candidate.rule,input.groundStatement,query,
                    limits.audit,limits.lookup,limits.traversal()),candidate,input.groundStatement,query,selectedProductions,selected,authority,external);
        } finally { requireCurrent(); }
    }
    /** Up to sixteen ordered acquired grounds; explicit prerequisites are fixed for this operation only. */
    public SequenceResult evaluateSequence(List<SourceRule> candidates,List<SourceRule> productions,List<SourceRule> supports,
            String query,Limits limits,int maximumVisits) throws Exception {
        requireCurrent();
        if(candidates==null || productions==null || supports==null || limits==null)throw new NullPointerException();
        if(candidates.size()>DmzSourceDispatchPlan.MAXIMUM_GROUNDS || productions.size()>64 || supports.size()>64
                || productions.size()+supports.size()>64 || maximumVisits<1 || maximumVisits>10000)
            throw new IllegalArgumentException("Bounded ordered candidates, prerequisites and visits required");
        if(query==null || !query.matches("\\?~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
            throw new IllegalArgumentException("Signed unary integer query required");
        List<SourceRule> ordered=new ArrayList<SourceRule>(candidates),units=new ArrayList<SourceRule>(productions),grounds=new ArrayList<SourceRule>(supports);
        java.util.Set<String> distinct=new java.util.HashSet<String>();
        List<DmzSourceShape.Declaration> inputs=new ArrayList<DmzSourceShape.Declaration>();
        for(SourceRule input:ordered) {
            if(input==null || !distinct.add(key(input)))throw new IllegalArgumentException("Distinct ordered input occurrences required");
            inputs.add(ground(input));
        }
        List<DmzSourceShape.Declaration> unitInputs=new ArrayList<DmzSourceShape.Declaration>(),groundInputs=new ArrayList<DmzSourceShape.Declaration>();
        for(SourceRule input:units) {
            if(input==null || !distinct.add(key(input)))throw new IllegalArgumentException("Distinct prerequisite occurrences required");
            DmzSourceShape.Declaration declaration=declaration(input);
            if(declaration.unitStatement==null)throw new IllegalArgumentException("Acquired signed unary shared-variable implication required");
            unitInputs.add(declaration);
        }
        for(SourceRule input:grounds) {
            if(input==null || !distinct.add(key(input)))throw new IllegalArgumentException("Distinct prerequisite occurrences required");
            groundInputs.add(ground(input));
        }
        SequenceRun run=new SequenceRun(this,ordered,units,grounds,inputs,query,limits,maximumVisits);
        try(DmzStoredRetractionTransaction workflow=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,limits.preparation)) {
            for(int n=0;n<units.size();++n)if(!Boolean.TRUE.equals(workflow.acceptUnitProduction(journal,
                    external.get(units.get(n).context).revision,unitInputs.get(n),limits.preparation)))
                throw new IllegalStateException("Explicit sequence production prerequisite was not accepted");
            for(int n=0;n<grounds.size();++n)if(!Boolean.TRUE.equals(workflow.accept(external.get(grounds.get(n).context).revision,
                    grounds.get(n).rule,DmzReplayProvenance.Authority.EXTERNAL,groundInputs.get(n).groundStatement)))
                throw new IllegalStateException("Explicit sequence ground prerequisite was not accepted");
            try { run.walk(workflow,0,Collections.<SequenceStep>emptyList()); }
            catch(SequenceVisitStop bounded) { /* Every live child has rolled back; observations require a fresh invocation. */ }
            return new SequenceResult(run);
        } finally { requireCurrent(); }
    }
    public static final class SourcesResult {
        public final DmzSourceDispatchPlan plan;
        public final SequenceResult sequence;
        private SourcesResult(DmzSourceDispatchPlan plan,SequenceResult sequence) { this.plan=plan;this.sequence=sequence; }
    }
    /** Reviewable deterministic coverage of all acquired active primary declarations; no native trial opens. */
    public DmzSourceDispatchPlan planSources() throws Exception {
        requireCurrent();
        return DmzSourceDispatchPlan.prepare(authority,new ArrayList<Participant>(external.values()));
    }
    /** Fixed acquired units followed by every acquired ground; no unsupported or excess primary is skipped. */
    public SourcesResult evaluateSources(String query,Limits limits,int maximumVisits) throws Exception {
        DmzSourceDispatchPlan plan=planSources();
        return new SourcesResult(plan,evaluateSequence(plan.candidates,plan.productions,Collections.<SourceRule>emptyList(),query,limits,maximumVisits));
    }
    private static String key(SourceRule rule) { return rule.context.toString()+":"+rule.rule; }
    private DmzSourceShape.Declaration declaration(SourceRule selected) {
        Participant source=external.get(selected.context);
        if(source==null)throw new IllegalArgumentException("Selected external Context required");
        for(DmzSourceShape.Declaration input:source.inputs.declarations)
            if(input.sourceRule==selected.rule)return input;
        throw new IllegalArgumentException("Acquired source-local primary rule required");
    }
    private DmzSourceShape.Declaration ground(SourceRule selected) {
        DmzSourceShape.Declaration input=declaration(selected);
        if(input.groundStatement==null)throw new IllegalArgumentException("Only acquired signed unary integer primary grounds can be dispatched");
        return input;
    }
    private static DmzConditionalResult.Answer answer(DmzStoredRetractionTransaction.QueryResult value) {
        return value==null ? null : new DmzConditionalResult.Answer(
                DmzConditionalResult.CandidateMode.valueOf(value.candidateMode.name()),value.value,value.hypotheses,value.hypothesisAssertions,value.proofs);
    }
    @Override public void close() throws Exception {
        if(closed)return;
        requireScope();
        Throwable failure=null;
        try { root.discardEphemeral(q); user.setCurrentMind(root); }catch(Throwable problem) { failure=problem; }
        try { if(capture!=null)capture.close(); }catch(Throwable problem) { if(failure==null)failure=problem; else failure.addSuppressed(problem); }
        try { if(journal!=null)journal.close(); }catch(Throwable problem) { if(failure==null)failure=problem; else failure.addSuppressed(problem); }
        closed=true;
        if(failure!=null)rethrow(failure);
    }
    private static void rethrow(Throwable failure) throws Exception {
        if(failure instanceof Exception)throw (Exception)failure;
        if(failure instanceof Error)throw (Error)failure;
        throw new AssertionError(failure);
    }
}
