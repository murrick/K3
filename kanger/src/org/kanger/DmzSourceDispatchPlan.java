/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.kanger.interfaces.IContextResults;

/** Internal provider bridge: every acquired active primary is classified before native materialization. */
public final class DmzSourceDispatchPlan {
    public static final int MAXIMUM_GROUNDS=16,MAXIMUM_PRODUCTIONS=64;
    public enum Policy { FIXED_UNITS_THEN_ORDERED_GROUNDS_CONTEXT_UUID_RULE_ID }
    public enum Refusal { UNSUPPORTED_DECLARATION, GROUND_LIMIT, PRODUCTION_LIMIT }
    public static final class PlanningFailure extends IllegalArgumentException {
        private static final long serialVersionUID=1L;
        public final Refusal reason;
        public final DmzCandidateSession.SourceRule offendingInput;
        private PlanningFailure(Refusal reason,DmzCandidateSession.SourceRule input,String message) {
            super(message);this.reason=reason;offendingInput=input;
        }
    }
    public static final class SourceInventory {
        public final IContextResults.Revision revision;
        public final int primaryRules,inspectedRows,excludedGeneratedCaches;
        public final String primaryDigest;
        private SourceInventory(DmzCandidateSession.Participant source) {
            revision=source.revision;primaryRules=source.inputs.primaryRules;inspectedRows=source.inputs.inspectedRows;
            primaryDigest=source.inputs.primaryDigest;
            excludedGeneratedCaches=source.inputs.cachedGrounds.size()+source.inputs.unsupportedGenerated;
        }
    }
    public final Policy policy=Policy.FIXED_UNITS_THEN_ORDERED_GROUNDS_CONTEXT_UUID_RULE_ID;
    public final IContextResults.Revision authorityRevision;
    public final List<SourceInventory> sources;
    public final List<DmzCandidateSession.SourceRule> candidates,productions;
    public final List<String> candidateStatements,productionStatements;
    public final int primaryRules,excludedGeneratedCaches;
    private DmzSourceDispatchPlan(IContextResults.Revision authority,List<SourceInventory> sources,
            List<DmzCandidateSession.SourceRule> candidates,List<DmzCandidateSession.SourceRule> productions,
            List<String> candidateStatements,List<String> productionStatements,int primary,int caches) {
        authorityRevision=authority;this.sources=copy(sources);this.candidates=copy(candidates);this.productions=copy(productions);
        this.candidateStatements=copy(candidateStatements);this.productionStatements=copy(productionStatements);
        primaryRules=primary;excludedGeneratedCaches=caches;
    }
    private static <T> List<T> copy(List<T> values) { return Collections.unmodifiableList(new ArrayList<T>(values)); }
    static DmzSourceDispatchPlan prepare(DmzCandidateSession.Participant authority,List<DmzCandidateSession.Participant> external) {
        List<DmzCandidateSession.Participant> ordered=new ArrayList<DmzCandidateSession.Participant>(external);
        Collections.sort(ordered,new Comparator<DmzCandidateSession.Participant>() {
            public int compare(DmzCandidateSession.Participant a,DmzCandidateSession.Participant b) {
                return a.revision.getContextId().toString().compareTo(b.revision.getContextId().toString());
            }
        });
        List<SourceInventory> sources=new ArrayList<SourceInventory>();
        List<DmzCandidateSession.SourceRule> grounds=new ArrayList<DmzCandidateSession.SourceRule>(),units=new ArrayList<DmzCandidateSession.SourceRule>();
        List<String> groundText=new ArrayList<String>(),unitText=new ArrayList<String>();
        int primary=0,caches=0;
        for(DmzCandidateSession.Participant source:ordered) {
            SourceInventory inventory=new SourceInventory(source);sources.add(inventory);primary+=inventory.primaryRules;caches+=inventory.excludedGeneratedCaches;
            List<DmzSourceShape.Declaration> declarations=new ArrayList<DmzSourceShape.Declaration>(source.inputs.declarations);
            Collections.sort(declarations,new Comparator<DmzSourceShape.Declaration>() {
                public int compare(DmzSourceShape.Declaration a,DmzSourceShape.Declaration b) { return Long.compare(a.sourceRule,b.sourceRule); }
            });
            for(DmzSourceShape.Declaration declaration:declarations) {
                DmzCandidateSession.SourceRule selected=new DmzCandidateSession.SourceRule(source.revision.getContextId(),declaration.sourceRule);
                if(declaration.groundStatement!=null) { grounds.add(selected);groundText.add(declaration.statement); }
                else if(declaration.unitStatement!=null) { units.add(selected);unitText.add(declaration.statement); }
                else throw new PlanningFailure(Refusal.UNSUPPORTED_DECLARATION,selected,
                        "Unsupported acquired declaration at "+selected.context+":"+selected.rule+": "+declaration.statement);
            }
        }
        if(grounds.size()>MAXIMUM_GROUNDS)throw new PlanningFailure(Refusal.GROUND_LIMIT,null,
                "Whole-source ground count "+grounds.size()+" exceeds "+MAXIMUM_GROUNDS+"; no partial selection permitted");
        if(units.size()>MAXIMUM_PRODUCTIONS)throw new PlanningFailure(Refusal.PRODUCTION_LIMIT,null,
                "Whole-source production count "+units.size()+" exceeds "+MAXIMUM_PRODUCTIONS+"; no partial selection permitted");
        if(primary!=grounds.size()+units.size())throw new IllegalStateException("Whole-source declaration coverage mismatch");
        return new DmzSourceDispatchPlan(authority.revision,sources,grounds,units,groundText,unitText,primary,caches);
    }
}
