/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.util.ArrayList;
import java.util.List;
import org.kanger.Mind;
import org.kanger.DmzCandidateSession;
import org.kanger.DmzSourceShape;
import org.kanger.interfaces.DmzConditionalResult;
import org.kanger.interfaces.DmzSourcePlan;

/** Provider-owned internal bridge, with a live physical Q input boundary. */
final class DmzSourceSession implements AutoCloseable {
    final DmzSourcePlan plan;
    private final DB provider;
    private final Mind physicalQ;
    private final Thread owner=Thread.currentThread();
    private final DmzCandidateSession core;
    private final DmzSourceShape.Observation inputs;
    private final List<ContextConnection> topology;
    private boolean closed;

    static DmzSourceSession open(DB provider,Mind q,DmzSourcePreflight.Inputs acquired,int budget) throws Exception {
        List<DmzCandidateSession.Participant> external=new ArrayList<DmzCandidateSession.Participant>();
        for(int i=0;i<acquired.external.size();++i)
            external.add(new DmzCandidateSession.Participant(acquired.plan.external.get(i).revision,acquired.external.get(i)));
        List<DmzCandidateSession.Participant> components=new ArrayList<DmzCandidateSession.Participant>();
        for(int i=0;i<acquired.components.size();++i)
            components.add(new DmzCandidateSession.Participant(acquired.plan.authorityComponents.get(i).revision,acquired.components.get(i)));
        DmzCandidateSession core=DmzCandidateSession.open(
                new DmzCandidateSession.Participant(acquired.plan.authority.revision,acquired.authority),components,external,budget);
        DmzSourceSession session=new DmzSourceSession(provider,q,acquired.plan,acquired.authority,core);
        try { session.requireCurrent(); return session; }
        catch(Throwable failure) {
            try { session.close(); }catch(Throwable cleanup) { failure.addSuppressed(cleanup); }
            if(failure instanceof Exception)throw (Exception)failure;
            if(failure instanceof Error)throw (Error)failure;
            throw new AssertionError(failure);
        }
    }
    private DmzSourceSession(DB provider,Mind physicalQ,DmzSourcePlan plan,DmzSourceShape.Observation inputs,DmzCandidateSession core) {
        this.provider=provider;this.topology=provider.dmzTopology(); this.physicalQ=physicalQ; this.plan=plan; this.inputs=inputs; this.core=core;
    }
    private void requireCurrent() throws Exception {
        if(closed || Thread.currentThread()!=owner)throw new IllegalStateException("Active owning provider DMZ session required");
        if(((org.kanger.User)physicalQ.getUser()).getData()!=provider || physicalQ.getUser().getCurrentMind()!=physicalQ
                || !plan.authority.revision.getContextId().equals(provider.getContextId())
                || plan.authority.revision.getRevision()!=provider.getRevision()
                || physicalQ.getTransactionLevel()!=plan.qTransactionLevel)
            throw new IllegalStateException("Physical Q session boundary changed");
        if(!topology.equals(provider.dmzTopology()))throw new IllegalStateException("DMZ working topology changed");
        DmzCandidateSession.requireSettledPhysicalQ(physicalQ);
        DmzSourceShape.Observation now=DmzSourceShape.inspect(physicalQ,plan.maximumRows);
        if(!inputs.isCurrent(physicalQ) || now.inspectedRows!=plan.authority.inspectedRows || !now.primaryDigest.equals(plan.authority.primaryDigest))
            throw new IllegalStateException("Physical Q declarations changed during DMZ session");
    }
    DmzConditionalResult.Answer queryQ(String statement,int auditBudget) throws Exception {
        synchronized(provider) {
            requireCurrent();
            try { return core.queryQ(statement,auditBudget); }
            finally { requireCurrent(); }
        }
    }
    DmzCandidateSession.Result evaluate(DmzCandidateSession.SourceRule candidate,
            List<DmzCandidateSession.SourceRule> supports,String query,DmzCandidateSession.Limits limits) throws Exception {
        synchronized(provider) {
            requireCurrent();
            try { return core.evaluate(candidate,supports,query,limits); }
            finally { requireCurrent(); }
        }
    }
    DmzCandidateSession.Result evaluateWithProductions(DmzCandidateSession.SourceRule candidate,
            List<DmzCandidateSession.SourceRule> productions,List<DmzCandidateSession.SourceRule> supports,
            String query,DmzCandidateSession.Limits limits) throws Exception {
        synchronized(provider) {
            requireCurrent();
            try { return core.evaluateWithProductions(candidate,productions,supports,query,limits); }
            finally { requireCurrent(); }
        }
    }
    DmzCandidateSession.SequenceResult evaluateSequence(List<DmzCandidateSession.SourceRule> candidates,
            List<DmzCandidateSession.SourceRule> productions,List<DmzCandidateSession.SourceRule> supports,
            String query,DmzCandidateSession.Limits limits,int maximumVisits) throws Exception {
        synchronized(provider) {
            requireCurrent();
            try { return core.evaluateSequence(candidates,productions,supports,query,limits,maximumVisits); }
            finally { requireCurrent(); }
        }
    }
    org.kanger.DmzSourceDispatchPlan planSources() throws Exception {
        synchronized(provider) { requireCurrent();try { return core.planSources(); }finally { requireCurrent(); } }
    }
    DmzCandidateSession.SourcesResult evaluateSources(String query,DmzCandidateSession.Limits limits,int maximumVisits) throws Exception {
        synchronized(provider) { requireCurrent();try { return core.evaluateSources(query,limits,maximumVisits); }finally { requireCurrent(); } }
    }
    @Override public void close() throws Exception {
        if(closed)return;
        if(Thread.currentThread()!=owner)throw new IllegalStateException("Provider DMZ session must close on its owning thread");
        core.close(); closed=true;
    }
}
