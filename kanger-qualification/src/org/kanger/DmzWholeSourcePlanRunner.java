/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.kanger.interfaces.IContextResults;
import org.kanger.units.Rule;
import org.kanger.units.Term;

/** Complete primary coverage, stable source order and explicit whole-operation refusals. */
public final class DmzWholeSourcePlanRunner {
    private static int checks;
    public static void main(String[] args) {
        try { ordering();zeroGrounds();boundaries();refusals();System.out.println("DMZ_WHOLE_SOURCE_PLAN_PASS checks="+checks);System.exit(0); }
        catch(Throwable failure) { failure.printStackTrace();System.exit(1); }
    }
    private static DmzCandidateSession.Limits limits(int audit) {
        return new DmzCandidateSession.Limits(10000,audit,10000,10000,10000,10000,10000,8,128,1000000);
    }
    private static Mind source(String text) throws Exception {
        User user=new User();Mind mind=new Mind(user);user.setCurrentMind(mind);
        if(!text.isEmpty())require(mind.compile(text,null,false),"source fixture accepted");return mind;
    }
    private static DmzCandidateSession.Participant participant(int id,Mind source) throws Exception {
        return new DmzCandidateSession.Participant(new IContextResults.Revision(new UUID(0,id),7),DmzSourceShape.inspect(source,10000));
    }
    private static void ordering() throws Exception {
        Mind q=source("!authority(1); !seed(1); !keep(1);");
        DmzCandidateSession.Participant authority=participant(99,q);
        DmzCandidateSession.Participant a=participant(1,source("!@x seed(x) -> out(x); !positive(2); !same(3);"));
        DmzCandidateSession.Participant b=participant(2,source("!~positive(2); !same(3); !@x same(x) -> tail(x);"));
        DmzCandidateSession.Participant empty=participant(3,source(""));
        DmzSourceDispatchPlan retained;
        try(DmzCandidateSession session=DmzCandidateSession.open(authority,Arrays.asList(empty,b,a),10000)) {
            retained=session.planSources();
            require(retained.sources.size()==3 && retained.sources.get(0).revision.getContextId().equals(a.revision.getContextId())
                    && retained.sources.get(1).revision.getContextId().equals(b.revision.getContextId()),"request source order cannot replace UUID order");
            require(retained.sources.get(2).primaryRules==0,"named empty source remains in exact source inventory");
            require(retained.primaryRules==6 && retained.candidates.size()==4 && retained.productions.size()==2,"every acquired primary occurs exactly once");
            require(retained.excludedGeneratedCaches>0,"native generated consequences are reported and excluded from declaration selection");
            require(retained.policy==DmzSourceDispatchPlan.Policy.FIXED_UNITS_THEN_ORDERED_GROUNDS_CONTEXT_UUID_RULE_ID,"explicit fixed-production policy");
            for(int n=1;n<retained.candidates.size();++n) {
                DmzCandidateSession.SourceRule before=retained.candidates.get(n-1),after=retained.candidates.get(n);
                require(before.context.toString().compareTo(after.context.toString())<0 || before.context.equals(after.context) && before.rule<after.rule,
                        "ground order uses exact source-local rule IDs");
            }
            for(String query:new String[]{"?authority(1);","?out(1);","?tail(3);"}) {
                DmzCandidateSession.SourcesResult result=session.evaluateSources(query,limits(10000),1000);
                require(!result.sequence.restartRequired && result.sequence.opinions.size()>=2,"automatic opposing-source alternatives complete");
                for(DmzCandidateSession.SequenceOpinion opinion:result.sequence.opinions)
                    require(opinion.steps.size()==retained.candidates.size() && Boolean.TRUE.equals(opinion.answer.value),"all selected grounds reach final source answer");
                require(result.plan.primaryRules==result.sequence.candidates.size()+result.sequence.productions.size(),"executed plan has no primary omissions");
            }
            require(!Boolean.TRUE.equals(session.queryQ("?out(1);",10000).value),"unit consequence derived from Q stays external to Q authority");
            require(session.evaluateSources("?authority(1);",limits(10000),1).sequence.restartRequired,"automatic invocation propagates ordered visit stop");
            try { retained.candidates.clear();throw new AssertionError("mutable plan"); }catch(UnsupportedOperationException expected) { ++checks; }
            try { retained.sources.clear();throw new AssertionError("mutable inventory"); }catch(UnsupportedOperationException expected) { ++checks; }
        }
        try(DmzCandidateSession reordered=DmzCandidateSession.open(authority,Arrays.asList(a,b,empty),10000)) {
            DmzSourceDispatchPlan plan=reordered.planSources();
            for(int n=0;n<plan.candidates.size();++n)require(plan.candidates.get(n).context.equals(retained.candidates.get(n).context)
                    && plan.candidates.get(n).rule==retained.candidates.get(n).rule,"ordering survives session and request permutation");
        }
        require(retained.primaryRules==6 && authority.inputs.isCurrent(q),"detached plan and original Q survive owner closure");
    }
    private static void zeroGrounds() throws Exception {
        Mind q=source("!authority(1); !seed(1); !keep(1);");DmzCandidateSession.Participant authority=participant(99,q);
        DmzCandidateSession.Participant units=participant(1,source("!@x seed(x) -> middle(x); !@x middle(x) -> out(x);"));
        try(DmzCandidateSession session=DmzCandidateSession.open(authority,Collections.singletonList(units),10000)) {
            DmzCandidateSession.SourcesResult result=session.evaluateSources("?out(1);",limits(10000),1);
            require(result.plan.candidates.isEmpty() && result.plan.productions.size()==2 && !result.sequence.restartRequired
                    && result.sequence.opinions.size()==1 && result.sequence.opinions.get(0).steps.isEmpty()
                    && Boolean.TRUE.equals(result.sequence.opinions.get(0).answer.value),"unit-only source uses Q grounds with a bounded fixed-source audit");
            require(!Boolean.TRUE.equals(session.queryQ("?out(1);",10000).value),"unit-only output never becomes original Q");
            boolean bounded=false;try { session.evaluateSources("?out(1);",limits(1),1); }catch(IllegalStateException expected) { bounded=true; }
            require(bounded && Boolean.TRUE.equals(session.queryQ("?authority(1);",10000).value),"fixed-source audit budget refuses without leaking state");
            require(Boolean.TRUE.equals(session.evaluateSources("?out(1);",limits(10000),1).sequence.opinions.get(0).answer.value),"fresh unit-only retry succeeds");
        }
        for(List<DmzCandidateSession.Participant> selected:Arrays.asList(Collections.<DmzCandidateSession.Participant>emptyList(),
                Collections.singletonList(participant(3,source(""))))) {
            try(DmzCandidateSession session=DmzCandidateSession.open(authority,selected,10000)) {
                DmzCandidateSession.SourcesResult result=session.evaluateSources("?authority(1);",limits(10000),1);
                require(result.plan.primaryRules==0 && result.sequence.opinions.size()==1 && Boolean.TRUE.equals(result.sequence.opinions.get(0).answer.value),"empty selection is an audited original-Q answer");
                require(result.plan.sources.size()==selected.size(),"empty named source retains its revision pin");
            }
        }
        DmzCandidateSession.Participant bad=participant(4,source("!@x seed(x) -> ~keep(x);"));
        try(DmzCandidateSession session=DmzCandidateSession.open(authority,Collections.singletonList(bad),10000)) {
            require(session.planSources().productions.size()==1,"fixed production conflict is not omitted by planning");
            boolean refused=false;try { session.evaluateSources("?keep(1);",limits(10000),100); }catch(IllegalStateException expected) { refused=true; }
            require(refused && Boolean.TRUE.equals(session.queryQ("?keep(1);",10000).value),"conflicting fixed units refuse the operation and preserve Q");
        }
    }
    private static void boundaries() throws Exception {
        DmzCandidateSession.Participant q=participant(99,source("!authority(1);"));
        StringBuilder grounds=new StringBuilder();for(int n=0;n<16;++n)grounds.append("!many(").append(n).append("); ");
        try(DmzCandidateSession session=DmzCandidateSession.open(q,Collections.singletonList(participant(1,source(grounds.toString()))),10000)) {
            DmzCandidateSession.SourcesResult result=session.evaluateSources("?many(15);",limits(10000),17);
            require(result.plan.candidates.size()==16 && !result.sequence.restartRequired && result.sequence.visits==17
                    && result.sequence.opinions.size()==1 && Boolean.TRUE.equals(result.sequence.opinions.get(0).answer.value),
                    "exact ground capacity processes its last input and final answer");
            require(session.evaluateSources("?many(15);",limits(10000),16).sequence.restartRequired,"final-answer visit is charged at capacity");
        }
        StringBuilder units=new StringBuilder();for(int n=0;n<64;++n)units.append("!@x a").append(n).append("(x) -> b").append(n).append("(x); ");
        try(DmzCandidateSession session=DmzCandidateSession.open(q,Collections.singletonList(participant(2,source(units.toString()))),10000)) {
            DmzCandidateSession.SourcesResult result=session.evaluateSources("?authority(1);",limits(10000),1);
            require(result.plan.productions.size()==64 && !result.sequence.restartRequired && result.sequence.opinions.size()==1
                    && Boolean.TRUE.equals(result.sequence.opinions.get(0).answer.value),"exact fixed-production capacity retains an audited zero-ground answer");
        }
    }
    private static void refusals() throws Exception {
        DmzCandidateSession.Participant q=participant(99,source("!authority(1);"));
        checkFailure(q,participant(1,source("!decimal(1.0);")),DmzSourceDispatchPlan.Refusal.UNSUPPORTED_DECLARATION);
        Mind unit=source("!@x seed(x) -> out(x);");
        for(org.kanger.interfaces.IRule item:unit.getRules())if(!unit.getRules().isGenerated(item))((Rule)item).setOrigin(new Term("!@x (~seed(x) | out(x));",unit));
        checkFailure(q,participant(2,unit),DmzSourceDispatchPlan.Refusal.UNSUPPORTED_DECLARATION);
        StringBuilder grounds=new StringBuilder();for(int n=0;n<17;++n)grounds.append("!many(").append(n).append("); ");
        checkFailure(q,participant(3,source(grounds.toString())),DmzSourceDispatchPlan.Refusal.GROUND_LIMIT);
        StringBuilder units=new StringBuilder();for(int n=0;n<65;++n)units.append("!@x a").append(n).append("(x) -> b").append(n).append("(x); ");
        checkFailure(q,participant(4,source(units.toString())),DmzSourceDispatchPlan.Refusal.PRODUCTION_LIMIT);
    }
    private static void checkFailure(DmzCandidateSession.Participant q,DmzCandidateSession.Participant source,DmzSourceDispatchPlan.Refusal reason) throws Exception {
        try(DmzCandidateSession session=DmzCandidateSession.open(q,Collections.singletonList(source),10000)) {
            for(boolean execute:new boolean[]{false,true}) {
                boolean refused=false;
                try { if(execute)session.evaluateSources("?authority(1);",limits(10000),1000);else session.planSources(); }
                catch(DmzSourceDispatchPlan.PlanningFailure expected) {
                    require(expected.reason==reason,"typed deterministic planning refusal");
                    require(reason!=DmzSourceDispatchPlan.Refusal.UNSUPPORTED_DECLARATION || expected.offendingInput!=null
                            && expected.offendingInput.context.equals(source.revision.getContextId()),"unsupported primary identifies its exact source occurrence");
                    refused=true;
                }
                require(refused && Boolean.TRUE.equals(session.queryQ("?authority(1);",10000).value),"planning refusal occurs before any source trial and keeps owner usable");
            }
        }
    }
    private static void require(boolean value,String reason) { ++checks;if(!value)throw new AssertionError(reason); }
}
