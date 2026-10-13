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
    public enum RestrictionStatus { NOT_REQUESTED, COMPLETE, NEXT_CONFLICT, STOPPED_UNCOVERED }
    public enum ProjectionPhase { CONFLICT_EVIDENCE, QUERY_ANSWER }
    /** Bounded detached projection refusal; does not report the full required proof size. */
    public static final class ProjectionRefusal extends IllegalStateException {
        private static final long serialVersionUID=1L;
        public final ProjectionPhase phase;
        public final int completedChoices,projectionLimit,restrictionBudget,allowance;
        public ProjectionRefusal(ProjectionPhase phase,int completedChoices,int projectionLimit,int restrictionBudget) {
            super("Detached source proof budget exceeded; phase="+phase+"; completedChoices="+completedChoices
                    +"; projection="+projectionLimit+"; restriction-budget="+restrictionBudget
                    +"; allowance="+Math.min(projectionLimit,restrictionBudget));
            if(phase==null || completedChoices<0 || completedChoices>128 || projectionLimit<1 || projectionLimit>10000
                    || restrictionBudget<1 || restrictionBudget>10000)throw new IllegalArgumentException("Bounded projection refusal required");
            this.phase=phase;this.completedChoices=completedChoices;this.projectionLimit=projectionLimit;
            this.restrictionBudget=restrictionBudget;this.allowance=Math.min(projectionLimit,restrictionBudget);
        }
    }
    /** One covered historical pair of proofs for an exact opposite signed ground. */
    public static final class RestrictionConflict {
        private static final java.util.regex.Pattern GROUND=java.util.regex.Pattern.compile("!(~?)([A-Za-z_][A-Za-z_0-9]*)\\(([+-]?[0-9]+)\\);");
        public final Proof positive,negative;
        public RestrictionConflict(Proof positive,Proof negative) {
            if(positive==null || negative==null)throw new NullPointerException();
            java.util.regex.Matcher a=GROUND.matcher(positive.conclusion),b=GROUND.matcher(negative.conclusion);
            if(!a.matches() || !b.matches() || !a.group(1).isEmpty() || !b.group(1).equals("~")
                    || !a.group(2).equals(b.group(2)) || !new java.math.BigInteger(a.group(3)).equals(new java.math.BigInteger(b.group(3))))
                throw new IllegalArgumentException("Exact opposite signed proof conclusions required");
            this.positive=positive;this.negative=negative;
        }
        public String identity() { return positive.identity()+"<>"+negative.identity(); }
        public boolean contains(String identity) { return positive.identity().equals(identity) || negative.identity().equals(identity); }
    }
    /** Historical alternatives and the exact choice made at one native conflict. */
    public static final class RestrictionStep {
        public final int number;
        public final Proof selected;
        public final List<Proof> alternatives;
        public final RestrictionStatus after;
        public final List<RestrictionConflict> conflicts;
        public RestrictionStep(int number,Proof selected,List<Proof> alternatives,RestrictionStatus after) {
            this(number,selected,alternatives,after,Collections.<RestrictionConflict>emptyList());
        }
        public RestrictionStep(int number,Proof selected,List<Proof> alternatives,RestrictionStatus after,List<RestrictionConflict> conflicts) {
            if(number<1 || number>128 || selected==null || after==null || after==RestrictionStatus.NOT_REQUESTED)
                throw new IllegalArgumentException("Exact historical restriction step required");
            this.number=number;this.selected=selected;this.alternatives=copy(alternatives);this.after=after;
            this.conflicts=copy(conflicts);
            java.util.Set<String> identities=new java.util.HashSet<String>();
            for(Proof proof:this.alternatives)if(!identities.add(proof.identity()))
                throw new IllegalArgumentException("Distinct historical alternatives required");
            if(!identities.contains(selected.identity()))throw new IllegalArgumentException("Selected proof must be an offered alternative");
            requireConflictMembers(this.alternatives,this.conflicts);
        }
    }
    /** Detached summary of an accumulated restriction workflow; carries no native owner or resume token. */
    public static final class RestrictionOutcome {
        public final RestrictionStatus status;
        public final int choices;
        /** Historical proof alternatives; replay the whole request to validate an identity. */
        public final List<Proof> availableChoices;
        public final List<RestrictionStep> trace;
        public final List<RestrictionConflict> availableConflicts;
        public RestrictionOutcome(RestrictionStatus status,int choices) {
            this(status,choices,Collections.<Proof>emptyList());
        }
        public RestrictionOutcome(RestrictionStatus status,int choices,List<Proof> availableChoices) {
            this(status,choices,availableChoices,Collections.<RestrictionStep>emptyList());
        }
        public RestrictionOutcome(RestrictionStatus status,int choices,List<Proof> availableChoices,List<RestrictionStep> trace) {
            this(status,choices,availableChoices,trace,Collections.<RestrictionConflict>emptyList());
        }
        public RestrictionOutcome(RestrictionStatus status,int choices,List<Proof> availableChoices,List<RestrictionStep> trace,
                List<RestrictionConflict> availableConflicts) {
            if(status==null || choices<0 || status==RestrictionStatus.NOT_REQUESTED && choices!=0)
                throw new IllegalArgumentException("Restriction outcome");
            this.status=status;this.choices=choices;
            this.availableChoices=copy(availableChoices);
            this.trace=copy(trace);
            this.availableConflicts=copy(availableConflicts);
            if(status!=RestrictionStatus.NEXT_CONFLICT && !this.availableConflicts.isEmpty())
                throw new IllegalArgumentException("Only next conflicts expose current proof pairs");
            requireConflictMembers(this.availableChoices,this.availableConflicts);
            if(!this.trace.isEmpty()) {
                if(this.trace.size()!=choices)throw new IllegalArgumentException("Trace must cover the exact selected prefix");
                for(int i=0;i<this.trace.size();++i) {
                    RestrictionStep step=this.trace.get(i);
                    if(step.number!=i+1 || step.after!=(i+1==choices?status:RestrictionStatus.NEXT_CONFLICT))
                        throw new IllegalArgumentException("Ordered restriction outcomes required");
                }
            }
            if(status!=RestrictionStatus.NEXT_CONFLICT && !this.availableChoices.isEmpty())
                throw new IllegalArgumentException("Only next conflicts have choices");
        }
    }
    private static void requireConflictMembers(List<Proof> proofs,List<RestrictionConflict> conflicts) {
        java.util.Set<String> seen=new java.util.HashSet<String>();
        java.util.Set<String> members=new java.util.HashSet<String>();
        for(RestrictionConflict conflict:conflicts) {
            if(!seen.add(conflict.identity()))throw new IllegalArgumentException("Distinct historical conflict pairs required");
            members.add(conflict.positive.identity());members.add(conflict.negative.identity());
        }
        if(!conflicts.isEmpty())for(Proof proof:proofs)if(!members.contains(proof.identity()))
            throw new IllegalArgumentException("Choice proof must belong to the historical conflict pairs");
    }
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
        /** null selects the ordinary ordered opinion traversal. */
        public final RestrictionRequest restrictions;
        /** Build a fresh request with replacement traversal limits and the same exact inputs.
         * Preserves source pins, policy, query, trigger, selected identity prefix and restriction
         * workflow budget. Performs no inference or provider lookup; execute the returned request
         * to revalidate it. The original request remains unchanged. */
        public Request withLimits(Limits replacement) {
            return new Request(sources,policy,queryStatement,replacement,restrictions);
        }
        public Request(DmzSourcePlan.Request sources,Policy policy,String queryStatement,Limits limits) {
            this(sources,policy,queryStatement,limits,null);
        }
        public Request(DmzSourcePlan.Request sources,Policy policy,String queryStatement,Limits limits,RestrictionRequest restrictions) {
            if(sources==null || policy==null || queryStatement==null || limits==null)throw new NullPointerException();
            if(queryStatement.trim().isEmpty())throw new IllegalArgumentException("Explicit DMZ query required");
            this.sources=sources;this.policy=policy;this.queryStatement=queryStatement;this.limits=limits;
            if(restrictions!=null) {
                boolean selected=false;
                for(DmzSourcePlan.Selection source:sources.external)
                    if(source.revision.getContextId().equals(restrictions.triggerRevision.getContextId())
                            && source.revision.getRevision()==restrictions.triggerRevision.getRevision())selected=true;
                if(!selected)throw new IllegalArgumentException("Restriction trigger requires an exact selected source pin");
            }
            this.restrictions=restrictions;
        }
    }
    /** Explicit opt-in. Every invocation reconstructs and revalidates these historical proof identities.
     * Empty choices inspect the first conflict; extend the prefix from availableChoices.
     * Only COMPLETE carries an opinion, queried while all choices are enforced in the native child.
     * Preparation, projection and maximumVisits apply alongside this workflow's phase budget.
     * This operation has no ordered-candidate step history or continuation token. */
    public static final class RestrictionRequest {
        public final IContextResults.Revision triggerRevision;
        public final long triggerRule;
        /** Exact Proof.identity() values, in successive native conflict order. */
        public final List<String> choices;
        /** 1..10000 work allowance for reconstruction, evidence, source binding and native projection phases. */
        public final int budget;
        public RestrictionRequest(IContextResults.Revision revision,long rule,List<String> choices,int budget) {
            if(revision==null || choices==null)throw new NullPointerException();
            if(revision.getCommune()!=null || !revision.getCommuneMembers().isEmpty() || rule<0
                    || choices.size()>128 || budget<1 || budget>10000)
                throw new IllegalArgumentException("Bounded exact restriction request required");
            java.util.Set<String> unique=new java.util.HashSet<String>();
            for(String choice:choices)if(choice==null || choice.isEmpty() || !unique.add(choice))
                throw new IllegalArgumentException("Distinct explicit proof identities required");
            triggerRevision=revision;triggerRule=rule;this.choices=copy(choices);this.budget=budget;
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
        public final List<Proof> restrictionProofs;
        public final List<String> hypotheses;
        private ViewOpinion(Boolean value,List<Proof> proofs,List<Proof> restrictionProofs,List<String> hypotheses) {
            this.value=value;this.proofs=copy(proofs);this.restrictionProofs=copy(restrictionProofs);this.hypotheses=copy(hypotheses);
        }
    }
    public static final class Opinion {
        public final List<Step> steps;
        public final DmzConditionalResult.Answer answer;
        /** Detached selected source chains associated with this typed result. */
        public final List<Proof> restrictionProofs;
        public Opinion(List<Step> steps,DmzConditionalResult.Answer answer) {
            this(steps,answer,Collections.<Proof>emptyList());
        }
        public Opinion(List<Step> steps,DmzConditionalResult.Answer answer,List<Proof> restrictionProofs) {
            if(answer==null)throw new NullPointerException();
            this.steps=copy(steps);this.answer=answer;this.restrictionProofs=copy(restrictionProofs);
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
        /** Build a fresh request selecting a one-based alternative from this historical result.
         * The caller must execute it to revalidate all pins and the whole choice prefix. */
        public Request chooseRestriction(long number) {
            return chooseRestriction(number,request.limits);
        }
        /** Select an explicit historical alternative with replacement traversal limits.
         * All other request inputs and the complete identity prefix are preserved. */
        public Request chooseRestriction(long number,Limits replacement) {
            Request replay=request.withLimits(replacement);
            if(request.restrictions==null || restrictionOutcome.status!=RestrictionStatus.NEXT_CONFLICT)
                throw new IllegalStateException("Saved result does not offer restriction choices");
            if(number<1 || number>restrictionOutcome.availableChoices.size())
                throw new IllegalArgumentException("No saved restriction choice "+number);
            if(request.restrictions.choices.size()>=replay.limits.maximumVisits)
                throw new IllegalStateException("Restriction visit limit reached; start a fresh request with explicit limits");
            List<String> choices=new ArrayList<String>(request.restrictions.choices);
            choices.add(restrictionOutcome.availableChoices.get((int)number-1).identity());
            return new Request(replay.sources,replay.policy,replay.queryStatement,replay.limits,
                    new RestrictionRequest(request.restrictions.triggerRevision,request.restrictions.triggerRule,choices,request.restrictions.budget));
        }
        public final Request request;
        /** Freshly acquired physical Q and named source observations. */
        public final DmzSourcePlan acquisition;
        public final List<SourceInventory> sources;
        public final List<Input> candidates,productions;
        public final List<Opinion> opinions;
        public final List<ViewOpinion> viewOpinions;
        /** Source proof trees identifying accumulated restriction choices carried by this result. */
        public final List<Proof> restrictionProofs;
        /** Detached outcome of the bounded restriction workflow, when requested. */
        public final RestrictionOutcome restrictionOutcome;
        public final List<Event> events;
        public final List<Step> interruptedPrefix;
        /** Completed leaves preceding a stop remain partial historical observations. */
        public final boolean restartRequired,visitLimit;
        public final int visits,interruptedInput;
        public Result(Request request,DmzSourcePlan acquisition,List<SourceInventory> sources,
                List<Input> candidates,List<Input> productions,List<Opinion> opinions,List<Event> events,
                List<Step> interruptedPrefix,boolean restartRequired,boolean visitLimit,int visits,int interruptedInput) {
            this(request,acquisition,sources,candidates,productions,opinions,events,interruptedPrefix,
                    restartRequired,visitLimit,visits,interruptedInput,Collections.<Proof>emptyList(),
                    new RestrictionOutcome(RestrictionStatus.NOT_REQUESTED,0));
        }
        public Result(Request request,DmzSourcePlan acquisition,List<SourceInventory> sources,
                List<Input> candidates,List<Input> productions,List<Opinion> opinions,List<Event> events,
                List<Step> interruptedPrefix,boolean restartRequired,boolean visitLimit,int visits,int interruptedInput,
                List<Proof> restrictionProofs) {
            this(request,acquisition,sources,candidates,productions,opinions,events,interruptedPrefix,restartRequired,
                    visitLimit,visits,interruptedInput,restrictionProofs,
                    new RestrictionOutcome(RestrictionStatus.NOT_REQUESTED,0));
        }
        public Result(Request request,DmzSourcePlan acquisition,List<SourceInventory> sources,
                List<Input> candidates,List<Input> productions,List<Opinion> opinions,List<Event> events,
                List<Step> interruptedPrefix,boolean restartRequired,boolean visitLimit,int visits,int interruptedInput,
                List<Proof> restrictionProofs,RestrictionOutcome restrictionOutcome) {
            if(request==null || acquisition==null)throw new NullPointerException();
            if(restrictionOutcome==null)throw new NullPointerException("restrictionOutcome");
            this.request=request;this.acquisition=acquisition;this.sources=copy(sources);
            this.candidates=copy(candidates);this.productions=copy(productions);this.opinions=copy(opinions);
            this.restrictionProofs=copy(restrictionProofs);this.restrictionOutcome=restrictionOutcome;this.viewOpinions=views(opinions);
            this.events=copy(events);this.interruptedPrefix=copy(interruptedPrefix);
            this.restartRequired=restartRequired;this.visitLimit=visitLimit;this.visits=visits;this.interruptedInput=interruptedInput;
        }
    }
    private static List<ViewOpinion> views(List<Opinion> opinions) {
        List<ViewOpinion> result=new ArrayList<ViewOpinion>();
        for(Boolean truth:new Boolean[]{Boolean.TRUE,Boolean.FALSE,null}) {
            java.util.Map<String,Proof> proofs=new java.util.LinkedHashMap<String,Proof>();
            java.util.Map<String,Proof> restrictions=new java.util.LinkedHashMap<String,Proof>();
            java.util.Set<String> hypotheses=new java.util.LinkedHashSet<String>();
            boolean found=false;
            for(Opinion opinion:opinions)if(java.util.Objects.equals(truth,opinion.answer.value)) {
                found=true;
                for(Proof proof:opinion.answer.proofs)proofs.put(proof.identity(),proof);
                for(Proof proof:opinion.restrictionProofs)restrictions.put(proof.identity(),proof);
                hypotheses.addAll(opinion.answer.hypotheses);
            }
            if(found)result.add(new ViewOpinion(truth,new ArrayList<Proof>(proofs.values()),
                    new ArrayList<Proof>(restrictions.values()),new ArrayList<String>(hypotheses)));
        }
        return copy(result);
    }
    private static <T> List<T> copy(List<T> values) {
        if(values==null)throw new NullPointerException();
        for(T value:values)if(value==null)throw new NullPointerException();
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
