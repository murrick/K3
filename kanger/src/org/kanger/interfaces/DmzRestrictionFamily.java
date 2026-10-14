/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.interfaces;

import java.util.*;

/** Bounded SDK traversal of qualified explicit-choice prefixes. No native owner or resume token. */
public final class DmzRestrictionFamily {
    private DmzRestrictionFamily() { }
    public enum Stop { INVOCATION_LIMIT, CHOICE_LIMIT }
    /** Historical parent and the first unexplored one-based choice; all following choices remain pending. */
    public static final class Pending {
        public final DmzSourcesQuery.Result parent;
        public final int nextChoice;
        public final Stop reason;
        private Pending(DmzSourcesQuery.Result parent,int nextChoice,Stop reason) {
            this.parent=parent;this.nextChoice=nextChoice;this.reason=reason;
        }
    }
    public static final class Result {
        public final List<DmzSourcesQuery.Result> observations,leaves,uncovered;
        public final List<Pending> pending;
        /** Exhaustion of qualified choices under this request only, not all consistent theories. */
        public final boolean exhausted;
        private Result(List<DmzSourcesQuery.Result> observations,List<DmzSourcesQuery.Result> leaves,
                List<DmzSourcesQuery.Result> uncovered,List<Pending> pending) {
            this.observations=copy(observations);this.leaves=copy(leaves);this.uncovered=copy(uncovered);this.pending=copy(pending);
            exhausted=pending.isEmpty() && uncovered.isEmpty();
        }
    }
    private static final class Frame {
        final DmzSourcesQuery.Result parent;
        int next=1;
        Frame(DmzSourcesQuery.Result parent){this.parent=parent;}
    }
    /** Explore all offered siblings in stable depth-first order, within a cap on fresh provider invocations.
     * Each invocation revalidates exact source pins and the full prefix. Provider failures propagate;
     * they are never converted into completed or uncovered branches. Start a fresh exploration to retry. */
    public static Result explore(IMind target,DmzSourcesQuery.Request seed,int maximumInvocations) throws Exception {
        if(target==null || seed==null)throw new NullPointerException();
        if(maximumInvocations<1 || maximumInvocations>10000 || seed.restrictions==null
                || seed.restrictions.choiceScope!=DmzSourcesQuery.RestrictionChoiceScope.OBSERVED_CAUSAL_CHAINS)
            throw new IllegalArgumentException("Bounded explicit observed-causal family request required");
        List<DmzSourcesQuery.Result> observations=new ArrayList<DmzSourcesQuery.Result>(),leaves=new ArrayList<DmzSourcesQuery.Result>(),
                uncovered=new ArrayList<DmzSourcesQuery.Result>();
        List<Pending> pending=new ArrayList<Pending>();
        Deque<Frame> stack=new ArrayDeque<Frame>();
        DmzSourcesQuery.Request request=seed;
        while(true) {
            DmzSourcesQuery.Result observed=target.executeDmzSources(request);
            observations.add(observed);
            switch(observed.restrictionOutcome.status) {
            case COMPLETE: leaves.add(observed);break;
            case STOPPED_UNCOVERED: uncovered.add(observed);break;
            case NEXT_CONFLICT:
                if(observed.restrictionOutcome.availableChoices.isEmpty())
                    throw new IllegalStateException("Nonempty qualified family choices required");
                if(observed.request.restrictions.choices.size()>=observed.request.limits.maximumVisits)
                    pending.add(new Pending(observed,1,Stop.CHOICE_LIMIT));
                else stack.push(new Frame(observed));
                break;
            default: throw new IllegalStateException("Restriction family provider outcome required");
            }
            if(observations.size()>=maximumInvocations || stack.isEmpty())break;
            Frame frame=stack.peek();
            request=frame.parent.chooseRestriction(frame.next++);
            if(frame.next>frame.parent.restrictionOutcome.availableChoices.size())stack.pop();
        }
        for(Frame frame:stack)pending.add(new Pending(frame.parent,frame.next,Stop.INVOCATION_LIMIT));
        return new Result(observations,leaves,uncovered,pending);
    }
    private static <T> List<T> copy(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
