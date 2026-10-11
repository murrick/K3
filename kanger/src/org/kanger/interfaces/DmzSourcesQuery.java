/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.interfaces;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Experimental whole-source ordered DMZ operation. Results are historical observations,
 * never replay permissions or resumable sessions. Completeness concerns this policy only. */
public final class DmzSourcesQuery {
    private DmzSourcesQuery() { }
    public enum Policy { FIXED_UNITS_THEN_ORDERED_GROUNDS_CONTEXT_UUID_RULE_ID }
    public enum Decision { ADMITTED, REUSED_PRIMARY, DECLINED_Q, KEEP_EXISTING, KEEP_CANDIDATE }
    public enum Refusal { UNSUPPORTED_DECLARATION, GROUND_LIMIT, PRODUCTION_LIMIT }
    public static final class PlanningRefusal extends IllegalArgumentException {
        private static final long serialVersionUID=1L;
        public final Refusal reason;
        public final java.util.UUID context;
        public final Long rule;
        public PlanningRefusal(Refusal reason,java.util.UUID context,Long rule,String message) {
            super(message);
            if(reason==null || (context==null)!=(rule==null))throw new IllegalArgumentException("Refusal identity");
            this.reason=reason;this.context=context;this.rule=rule;
        }
    }
    public static final class Limits {
        public final int preparation,audit,lookup,recognition,projection,application,query,depth,nodes,work,maximumVisits;
        public Limits(int preparation,int audit,int lookup,int recognition,int projection,int application,
                int query,int depth,int nodes,int work,int maximumVisits) {
            for(int phase:new int[]{preparation,audit,lookup,recognition,projection,application,query,nodes,maximumVisits})
                if(phase<1 || phase>10000)throw new IllegalArgumentException("Phase allowance must be between 1 and 10000");
            if(depth<1 || depth>16 || work<1 || work>1000000)throw new IllegalArgumentException("Bounded source traversal required");
            this.preparation=preparation;this.audit=audit;this.lookup=lookup;this.recognition=recognition;
            this.projection=projection;this.application=application;this.query=query;this.depth=depth;
            this.nodes=nodes;this.work=work;this.maximumVisits=maximumVisits;
        }
    }
    public static final class Request {
        public final DmzSourcePlan.Request sources;
        public final Policy policy;
        public final String queryStatement;
        public final Limits limits;
        public Request(DmzSourcePlan.Request sources,Policy policy,String queryStatement,Limits limits) {
            if(sources==null || policy==null || queryStatement==null || limits==null)throw new NullPointerException();
            if(queryStatement.trim().isEmpty())throw new IllegalArgumentException("Explicit DMZ query required");
            this.sources=sources;this.policy=policy;this.queryStatement=queryStatement;this.limits=limits;
        }
    }
    public static final class Input {
        public final IContextResults.Revision revision;
        public final long rule;
        public final String statement;
        public Input(IContextResults.Revision revision,long rule,String statement) {
            if(revision==null || statement==null)throw new NullPointerException();
            if(rule<0)throw new IllegalArgumentException("Source-local rule required");
            this.revision=revision;this.rule=rule;this.statement=statement;
        }
    }
    public static final class SourceInventory {
        public final IContextResults.Revision revision;
        public final int primaryRules,inspectedRows,excludedGeneratedCaches;
        public final String primaryDigest;
        public SourceInventory(IContextResults.Revision revision,int primaryRules,int inspectedRows,int caches,String digest) {
            if(revision==null || digest==null)throw new NullPointerException();
            if(primaryRules<0 || inspectedRows<primaryRules || caches<0 || !digest.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Source inventory");
            this.revision=revision;this.primaryRules=primaryRules;this.inspectedRows=inspectedRows;
            excludedGeneratedCaches=caches;primaryDigest=digest;
        }
    }
    public static final class Step {
        public final Input input;
        public final Decision decision;
        public final int variant;
        public Step(Input input,Decision decision,int variant) {
            if(input==null || decision==null)throw new NullPointerException();
            this.input=input;this.decision=decision;this.variant=variant;
        }
    }
    /** Immutable proof alternative, captured while its branch is alive. */
    public static final class Proof {
        public final Input source;
        public final String conclusion;
        public final List<Proof> premises;
        public Proof(Input source,String conclusion,List<Proof> premises) {
            if(source==null || conclusion==null)throw new NullPointerException();
            this.source=source;this.conclusion=conclusion;this.premises=copy(premises);
        }
        public String identity() {
            StringBuilder key=new StringBuilder(source.revision.getContextId()+"@"+source.revision.getRevision()
                    +":"+source.rule+":"+conclusion);
            for(Proof child:premises)key.append("[").append(child.identity()).append("]");
            return key.toString();
        }
    }
    /** User opinion; alternatives retain separate premise trees. */
    public static final class ViewOpinion {
        public final Boolean value;
        public final List<Proof> proofs;
        public final List<String> hypotheses;
        private ViewOpinion(Boolean value,List<Proof> proofs,List<String> hypotheses) {
            this.value=value;this.proofs=copy(proofs);this.hypotheses=copy(hypotheses);
        }
    }
    public static final class Opinion {
        public final List<Step> steps;
        public final DmzConditionalResult.Answer answer;
        public Opinion(List<Step> steps,DmzConditionalResult.Answer answer) {
            if(answer==null)throw new NullPointerException();
            this.steps=copy(steps);this.answer=answer;
        }
    }
    public static final class Event {
        public final int inputIndex;
        public final List<Step> prefix;
        public final DmzConditionalResult conditional;
        public Event(int inputIndex,List<Step> prefix,DmzConditionalResult conditional) {
            if(conditional==null)throw new NullPointerException();
            this.inputIndex=inputIndex;this.prefix=copy(prefix);this.conditional=conditional;
        }
    }
    public static final class Result {
        public final Request request;
        /** Freshly acquired physical Q and named source observations. */
        public final DmzSourcePlan acquisition;
        public final List<SourceInventory> sources;
        public final List<Input> candidates,productions;
        public final List<Opinion> opinions;
        public final List<ViewOpinion> viewOpinions;
        public final List<Event> events;
        public final List<Step> interruptedPrefix;
        /** Completed leaves preceding a stop remain partial historical observations. */
        public final boolean restartRequired,visitLimit;
        public final int visits,interruptedInput;
        public Result(Request request,DmzSourcePlan acquisition,List<SourceInventory> sources,
                List<Input> candidates,List<Input> productions,List<Opinion> opinions,List<Event> events,
                List<Step> interruptedPrefix,boolean restartRequired,boolean visitLimit,int visits,int interruptedInput) {
            if(request==null || acquisition==null)throw new NullPointerException();
            this.request=request;this.acquisition=acquisition;this.sources=copy(sources);
            this.candidates=copy(candidates);this.productions=copy(productions);this.opinions=copy(opinions);this.viewOpinions=views(opinions);
            this.events=copy(events);this.interruptedPrefix=copy(interruptedPrefix);
            this.restartRequired=restartRequired;this.visitLimit=visitLimit;this.visits=visits;this.interruptedInput=interruptedInput;
        }
    }
    private static List<ViewOpinion> views(List<Opinion> opinions) {
        List<ViewOpinion> result=new ArrayList<ViewOpinion>();
        for(Boolean truth:new Boolean[]{Boolean.TRUE,Boolean.FALSE,null}) {
            java.util.Map<String,Proof> proofs=new java.util.LinkedHashMap<String,Proof>();
            java.util.Set<String> hypotheses=new java.util.LinkedHashSet<String>();
            boolean found=false;
            for(Opinion opinion:opinions)if(java.util.Objects.equals(truth,opinion.answer.value)) {
                found=true;
                for(Proof proof:opinion.answer.proofs)proofs.put(proof.identity(),proof);
                hypotheses.addAll(opinion.answer.hypotheses);
            }
            if(found)result.add(new ViewOpinion(truth,new ArrayList<Proof>(proofs.values()),new ArrayList<String>(hypotheses)));
        }
        return copy(result);
    }
    private static <T> List<T> copy(List<T> values) {
        if(values==null)throw new NullPointerException();
        for(T value:values)if(value==null)throw new NullPointerException();
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
