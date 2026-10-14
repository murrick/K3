/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.interfaces.*;

/** Real provider family traversal, exact histories and bounded partial frontier. */
public final class DmzRestrictionFamilyRunner {
    private static int checks;
    private interface Attempt {void run() throws Exception;}
    public static void main(String[] args) {
        try {
            for(boolean negative:new boolean[]{false,true})for(boolean recursive:new boolean[]{false,true})run(negative,recursive);
            System.out.println("DMZ_RESTRICTION_FAMILY_PASS checks="+checks);System.exit(0);
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    private static void run(boolean negative,boolean recursive) throws Exception {
        Path dir=Files.createTempDirectory("dmz-family-");System.setProperty("user.home",dir.toString());
        String sign=negative?"~":"",opposite=negative?"":"~";
        create(dir,"Q","!a(1); !a(2); !a(3); !b(4);");
        IContextResults.Revision a=create(dir,"A",recursive?"!@x a(x) -> middle(x); !@x middle(x) -> "+sign+"p(x);":"!@x a(x) -> "+sign+"p(x);");
        IContextResults.Revision b=create(dir,"B","!b(1); !b(2); !@x b(x) -> "+opposite+"p(x);");
        long trigger;
        try(SnapshotMindRuntime snapshot=SnapshotMindRuntime.open(dir.resolve("A"),new RevisionRef(a.getContextId(),a.getRevision()),"family-trigger")) {
            trigger=DmzSourceShape.inspect(snapshot.getMind(),100).declarations.get(0).sourceRule;
        }
        User user=new User();user.setDatabaseDir(dir.toString()+java.io.File.separator);DB db=new DB();db.init(user);
        Mind initial=new Mind(user);user.setCurrentMind(initial);Mind q=(Mind)initial.useStorage("Q");user.setCurrentMind(q);
        DmzRestrictionFamily.Result retained;
        try {
            IContextResults.Revision pin=new IContextResults.Revision(db.getContextId(),db.getRevision());
            DmzSourcesQuery.Limits limits=new DmzSourcesQuery.Limits(10000,10000,10000,10000,10000,10000,10000,8,128,1000000,128);
            DmzSourcePlan.Request sources=new DmzSourcePlan.Request(pin,Arrays.asList(new DmzSourcePlan.Selection("A",a),new DmzSourcePlan.Selection("B",b)),1000);
            DmzSourcesQuery.RestrictionRequest restrictions=new DmzSourcesQuery.RestrictionRequest(a,trigger,Collections.<String>emptyList(),10000,
                    DmzSourcesQuery.RestrictionChoiceScope.OBSERVED_CAUSAL_CHAINS);
            DmzSourcesQuery.Request seed=new DmzSourcesQuery.Request(sources,DmzSourcesQuery.Policy.FIXED_UNITS_THEN_ORDERED_GROUNDS_CONTEXT_UUID_RULE_ID,"?p(1);",limits,restrictions);
            String state=DmzSourceMaterializationQualification.fingerprint(q);
            DmzRestrictionFamily.Result shortRun=DmzRestrictionFamily.explore(q,seed,1);
            require(!shortRun.exhausted && shortRun.observations.size()==1 && shortRun.leaves.isEmpty()
                    && shortRun.pending.size()==1 && shortRun.pending.get(0).nextChoice==1
                    && shortRun.pending.get(0).reason==DmzRestrictionFamily.Stop.INVOCATION_LIMIT,"one invocation retains complete initial frontier");
            DmzRestrictionFamily.Result partial=DmzRestrictionFamily.explore(q,seed,3);
            require(!partial.exhausted && partial.observations.size()==3 && partial.leaves.size()==1 && !partial.pending.isEmpty(),
                    "bounded traversal retains completed leaf and unexplored siblings");
            for(DmzRestrictionFamily.Pending pending:partial.pending)
                require(pending.nextChoice>=1 && pending.nextChoice<=pending.parent.restrictionOutcome.availableChoices.size(),"pending suffix has exact historical parent and valid ordinal");
            DmzSourcesQuery.Limits one=new DmzSourcesQuery.Limits(10000,10000,10000,10000,10000,10000,10000,8,128,1000000,1);
            DmzRestrictionFamily.Result choiceLimited=DmzRestrictionFamily.explore(q,seed.withLimits(one),64);
            require(!choiceLimited.exhausted && choiceLimited.leaves.isEmpty() && !choiceLimited.pending.isEmpty()
                    && choiceLimited.pending.stream().allMatch(p->p.reason==DmzRestrictionFamily.Stop.CHOICE_LIMIT),"choice-depth limit retains blocked branches without claiming exhaustion");
            retained=DmzRestrictionFamily.explore(q,seed,64);
            require(retained.exhausted && retained.pending.isEmpty() && retained.uncovered.isEmpty() && retained.leaves.size()>=4,
                    "qualified family exhausts both conflicts within invocation allowance");
            Set<String> values=new HashSet<String>(),prefixes=new HashSet<String>();
            for(DmzSourcesQuery.Result leaf:retained.leaves) {
                require(leaf.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE && leaf.restrictionProofs.size()==2
                        && !leaf.opinions.get(0).answer.proofs.isEmpty(),"leaf retains complete exact two-cut trace and native proof");
                require(prefixes.add(leaf.request.restrictions.choices.toString()),"different restriction histories are not silently merged");
                DmzSourcesQuery.Request other=new DmzSourcesQuery.Request(sources,seed.policy,"?p(2);",limits,leaf.request.restrictions);
                DmzSourcesQuery.Result checked=q.executeDmzSources(other);
                values.add(leaf.opinions.get(0).answer.value+"/"+checked.opinions.get(0).answer.value);
            }
            require(values.size()==4 && !values.toString().contains("null"),"family includes all four signed native opinion pairs");
            DmzRestrictionFamily.Result terminal=DmzRestrictionFamily.explore(q,retained.leaves.get(0).request,1);
            require(terminal.exhausted && terminal.observations.size()==1 && terminal.leaves.size()==1,
                    "exact invocation cap on terminal seed does not invent a pending branch");
            DmzSourcesQuery.Result parent=shortRun.observations.get(0);
            DmzSourcesQuery.Result stopped=new DmzSourcesQuery.Result(seed,parent.acquisition,parent.sources,parent.candidates,parent.productions,
                    Collections.<DmzSourcesQuery.Opinion>emptyList(),parent.events,parent.interruptedPrefix,true,false,0,0,
                    Collections.<DmzSourcesQuery.Proof>emptyList(),new DmzSourcesQuery.RestrictionOutcome(DmzSourcesQuery.RestrictionStatus.STOPPED_UNCOVERED,0));
            IMind uncoveredProvider=(IMind)java.lang.reflect.Proxy.newProxyInstance(IMind.class.getClassLoader(),new Class<?>[]{IMind.class},
                    (proxy,method,args)->{if(method.getName().equals("executeDmzSources"))return stopped;throw new UnsupportedOperationException();});
            DmzRestrictionFamily.Result uncovered=DmzRestrictionFamily.explore(uncoveredProvider,seed,64);
            require(!uncovered.exhausted && uncovered.uncovered.size()==1 && uncovered.leaves.isEmpty() && uncovered.pending.isEmpty(),
                    "provider uncovered outcome remains an explicit nonexhaustive stopped branch");
            IMind failedProvider=(IMind)java.lang.reflect.Proxy.newProxyInstance(IMind.class.getClassLoader(),new Class<?>[]{IMind.class},
                    (proxy,method,args)->{throw new IllegalStateException("provider refusal");});
            reject(() -> DmzRestrictionFamily.explore(failedProvider,seed,64),IllegalStateException.class);
            require(partial.leaves.get(0).request.restrictions.choices.equals(retained.leaves.get(0).request.restrictions.choices),"fresh larger run preserves deterministic first leaf");
            reject(() -> retained.pending.clear(),UnsupportedOperationException.class);
            reject(() -> retained.leaves.clear(),UnsupportedOperationException.class);
            reject(() -> partial.pending.clear(),UnsupportedOperationException.class);
            reject(() -> DmzRestrictionFamily.explore(q,seed,0),IllegalArgumentException.class);
            DmzSourcesQuery.Request ordinary=new DmzSourcesQuery.Request(sources,seed.policy,seed.queryStatement,limits);
            reject(() -> DmzRestrictionFamily.explore(q,ordinary,64),IllegalArgumentException.class);
            DmzSourcesQuery.Request legacy=new DmzSourcesQuery.Request(sources,seed.policy,seed.queryStatement,limits,
                    new DmzSourcesQuery.RestrictionRequest(a,trigger,Collections.<String>emptyList(),10000));
            reject(() -> DmzRestrictionFamily.explore(q,legacy,64),IllegalArgumentException.class);
            require(state.equals(DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q,
                    "all family evaluations and refusals preserve physical Q and owner");
        }finally {user.setCurrentMind(q.closeStorage());}
        require(!retained.leaves.get(0).opinions.get(0).answer.proofs.isEmpty(),"detached leaf proofs survive physical owner closure");
    }
    private static IContextResults.Revision create(Path dir,String name,String source) throws Exception {
        User user=new User();user.setDatabaseDir(dir.toString()+java.io.File.separator);DB db=new DB();db.init(user);
        Mind mind=new Mind(user);user.setCurrentMind(mind);mind=(Mind)mind.useStorage(name);user.setCurrentMind(mind);
        try {require(mind.compile(source,null,false),"fixture "+name);}finally {user.setCurrentMind(mind.closeStorage());}
        try(ContextSnapshot snapshot=ContextSnapshot.open(dir.resolve(name))){return new IContextResults.Revision(snapshot.getContextId(),snapshot.getRevision());}
    }
    private static void reject(Attempt attempt,Class<? extends Exception> type) throws Exception {
        try {attempt.run();throw new AssertionError("Expected rejection "+type);}catch(Exception expected){if(!type.isInstance(expected))throw expected;++checks;}
    }
    private static void require(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
}
