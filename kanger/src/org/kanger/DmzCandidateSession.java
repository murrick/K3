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
        public final List<IContextResults.Revision> supportRevisions;
        public final String candidateStatement,queryStatement;
        public final List<SourceRule> supports;
        public final DmzConditionalResult.Answer legacyAnswer;
        public final DmzConditionalResult conditional;
        private Result(DmzStoredRetractionTransaction.CandidateEvaluation evaluation,SourceRule candidate,
                String candidateStatement,String query,List<SourceRule> supports,Participant authority,Map<UUID,Participant> sources) {
            status=Status.valueOf(evaluation.status.name()); this.candidate=candidate;
            this.candidateStatement=candidateStatement; queryStatement=query;
            this.supports=Collections.unmodifiableList(new ArrayList<SourceRule>(supports));
            authorityRevision=authority.revision; candidateRevision=sources.get(candidate.context).revision;
            List<IContextResults.Revision> pins=new ArrayList<IContextResults.Revision>();
            for(SourceRule support:supports)pins.add(sources.get(support.context).revision);
            supportRevisions=Collections.unmodifiableList(pins);
            legacyAnswer=answer(evaluation.query);
            conditional=evaluation.conditional==null ? null : evaluation.conditional.toContextResult();
        }
    }
    private final Thread owner=Thread.currentThread();
    private final Participant authority;
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
        if(authority==null || external==null)throw new NullPointerException();
        if(preparationBudget<1 || preparationBudget>10000 || external.size()>64)
            throw new IllegalArgumentException("Bounded explicit session inputs required");
        Map<UUID,Participant> selected=new LinkedHashMap<UUID,Participant>();
        for(Participant source:external) {
            if(source==null || source.revision.getContextId().equals(authority.revision.getContextId())
                    || selected.put(source.revision.getContextId(),source)!=null)
                throw new IllegalArgumentException("Unique external source Contexts required");
        }
        int rows=authority.inputs.inspectedRows;
        for(Participant source:selected.values())rows+=source.inputs.inspectedRows;
        if(rows>10000)throw new IllegalArgumentException("At most 10000 acquired source rows required");
        DmzCandidateSession session=new DmzCandidateSession(authority,selected,preparationBudget);
        try { session.prepare(); return session; }
        catch(Throwable failure) {
            try { session.close(); }catch(Throwable cleanup) { failure.addSuppressed(cleanup); }
            rethrow(failure); throw new AssertionError("unreachable");
        }
    }
    private DmzCandidateSession(Participant authority,Map<UUID,Participant> external,int budget) throws Exception {
        this.authority=authority; this.external=Collections.unmodifiableMap(external); preparationBudget=budget;
        user=new User(); root=new Mind(user); user.setCurrentMind(root);
        q=Mind.contextConnectionLayer(root); user.setCurrentMind(q);
    }
    private void prepare() throws Exception {
        journal=DmzReplayProvenance.begin(); capture=TerminalSupportCapture.begin();
        try(DmzReplayProvenance.Preparation preparation=DmzReplayProvenance.preparation(q)) {
            for(DmzSourceShape.Declaration input:authority.inputs.declarations)
                if(!Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,authority.revision,input.sourceRule,
                        DmzReplayProvenance.Authority.EXTERNAL,input.statement)))
                    throw new IllegalStateException("Q primary reconstruction refused");
            preparation.accept();
        }
        // Physical Q caches are observations to verify, never replay inputs or proof seeds.
        if(authority.inputs.unsupportedGenerated!=0)throw new IllegalStateException("Unsupported physical Q generated cache");
        DmzCurrentUnaryProofInventory rebuilt=DmzCurrentUnaryProofInventory.capture(q,preparationBudget,false);
        if(!rebuilt.eligible)throw new IllegalStateException("Reconstructed Q inventory unavailable: "+rebuilt.gaps);
        for(TerminalSupportCapture.Ground ground:authority.inputs.cachedGrounds) {
            DmzCurrentUnaryProofInventory.Proofs proofs=rebuilt.proofs(ground,Collections.<DmzProofWitnesses.NoGood>emptyList(),preparationBudget);
            if(proofs.truncated || proofs.witnesses.isEmpty())throw new IllegalStateException("Physical Q cache has no complete reconstructed support");
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
        requireCurrent();
        if(candidate==null || supports==null || limits==null)throw new NullPointerException();
        if(supports.size()>64)throw new IllegalArgumentException("At most 64 explicit support grounds required");
        if(query==null || !query.matches("\\?~?[A-Za-z_][A-Za-z_0-9]*\\([+-]?[0-9]+\\);"))
            throw new IllegalArgumentException("Signed unary integer query required");
        DmzSourceShape.Declaration input=ground(candidate);
        List<SourceRule> selected=new ArrayList<SourceRule>(supports);
        List<DmzSourceShape.Declaration> prerequisites=new ArrayList<DmzSourceShape.Declaration>();
        java.util.Set<String> distinct=new java.util.HashSet<String>(); distinct.add(key(candidate));
        for(SourceRule support:selected) {
            if(support==null || !distinct.add(key(support)))throw new IllegalArgumentException("Distinct support and candidate occurrences required");
            prerequisites.add(ground(support));
        }
        Participant source=external.get(candidate.context);
        try(DmzStoredRetractionTransaction workflow=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,limits.preparation)) {
            for(int i=0;i<selected.size();++i) {
                SourceRule support=selected.get(i);
                if(!Boolean.TRUE.equals(workflow.accept(external.get(support.context).revision,support.rule,
                        DmzReplayProvenance.Authority.EXTERNAL,prerequisites.get(i).groundStatement)))
                    throw new IllegalStateException("Explicit candidate support was not accepted");
            }
            return new Result(workflow.evaluateCandidate(source.revision,candidate.rule,input.groundStatement,query,
                    limits.audit,limits.lookup,limits.traversal()),candidate,input.groundStatement,query,selected,authority,external);
        } finally { requireCurrent(); }
    }
    private static String key(SourceRule rule) { return rule.context.toString()+":"+rule.rule; }
    private DmzSourceShape.Declaration ground(SourceRule selected) {
        Participant source=external.get(selected.context);
        if(source==null)throw new IllegalArgumentException("Selected external Context required");
        for(DmzSourceShape.Declaration input:source.inputs.declarations)
            if(input.sourceRule==selected.rule) {
                if(input.groundStatement==null)throw new IllegalArgumentException("Only acquired signed unary integer primary grounds can be dispatched");
                return input;
            }
        throw new IllegalArgumentException("Acquired source-local primary rule required");
    }
    private static DmzConditionalResult.Answer answer(DmzStoredRetractionTransaction.QueryResult value) {
        return value==null ? null : new DmzConditionalResult.Answer(
                DmzConditionalResult.CandidateMode.valueOf(value.candidateMode.name()),value.value,value.hypotheses,value.hypothesisAssertions);
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
