/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.UUID;
import org.kanger.interfaces.IContextResults;

/** A throwing live continuation must roll back its child and release visitor/traversal ownership. */
public final class DmzLiveBranchContinuationRunner {
    private static int checks;
    public static void main(String[] args) {
        try {
            for(int failure:new int[]{1,2})run(failure);
            System.out.println("DMZ_LIVE_BRANCH_CONTINUATION_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) { failure.printStackTrace();System.exit(1); }
    }
    private static DmzStoredRetractionTransaction.ConditionalQueryLimits limits() {
        return new DmzStoredRetractionTransaction.ConditionalQueryLimits(10000,10000,10000,10000,8,64,1000000);
    }
    private static void run(final int failAt) throws Exception {
        User user=new User();Mind q=new Mind(user);user.setCurrentMind(q);
        IContextResults.Revision authority=new IContextResults.Revision(UUID.randomUUID(),1);
        final IContextResults.Revision external=new IContextResults.Revision(UUID.randomUUID(),1);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin(q);TerminalSupportCapture capture=TerminalSupportCapture.begin(q)) {
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,authority,1,DmzReplayProvenance.Authority.EXTERNAL,"!authority(1);")),"settled Q input");
            String state=DmzObservationStateFingerprint.capture(q);
            DmzReplayProvenance.SourceCheckpoint sources=DmzReplayProvenance.sourceCheckpoint(q);
            final int[] visits={0};
            try(DmzStoredRetractionTransaction workflow=DmzStoredRetractionTransaction.beginCandidateWorkflow(capture,journal,q,10000)) {
                require(Boolean.TRUE.equals(workflow.accept(external,2,DmzReplayProvenance.Authority.EXTERNAL,"!left(2);")),"pending prior input");
                boolean refused=false;
                try {
                    workflow.visitCandidate(external,3,"!~left(2);","?authority(1);",10000,10000,limits(),journal,
                            new DmzStoredRetractionTransaction.LiveBranchVisitor() {
                                public void visit(DmzStoredRetractionTransaction owner,Mind live,DmzStoredRetractionTransaction.InputVisitKind kind,int variant) throws Exception {
                                    try(DmzStoredRetractionTransaction child=DmzStoredRetractionTransaction.beginBranchContinuation(owner,live,10000)) {
                                        require(Boolean.TRUE.equals(child.accept(external,4,DmzReplayProvenance.Authority.EXTERNAL,"!later(3);")),"nested private input");
                                        require(Boolean.TRUE.equals(child.queryContinuationResult("?authority(1);",10000).value),"nested child keeps original Q");
                                        if(++visits[0]==failAt)throw new IllegalStateException("qualification continuation refusal");
                                    }
                                }
                            });
                }catch(IllegalStateException expected) { if(!expected.getMessage().contains("qualification continuation refusal"))throw expected;refused=true; }
                require(refused && visits[0]==failAt,"visitor failure crosses only the selected live prefix");
                require(workflow.classifyInput("!left(2);",10000)==DmzStoredRetractionTransaction.InputKind.EXISTING_PRIMARY,"failed continuation retains parent input");
                require(workflow.classifyInput("!later(3);",10000)==DmzStoredRetractionTransaction.InputKind.FRESH,"nested effects never escape failure");
                require(Boolean.TRUE.equals(workflow.queryContinuationResult("?authority(1);",10000).value),"failed traversal releases query ownership");
                final int[] retried={0};
                DmzStoredRetractionTransaction.ConditionalQueryResult result=workflow.visitCandidate(external,3,"!~left(2);","?authority(1);",10000,10000,limits(),journal,
                        new DmzStoredRetractionTransaction.LiveBranchVisitor() {
                            public void visit(DmzStoredRetractionTransaction owner,Mind live,DmzStoredRetractionTransaction.InputVisitKind kind,int variant) throws Exception {
                                try(DmzStoredRetractionTransaction child=DmzStoredRetractionTransaction.beginBranchContinuation(owner,live,10000)) {
                                    String query=kind==DmzStoredRetractionTransaction.InputVisitKind.KEEP_EXISTING ? "?left(2);" : "?~left(2);";
                                    require(Boolean.TRUE.equals(child.queryContinuationResult(query,10000).value),"retry recognizes its actual live initial side");
                                    require(Boolean.TRUE.equals(child.queryContinuationResult("?authority(1);",10000).value),"retry cannot promote branch to Q");
                                    ++retried[0];
                                }
                            }
                        });
                require(retried[0]==2 && result!=null && !result.restartRequired,"fresh retry restores both sides with no leaked visitor");
                Mind outsider=new Mind(new User());boolean wrong=false;
                try(DmzStoredRetractionTransaction unexpected=DmzStoredRetractionTransaction.beginBranchContinuation(workflow,outsider,10000)) {}
                catch(IllegalStateException expected) { wrong=expected.getMessage().contains("live owner"); }
                require(wrong,"foreign live Mind cannot become a continuation capability");
            }
            require(state.equals(DmzObservationStateFingerprint.capture(q)) && sources.isCurrent(q) && !q.hasPendingTransactions(),"all callback failures and retries preserve settled Q and release children");
        }
    }
    private static void require(boolean value,String reason) { ++checks;if(!value)throw new AssertionError(reason); }
}
