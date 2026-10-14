/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.DmzSourcePlan;
import org.kanger.interfaces.DmzSourcesQuery;
import org.kanger.interfaces.IContextResults;
import org.kanger.interfaces.internal.IContextFederation;

/** Public provider contract, detached histories, fresh exact acquisition and failure cleanup. */
public final class DmzSourcesQueryRunner {
    private static int checks;
    private interface Attempt { void run() throws Exception; }
    private static DmzSourcesQuery.Limits limits(int visits,int nodes) {
        return new DmzSourcesQuery.Limits(10000,10000,10000,10000,10000,10000,10000,8,nodes,1000000,visits);
    }
    private static DmzSourcesQuery.Request request(IContextResults.Revision q,String query,int visits,int nodes,DmzSourcePlan.Selection... sources) {
        return new DmzSourcesQuery.Request(new DmzSourcePlan.Request(q,Arrays.asList(sources),1000),
                DmzSourcesQuery.Policy.FIXED_UNITS_THEN_ORDERED_GROUNDS_CONTEXT_UUID_RULE_ID,query,limits(visits,nodes));
    }
    public static void main(String[] args) {
        try { run();for(boolean negative:new boolean[]{false,true}) {
                for(boolean recursive:new boolean[]{false,true})restrictions(negative,recursive);
                growingEvidence(negative);
            }
            for(boolean negative:new boolean[]{false,true})for(boolean recursive:new boolean[]{false,true})for(boolean protectedRule:new boolean[]{false,true})causalChoices(negative,recursive,protectedRule);
            for(boolean negative:new boolean[]{false,true})for(boolean recursive:new boolean[]{false,true})successiveCausal(negative,recursive);
            queryProjection();
            System.out.println("DMZ_SOURCES_QUERY_PASS checks="+checks);System.exit(0); }
        catch(Throwable failure) { failure.printStackTrace();System.exit(1); }
    }
    private static void run() throws Exception {
        Path dir=Files.createTempDirectory("dmz-public-query-");System.setProperty("user.home",dir.toString());
        create(dir,"Q","!authority(1);");
        IContextResults.Revision a=create(dir,"A","!left(2); !source(3); !@x source(x) -> result(x);");
        IContextResults.Revision b=create(dir,"B","!~left(2); !~authority(1);");
        IContextResults.Revision empty=create(dir,"Empty","");
        IContextResults.Revision decimal=create(dir,"Decimal","!fraction(1.0);");
        IContextResults.Revision unit=create(dir,"Unit","!@x authority(x) -> more(x);");
        IContextResults.Revision badUnit=create(dir,"BadUnit","!@x authority(x) -> ~authority(x);");
        StringBuilder grounds=new StringBuilder(),units=new StringBuilder();
        for(int i=0;i<17;++i)grounds.append("!ground(").append(i).append("); ");
        for(int i=0;i<65;++i)units.append("!@x from").append(i).append("(x) -> to").append(i).append("(x); ");
        IContextResults.Revision tooMany=create(dir,"Grounds",grounds.toString());
        IContextResults.Revision tooManyUnits=create(dir,"Units",units.toString());
        User user=new User();user.setDatabaseDir(dir.toString()+java.io.File.separator);
        DB db=new DB();db.init(user);Mind initial=new Mind(user);user.setCurrentMind(initial);
        final Mind q=(Mind)initial.useStorage("Q");user.setCurrentMind(q);
        IContextFederation provider=db;
        try {
            IContextResults.Revision pin=new IContextResults.Revision(db.getContextId(),db.getRevision());
            DmzSourcePlan.Selection sa=new DmzSourcePlan.Selection("A",a),sb=new DmzSourcePlan.Selection("B",b),se=new DmzSourcePlan.Selection("Empty",empty);
            String state=org.kanger.DmzSourceMaterializationQualification.fingerprint(q);
            DmzSourcesQuery.Request invocation=request(pin,"?left(2);",1000,128,sb,se,sa);
            DmzSourcesQuery.Limits replacement=limits(2,128);
            DmzSourcesQuery.Request changed=invocation.withLimits(replacement);
            require(changed!=invocation && changed.limits==replacement && changed.sources==invocation.sources
                    && changed.policy==invocation.policy && changed.queryStatement.equals(invocation.queryStatement)
                    && changed.restrictions==null && invocation.limits.maximumVisits==1000,
                    "ordinary limit helper retains exact acquisition and policy without enabling restrictions");
            reject(() -> invocation.withLimits(null),NullPointerException.class);
            DmzSourcesQuery.Result retained=provider.executeDmzSources(q,invocation);
            require(!retained.restartRequired && retained.opinions.size()>=2,"public invocation completes both opposing opinions");
            require(retained.restrictionProofs.isEmpty(),"ordinary public result has no attached accumulated restriction receipt");
            require(retained.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.NOT_REQUESTED
                    && retained.restrictionOutcome.choices==0,"ordinary public result reports that restriction continuation was not requested");
            require(retained.opinions.get(0).restrictionProofs.isEmpty(),"ordinary opinion has no attached accumulated restriction receipt");
            for(DmzSourcesQuery.ViewOpinion view:retained.viewOpinions)
                require(view.restrictionProofs.isEmpty(),"ordinary view opinion has no attached restriction provenance");
            require(retained.request==invocation && retained.acquisition.authority.revision.getContextId().equals(pin.getContextId()),"explicit policy/limits and Q pin retained");
            require(retained.candidates.size()==4 && retained.productions.size()==1 && retained.sources.size()==3,"all declarations and named empty inventory retained");
            boolean yes=false,other=false,declined=false;
            for(DmzSourcesQuery.Opinion opinion:retained.opinions) {
                yes|=Boolean.TRUE.equals(opinion.answer.value);other|=!Boolean.TRUE.equals(opinion.answer.value);
                require(opinion.steps.size()==4,"complete leaf covers all ordered grounds");
                for(int i=0;i<opinion.steps.size();++i) {
                    require(opinion.steps.get(i).input==retained.candidates.get(i),"history refers to exact detached ordered occurrence");
                    declined|=opinion.steps.get(i).decision==DmzSourcesQuery.Decision.DECLINED_Q;
                }
            }
            require(yes && other && declined,"public result keeps opposing answers and Q rejection separate");
            require(!retained.events.isEmpty(),"conditional event projections preserved");
            DmzSourcesQuery.Result permuted=provider.executeDmzSources(q,request(pin,"?left(2);",1000,128,sa,sb,se));
            for(int i=0;i<4;++i)require(retained.candidates.get(i).revision.getContextId().equals(permuted.candidates.get(i).revision.getContextId())
                    && retained.candidates.get(i).rule==permuted.candidates.get(i).rule,"request order does not alter explicit UUID/rule policy");
            reject(() -> retained.opinions.clear(),UnsupportedOperationException.class);
            reject(() -> retained.opinions.get(0).steps.clear(),UnsupportedOperationException.class);
            reject(() -> retained.candidates.clear(),UnsupportedOperationException.class);
            reject(() -> retained.events.clear(),UnsupportedOperationException.class);
            reject(() -> retained.restrictionProofs.clear(),UnsupportedOperationException.class);
            reject(() -> retained.opinions.get(0).restrictionProofs.clear(),UnsupportedOperationException.class);
            DmzSourcesQuery.Result stopped=provider.executeDmzSources(q,request(pin,"?left(2);",2,128,sa,sb));
            require(stopped.restartRequired && stopped.visitLimit && stopped.visits==2 && !stopped.interruptedPrefix.isEmpty(),"public visit stop is partial historical frontier");
            DmzSourcesQuery.Request bounded=request(pin,"?left(2);",1000,1,sa,sb);
            DmzSourcesQuery.Result nodeStop=provider.executeDmzSources(q,new DmzSourcesQuery.Request(bounded.sources,bounded.policy,
                    bounded.queryStatement,new DmzSourcesQuery.Limits(10000,10000,10000,10000,10000,10000,10000,8,1,1,1000)));
            require(nodeStop.restartRequired && !nodeStop.events.isEmpty(),"conditional stop retained through public projection");
            require(Boolean.TRUE.equals(provider.executeDmzSources(q,request(pin,"?authority(1);",1000,128)).opinions.get(0).answer.value),"empty selection returns Q after preceding stopped owner closes");
            require(Boolean.TRUE.equals(provider.executeDmzSources(q,request(pin,"?more(1);",1000,128,new DmzSourcePlan.Selection("Unit",unit))).opinions.get(0).answer.value),"zero-ground fixed-unit inference through public contract");
            require(provider.executeDmzSources(q,request(pin,"?authority(1);",1000,128,se)).sources.size()==1,"named empty source retained");
            refusal(provider,q,request(pin,"?authority(1);",1000,128,new DmzSourcePlan.Selection("Decimal",decimal)),DmzSourcesQuery.Refusal.UNSUPPORTED_DECLARATION,true);
            refusal(provider,q,request(pin,"?authority(1);",1000,128,new DmzSourcePlan.Selection("Grounds",tooMany)),DmzSourcesQuery.Refusal.GROUND_LIMIT,false);
            refusal(provider,q,request(pin,"?authority(1);",1000,128,new DmzSourcePlan.Selection("Units",tooManyUnits)),DmzSourcesQuery.Refusal.PRODUCTION_LIMIT,false);
            reject(() -> provider.executeDmzSources(q,request(pin,"?authority(1);",1000,128,new DmzSourcePlan.Selection("BadUnit",badUnit))),Exception.class);
            reject(() -> provider.executeDmzSources(q,request(pin,"@invalid",1000,128,sa)),Exception.class);
            reject(() -> provider.executeDmzSources(q,request(new IContextResults.Revision(pin.getContextId(),pin.getRevision()+1),"?authority(1);",1000,128)),Exception.class);
            reject(() -> provider.executeDmzSources(q,request(pin,"?authority(1);",1000,128,new DmzSourcePlan.Selection("Missing",a))),Exception.class);
            reject(() -> provider.executeDmzSources(q,request(pin,"?authority(1);",1000,128,new DmzSourcePlan.Selection("A",b))),Exception.class);
            reject(() -> new DmzSourcesQuery.Limits(10000,10000,10000,10000,10000,10000,10000,8,128,1000000,0),IllegalArgumentException.class);
            Mind reserved=new Mind(q);
            try { reject(() -> provider.executeDmzSources(q,invocation),Exception.class); }finally { q.release(reserved); }
            IContextResults.Revision advanced=create(dir,"A","!later(9);");
            require(advanced.getRevision()>a.getRevision(),"foreign HEAD advances");
            DmzSourcesQuery.Result oldPin=provider.executeDmzSources(q,request(pin,"?result(3);",1000,128,sa));
            require(Boolean.TRUE.equals(oldPin.opinions.get(0).answer.value) && oldPin.candidates.get(0).revision.getRevision()==a.getRevision(),"fresh invocation still acquires exact historical pin");
            require(retained.candidates.size()==4 && retained.opinions.get(0).steps.size()==4,"closed historical result stays usable after later invocations");
            require(state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q
                    && db.getRevision()==pin.getRevision() && !db.hasConnectedContexts(),"successes, stops and refusals preserve physical Q/topology");
            require(Boolean.TRUE.equals(provider.executeDmzSources(q,request(pin,"?authority(1);",1000,128)).opinions.get(0).answer.value),"fresh public operation remains usable after all failures");
        }finally { user.setCurrentMind(q.closeStorage()); }
    }
    private static void refusal(IContextFederation provider,Mind q,DmzSourcesQuery.Request request,DmzSourcesQuery.Refusal reason,boolean identity) throws Exception {
        try { provider.executeDmzSources(q,request);throw new AssertionError("Expected typed refusal"); }
        catch(DmzSourcesQuery.PlanningRefusal expected) { require(expected.reason==reason && (expected.context!=null)==identity && (expected.rule!=null)==identity,"typed public planning refusal with precise identity"); }
    }
    private static void restrictions(boolean negative,boolean recursive) throws Exception {
        Path dir=Files.createTempDirectory("dmz-provider-restrictions-");System.setProperty("user.home",dir.toString());
        String sign=negative?"~":"",opposite=negative?"":"~",premise=recursive?"middle":"seed";
        create(dir,"Q","!seed(1); !"+opposite+"cache(1); !seed(2); !"+opposite+"cache(2); !seed(3);");
        String text=(recursive?"!@x seed(x) -> middle(x); ":"")+"!@x "+premise+"(x) -> "+sign+"cache(x);";
        IContextResults.Revision a=create(dir,"A",text),b=create(dir,"B",text);
        long trigger;
        try(SnapshotMindRuntime snapshot=SnapshotMindRuntime.open(dir.resolve("A"),new RevisionRef(a.getContextId(),a.getRevision()),"restriction-trigger")) {
            trigger=org.kanger.DmzSourceShape.inspect(snapshot.getMind(),100).declarations.get(0).sourceRule;
        }
        User user=new User();user.setDatabaseDir(dir.toString()+java.io.File.separator);
        DB db=new DB();db.init(user);Mind initial=new Mind(user);user.setCurrentMind(initial);
        Mind q=(Mind)initial.useStorage("Q");user.setCurrentMind(q);
        try {
            IContextResults.Revision pin=new IContextResults.Revision(db.getContextId(),db.getRevision());
            DmzSourcesQuery.Request base=request(pin,"?"+sign+"cache(1);",1000,128,
                    new DmzSourcePlan.Selection("A",a),new DmzSourcePlan.Selection("B",b));
            java.util.List<String> choices=new java.util.ArrayList<String>();
            String state=org.kanger.DmzSourceMaterializationQualification.fingerprint(q);
            DmzSourcesQuery.Result current=db.executeDmzSources(q,optIn(base,a,trigger,choices,10000));
            require(current.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.NEXT_CONFLICT
                    && current.restrictionOutcome.choices==0 && current.opinions.isEmpty() && current.restartRequired,
                    "explicit trigger returns choices without inventing an opinion");
            int firstRoot=current.restrictionOutcome.availableChoices.get(0).conclusion.endsWith("(1);")?1:2;
            boolean mixedChain=false;
            int evidenceNodes=0;
            require(current.restrictionOutcome.availableConflicts.size()==current.restrictionOutcome.availableChoices.size(),
                    "covered pending conflict preserves both proof endpoints for every source alternative");
            for(DmzSourcesQuery.RestrictionConflict pair:current.restrictionOutcome.availableConflicts) {
                require(pair.positive.conclusion.substring(1).equals(pair.negative.conclusion.substring(2)),
                        "public endpoints are exact opposite signed grounds");
                DmzSourcesQuery.Proof protectedQ=negative?pair.positive:pair.negative;
                require(protectedQ.premises.isEmpty() && protectedQ.source.revision.getContextId().equals(pin.getContextId())
                        && protectedQ.source.revision.getRevision()==pin.getRevision(),"opposing Q endpoint retains its exact primary source pin");
                DmzSourcesQuery.Proof external=negative?pair.negative:pair.positive;
                java.util.Set<java.util.UUID> origins=new java.util.HashSet<java.util.UUID>();
                proofOrigins(external,origins);
                require(origins.contains(pin.getContextId()) && (origins.contains(a.getContextId()) || origins.contains(b.getContextId())),
                        "external endpoint retains both its production source and its Q premise source");
                if(origins.contains(a.getContextId()) && origins.contains(b.getContextId()))mixedChain=true;
                evidenceNodes+=proofNodes(pair.positive)+proofNodes(pair.negative);
            }
            require(!recursive || mixedChain,"recursive conflict evidence includes derivations crossing both exact external sources");
            final int exactEvidenceNodes=evidenceNodes;
            DmzSourcesQuery.Request exactProjection=projection(base,exactEvidenceNodes);
            DmzSourcesQuery.Result exact=db.executeDmzSources(q,optIn(exactProjection,a,trigger,Collections.<String>emptyList(),10000));
            require(exact.restrictionOutcome.availableConflicts.size()==current.restrictionOutcome.availableConflicts.size(),
                    "exact shared evidence node allowance succeeds without dropping opposing alternatives");
            for(int i=0;i<exact.restrictionOutcome.availableConflicts.size();i++)require(exact.restrictionOutcome.availableConflicts.get(i).identity()
                    .equals(current.restrictionOutcome.availableConflicts.get(i).identity()),"budget-boundary replay retains deterministic exact endpoint trees");
            try {
                db.executeDmzSources(q,optIn(projection(base,exactEvidenceNodes-1),a,trigger,Collections.<String>emptyList(),10000));
                throw new AssertionError("Incomplete opposing evidence accepted");
            } catch(IllegalStateException refusal) {
                require(refusal.getMessage().contains("Detached source proof budget exceeded"),
                        "one missing evidence node refuses before exposing a partial frontier");
                require(refusal instanceof DmzSourcesQuery.ProjectionRefusal
                        && ((DmzSourcesQuery.ProjectionRefusal)refusal).phase==DmzSourcesQuery.ProjectionPhase.CONFLICT_EVIDENCE
                        && ((DmzSourcesQuery.ProjectionRefusal)refusal).completedChoices==0
                        && ((DmzSourcesQuery.ProjectionRefusal)refusal).projectionLimit==exactEvidenceNodes-1
                        && ((DmzSourcesQuery.ProjectionRefusal)refusal).restrictionBudget==10000
                        && ((DmzSourcesQuery.ProjectionRefusal)refusal).allowance==exactEvidenceNodes-1,
                        "initial refusal identifies the actual projection phase and effective configured allowance");
            }
            require(state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q
                    && db.getRevision()==pin.getRevision() && !db.hasConnectedContexts(),
                    "evidence projection refusal restores physical Q, revision and topology");
            DmzSourcesQuery.Result initialResult=current;
            reject(() -> initialResult.restrictionOutcome.availableConflicts.clear(),UnsupportedOperationException.class);
            DmzSourcesQuery.RestrictionConflict firstPair=current.restrictionOutcome.availableConflicts.get(0);
            String protectedIdentity=(negative?firstPair.positive:firstPair.negative).identity();
            reject(() -> db.executeDmzSources(q,optIn(base,a,trigger,Collections.singletonList(protectedIdentity),10000)),IllegalArgumentException.class);
            require(db.executeDmzSources(q,optIn(base,a,trigger,Collections.<String>emptyList(),10000)).restrictionOutcome.availableConflicts.get(0)
                    .identity().equals(firstPair.identity()),"fresh full evidence remains usable after budget and primary-selection refusals");
            reject(() -> new DmzSourcesQuery.RestrictionConflict(firstPair.negative,firstPair.positive),IllegalArgumentException.class);
            DmzSourcesQuery.Proof wrongGround=new DmzSourcesQuery.Proof(firstPair.negative.source,"!~other(99);",firstPair.negative.premises);
            reject(() -> new DmzSourcesQuery.RestrictionConflict(firstPair.positive,wrongGround),IllegalArgumentException.class);
            DmzSourcesQuery.Result second=q.executeDmzSources(current.chooseRestriction(2));
            require(second.restrictionProofs.get(0).identity().equals(current.restrictionOutcome.availableChoices.get(1).identity()),
                    "historical ordinal helper preserves an explicit nonfirst caller choice");
            require(second.restrictionOutcome.trace.size()==1 && second.restrictionOutcome.trace.get(0).alternatives.size()
                    ==current.restrictionOutcome.availableChoices.size(),"trace retains all offered alternatives for an explicit nonfirst selection");
            reject(() -> second.restrictionOutcome.trace.clear(),UnsupportedOperationException.class);
            reject(() -> second.restrictionOutcome.trace.get(0).alternatives.clear(),UnsupportedOperationException.class);
            reject(() -> second.restrictionOutcome.trace.get(0).conflicts.clear(),UnsupportedOperationException.class);
            reject(() -> currentChoicesImmutable(base,db,q,a,trigger),UnsupportedOperationException.class);
            reject(() -> db.executeDmzSources(q,optIn(base,a,trigger,Collections.singletonList("foreign-proof"),10000)),IllegalArgumentException.class);
            reject(() -> db.executeDmzSources(q,optIn(base,a,Long.MAX_VALUE,choices,10000)),IllegalArgumentException.class);
            reject(() -> db.executeDmzSources(q,optIn(base,a,trigger,choices,1)),IllegalStateException.class);
            for(int count=0;current.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.NEXT_CONFLICT;++count) {
                require(count<8 && !current.restrictionOutcome.availableChoices.isEmpty(),"bounded caller choice workflow");
                DmzSourcesQuery.Proof selected=current.restrictionOutcome.availableChoices.get(0);
                DmzSourcesQuery.Result earlier=current;
                choices.add(selected.identity());
                current=db.executeDmzSources(q,optIn(base,a,trigger,choices,10000));
                require(current.restrictionProofs.size()==choices.size() && current.restrictionOutcome.choices==choices.size(),
                        "fresh provider reconstruction validates the full exact choice prefix");
                for(int i=0;i<choices.size();++i)require(choices.get(i).equals(current.restrictionProofs.get(i).identity()),
                        "detached selected proof identities are stable across invocations");
                require(current.restrictionOutcome.trace.size()==choices.size(),"trace covers every replayed choice");
                for(int i=0;i<choices.size();++i) {
                    DmzSourcesQuery.RestrictionStep step=current.restrictionOutcome.trace.get(i);
                    require(step.number==i+1 && step.selected.identity().equals(choices.get(i))
                            && step.alternatives.stream().anyMatch(proof->proof.identity().equals(step.selected.identity()))
                            && step.after==(i+1==choices.size()?current.restrictionOutcome.status:DmzSourcesQuery.RestrictionStatus.NEXT_CONFLICT),
                            "historical alternatives, selection and resulting status are coherent");
                    require(!step.conflicts.isEmpty() && step.conflicts.stream().anyMatch(pair->pair.contains(step.selected.identity())),
                            "selected historical proof belongs to an actually covered conflict pair");
                    for(DmzSourcesQuery.RestrictionConflict pair:step.conflicts) {
                        require(pair.positive.conclusion.substring(1).equals(pair.negative.conclusion.substring(2)),
                                "replayed historical endpoints retain exact opposite substitution");
                        for(int prior=0;prior<i;prior++)require(!pair.contains(choices.get(prior)),
                                "already excluded endpoint alternatives do not reappear in later conflict evidence");
                    }
                    if(i<earlier.restrictionOutcome.trace.size()) {
                        DmzSourcesQuery.RestrictionStep previous=earlier.restrictionOutcome.trace.get(i);
                        require(previous.selected.identity().equals(step.selected.identity())
                                && previous.alternatives.size()==step.alternatives.size(),"earlier branch trace survives native owner closure and fresh replay");
                        require(previous.conflicts.size()==step.conflicts.size(),"historical conflict pair count is stable across full replay");
                        for(int pair=0;pair<previous.conflicts.size();pair++)require(previous.conflicts.get(pair).identity().equals(step.conflicts.get(pair).identity()),
                                "both historical endpoint source chains survive native owner closure and fresh replay");
                    }
                }
                org.kanger.DmzCandidateSession.requireSettledPhysicalQ(q);
                require(state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q
                        && db.getRevision()==pin.getRevision(),"each provider invocation restores physical Q");
            }
            int expected=recursive?8:4;
            require(current.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE && choices.size()==expected
                    && !current.restartRequired && current.restrictionOutcome.availableChoices.isEmpty(),"two successive roots exhaust exact source alternatives: "+negative+"/"+recursive+" "+current.restrictionOutcome.status+" choices="+choices.size());
            require(current.restrictionOutcome.availableConflicts.isEmpty(),"complete result offers no current conflict evidence");
            require(current.opinions.size()==1 && Boolean.FALSE.equals(current.opinions.get(0).answer.value)
                    && !current.opinions.get(0).answer.proofs.isEmpty(),"query executes in the completed restricted native branch");
            require(current.opinions.get(0).restrictionProofs.size()==expected && current.viewOpinions.get(0).restrictionProofs.size()==expected,
                    "completed opinion and truth view retain exact restriction provenance");
            DmzSourcesQuery.Request unrelated=new DmzSourcesQuery.Request(base.sources,base.policy,"?"+sign+"cache(3);",base.limits);
            DmzSourcesQuery.Result unaffected=db.executeDmzSources(q,optIn(unrelated,a,trigger,choices,10000));
            require(Boolean.TRUE.equals(unaffected.opinions.get(0).answer.value) && unaffected.opinions.get(0).answer.proofs.size()>=2,
                    "other substitution retains source proof alternatives under the same accumulated cuts");
            DmzSourcesQuery.Request shortProof=new DmzSourcesQuery.Request(unrelated.sources,unrelated.policy,unrelated.queryStatement,
                    new DmzSourcesQuery.Limits(10000,10000,10000,10000,1,10000,10000,8,128,1000000,1000));
            reject(() -> db.executeDmzSources(q,optIn(shortProof,a,trigger,choices,10000)),IllegalStateException.class);
            DmzSourcesQuery.Request unknown=new DmzSourcesQuery.Request(base.sources,base.policy,"?unknown(9);",base.limits);
            DmzSourcesQuery.Result unknownResult=db.executeDmzSources(q,optIn(unknown,a,trigger,choices,10000));
            require(unknownResult.opinions.get(0).answer.value==null && unknownResult.opinions.get(0).answer.proofs.isEmpty(),
                    "unknown restricted query remains an unknown historical observation");
            java.util.List<String> extra=new java.util.ArrayList<String>(choices);extra.add("unused-proof");
            reject(() -> db.executeDmzSources(q,optIn(base,a,trigger,extra,10000)),IllegalArgumentException.class);
            DmzSourcesQuery.Request shortVisits=new DmzSourcesQuery.Request(base.sources,base.policy,base.queryStatement,limits(1,128));
            reject(() -> db.executeDmzSources(q,optIn(shortVisits,a,trigger,choices,10000)),IllegalArgumentException.class);
            IContextResults.Revision peaceful=create(dir,"Peaceful","!unused(99);");
            long peacefulRule=primaryRule(dir,"Peaceful",peaceful);
            DmzSourcesQuery.Request noConflict=request(pin,"?seed(1);",1000,128,new DmzSourcePlan.Selection("Peaceful",peaceful));
            reject(() -> db.executeDmzSources(q,optIn(noConflict,peaceful,peacefulRule,Collections.<String>emptyList(),10000)),IllegalStateException.class);
            if(!recursive) {
                IContextResults.Revision independent=create(dir,"Independent","!other("+(3-firstRoot)+"); !@x other(x) -> "+sign+"cache(x);");
                DmzSourcesQuery.Request uncovered=request(pin,base.queryStatement,1000,128,new DmzSourcePlan.Selection("A",a),
                        new DmzSourcePlan.Selection("B",b),new DmzSourcePlan.Selection("Independent",independent));
                java.util.List<String> prefix=new java.util.ArrayList<String>();
                DmzSourcesQuery.Result stopped=db.executeDmzSources(q,optIn(uncovered,a,trigger,prefix,10000));
                for(int count=0;stopped.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.NEXT_CONFLICT;++count) {
                    require(count<4,"uncovered fixture stays bounded");
                    prefix.add(stopped.restrictionOutcome.availableChoices.get(0).identity());
                    stopped=db.executeDmzSources(q,optIn(uncovered,a,trigger,prefix,10000));
                }
                require(stopped.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.STOPPED_UNCOVERED
                        && stopped.restrictionOutcome.availableChoices.isEmpty() && stopped.opinions.isEmpty() && stopped.restartRequired,
                        "uncovered successive history returns a stopped outcome without a complete opinion");
                require(stopped.restrictionOutcome.trace.size()==prefix.size() && stopped.restrictionOutcome.trace.get(prefix.size()-1).after
                        ==DmzSourcesQuery.RestrictionStatus.STOPPED_UNCOVERED,"uncovered outcome retains the executed historical branch trace");
                require(stopped.restrictionOutcome.availableConflicts.isEmpty() && !stopped.restrictionOutcome.trace.get(prefix.size()-1).conflicts.isEmpty(),
                        "uncovered stop retains earlier covered pairs without fabricating the next endpoints");
                DmzSourcesQuery.Result stoppedResult=stopped;
                reject(() -> stoppedResult.chooseRestriction(1),IllegalStateException.class);
                IContextResults.Revision initialGap=create(dir,"InitialGap","!other("+firstRoot+"); !@x other(x) -> "+sign+"cache(x);");
                DmzSourcesQuery.Request initialUncovered=request(pin,base.queryStatement,1000,128,new DmzSourcePlan.Selection("A",a),
                        new DmzSourcePlan.Selection("B",b),new DmzSourcePlan.Selection("InitialGap",initialGap));
                try {
                    db.executeDmzSources(q,optIn(initialUncovered,a,trigger,Collections.<String>emptyList(),10000));
                    throw new AssertionError("Initial uncovered conflict accepted");
                } catch(IllegalStateException refusal) {
                    require(refusal.getMessage().contains("initial conflict") && refusal.getMessage().contains("source history"),
                            "initial coverage gap is a hard refusal before a choice receipt is returned");
                }
            }
            DmzSourcesQuery.Request permuted=request(pin,base.queryStatement,1000,128,new DmzSourcePlan.Selection("B",b),new DmzSourcePlan.Selection("A",a));
            require(db.executeDmzSources(q,optIn(permuted,a,trigger,choices,10000)).restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE,
                    "source request permutation preserves caller proof choices");
            IContextResults.Revision advanced=create(dir,"A","!later(9);");
            require(advanced.getRevision()>a.getRevision() && db.executeDmzSources(q,optIn(base,a,trigger,choices,10000)).restrictionOutcome.status
                    ==DmzSourcesQuery.RestrictionStatus.COMPLETE,"historical choices reacquire exact source pins after HEAD advances");
            reject(() -> new DmzSourcesQuery.RestrictionRequest(a,trigger,Arrays.asList("duplicate","duplicate"),10000),IllegalArgumentException.class);
            reject(() -> new DmzSourcesQuery.RestrictionRequest(a,trigger,Collections.nCopies(129,"too-many"),10000),IllegalArgumentException.class);
            IContextResults.Revision empty=create(dir,"Empty","");
            reject(() -> new DmzSourcesQuery.Request(base.sources,base.policy,base.queryStatement,base.limits,
                    new DmzSourcesQuery.RestrictionRequest(empty,0,Collections.<String>emptyList(),10000)),IllegalArgumentException.class);
            require(state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q,
                    "invalid exact choices and triggers preserve the provider boundary");
        } finally { user.setCurrentMind(q.closeStorage()); }
    }
    private static void growingEvidence(boolean negative) throws Exception {
        Path dir=Files.createTempDirectory("dmz-growing-evidence-");System.setProperty("user.home",dir.toString());
        String sign=negative?"~":"",opposite=negative?"":"~";
        create(dir,"Q","!seed(1); !deep(2); !"+opposite+"cache(2);");
        IContextResults.Revision shortSource=create(dir,"Short","!@x seed(x) -> "+sign+"cache(x);");
        IContextResults.Revision longSource=create(dir,"Long","!@x deep(x) -> middle(x); !@x middle(x) -> "+sign+"cache(x);");
        IContextResults.Revision blocker=create(dir,"Blocker","!"+opposite+"cache(1);");
        long trigger=primaryRule(dir,"Blocker",blocker);
        User user=new User();user.setDatabaseDir(dir.toString()+java.io.File.separator);
        DB db=new DB();db.init(user);Mind initial=new Mind(user);user.setCurrentMind(initial);
        Mind q=(Mind)initial.useStorage("Q");user.setCurrentMind(q);
        try {
            IContextResults.Revision pin=new IContextResults.Revision(db.getContextId(),db.getRevision());
            String state=org.kanger.DmzSourceMaterializationQualification.fingerprint(q);
            DmzSourcesQuery.Request base=request(pin,"?"+sign+"cache(1);",1000,128,
                    new DmzSourcePlan.Selection("Short",shortSource),new DmzSourcePlan.Selection("Long",longSource),
                    new DmzSourcePlan.Selection("Blocker",blocker));
            DmzSourcesQuery.Result first=db.executeDmzSources(q,optIn(projection(base,3),blocker,trigger,Collections.<String>emptyList(),10000));
            require(first.restrictionOutcome.availableChoices.size()==1 && first.restrictionOutcome.availableConflicts.size()==1,
                    "explicit short trigger exposes one first conflict before the longer derivation");
            DmzSourcesQuery.RestrictionConflict firstPair=first.restrictionOutcome.availableConflicts.get(0);
            require(proofNodes(firstPair.positive)+proofNodes(firstPair.negative)==3,
                    "first evidence exactly consumes its three-node allowance");
            DmzSourcesQuery.Request lowReplay=first.chooseRestriction(1);
            String choice=lowReplay.restrictions.choices.get(0);
            try {
                db.executeDmzSources(q,lowReplay);throw new AssertionError("Later larger evidence accepted under first-conflict budget");
            } catch(IllegalStateException refusal) {
                require(refusal.getMessage().contains("Detached source proof budget exceeded"),
                        "later evidence projection refuses after replaying the selected short restriction");
                require(refusal instanceof DmzSourcesQuery.ProjectionRefusal
                        && ((DmzSourcesQuery.ProjectionRefusal)refusal).phase==DmzSourcesQuery.ProjectionPhase.CONFLICT_EVIDENCE
                        && ((DmzSourcesQuery.ProjectionRefusal)refusal).completedChoices==1
                        && ((DmzSourcesQuery.ProjectionRefusal)refusal).allowance==3,
                        "later evidence refusal reports one completed choice and the exact three-node cap");
            }
            require(state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q
                    && db.getRevision()==pin.getRevision() && !db.hasConnectedContexts(),
                    "later projection failure rolls back the selected prefix and preserves provider topology");
            require(first.restrictionOutcome.trace.isEmpty() && first.restrictionOutcome.availableConflicts.get(0).identity().equals(firstPair.identity())
                    && first.chooseRestriction(1).restrictions.choices.equals(lowReplay.restrictions.choices),
                    "failed later projection leaves retained first evidence and exact replay request usable");
            DmzSourcesQuery.Limits enlarged=projection(base,4).limits;
            require(first.chooseRestriction(1,enlarged).restrictions.choices.equals(lowReplay.restrictions.choices),
                    "explicit SDK selection with replacement limits preserves the offered identity");
            DmzSourcesQuery.Request enlargedReplay=lowReplay.withLimits(enlarged);
            require(enlargedReplay!=lowReplay && enlargedReplay.sources==lowReplay.sources && enlargedReplay.policy==lowReplay.policy
                    && enlargedReplay.queryStatement.equals(lowReplay.queryStatement) && enlargedReplay.restrictions==lowReplay.restrictions
                    && enlargedReplay.limits==enlarged && lowReplay.limits.projection==3
                    && enlargedReplay.restrictions.budget==10000 && enlargedReplay.restrictions.choices.equals(Collections.singletonList(choice)),
                    "limit helper preserves exact source pins, trigger, full prefix and workflow budget after refusal");
            reject(() -> lowReplay.withLimits(null),NullPointerException.class);
            org.kanger.CanonicalCommandProcessor processor=new org.kanger.CanonicalCommandProcessor();
            org.kanger.command.CommandParser parser=new org.kanger.command.CommandParser();
            user.setLastDmzResult(first);
            reject(() -> processor.execute(parser.parse("ctx dmz choose 1 projection=3"),user),IllegalStateException.class);
            require(user.getLastDmzResult()==null && state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q)),
                    "Console execution refusal clears the active saved frontier and restores Q");
            user.setLastDmzResult(first);
            DmzSourcesQuery.Result later=processor.execute(parser.parse("dmz choose 1 projection=4"),user).getDmzResult();
            require(later.request.restrictions.choices.equals(enlargedReplay.restrictions.choices)
                    && later.request.sources==first.request.sources && later.request.restrictions.triggerRevision==first.request.restrictions.triggerRevision,
                    "Console enlarged projection replays the same exact SDK request inputs");
            require(later.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.NEXT_CONFLICT && later.restrictionOutcome.trace.size()==1
                    && later.restrictionOutcome.trace.get(0).selected.identity().equals(choice)
                    && later.restrictionOutcome.trace.get(0).conflicts.get(0).identity().equals(firstPair.identity()),
                    "larger allowance reconstructs the same selected prefix and historical pair");
            require(later.restrictionOutcome.availableConflicts.size()==1 && later.restrictionOutcome.availableChoices.size()==1,
                    "later frontier exposes the full remaining recursive pair");
            DmzSourcesQuery.RestrictionConflict laterPair=later.restrictionOutcome.availableConflicts.get(0);
            require(proofNodes(laterPair.positive)+proofNodes(laterPair.negative)==4 && !laterPair.contains(choice),
                    "later evidence needs four nodes and excludes the already selected derivation");
            DmzSourcesQuery.Result done=db.executeDmzSources(q,later.chooseRestriction(1));
            require(done.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE && done.restrictionOutcome.trace.size()==2
                    && done.restrictionOutcome.trace.get(0).conflicts.get(0).identity().equals(firstPair.identity())
                    && done.restrictionOutcome.trace.get(1).conflicts.get(0).identity().equals(laterPair.identity())
                    && Boolean.FALSE.equals(done.opinions.get(0).answer.value),
                    "per-conflict allowance supports both historical pairs and the final restricted native opinion");
            require(later.restrictionOutcome.trace.size()==1 && later.restrictionOutcome.availableConflicts.get(0).identity().equals(laterPair.identity())
                    && state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q,
                    "completed replay preserves earlier detached frontier and physical Q");
            DmzSourcesQuery.Request insufficientVisits=done.request.withLimits(limits(1,128));
            require(insufficientVisits.restrictions.choices.equals(done.request.restrictions.choices)
                    && insufficientVisits.restrictions.choices.size()==2,"lowering visit allowance retains the complete selected prefix");
            reject(() -> db.executeDmzSources(q,insufficientVisits),IllegalArgumentException.class);
            require(state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q,
                    "provider revalidates lowered limits without modifying Q or earlier completed history");
        } finally { user.setCurrentMind(q.closeStorage()); }
    }
    private static void queryProjection() throws Exception {
        Path dir=Files.createTempDirectory("dmz-answer-projection-");System.setProperty("user.home",dir.toString());
        create(dir,"Q","!seed(1); !~cache(1); !seed(2);");
        IContextResults.Revision shortSource=create(dir,"Short","!@x seed(x) -> cache(x);");
        IContextResults.Revision answerSource=create(dir,"Answer","!@x seed(x) -> a(x); !@x a(x) -> b(x); !@x b(x) -> answer(x);");
        long trigger=primaryRule(dir,"Short",shortSource);
        User user=new User();user.setDatabaseDir(dir.toString()+java.io.File.separator);
        DB db=new DB();db.init(user);Mind initial=new Mind(user);user.setCurrentMind(initial);
        Mind q=(Mind)initial.useStorage("Q");user.setCurrentMind(q);
        try {
            IContextResults.Revision pin=new IContextResults.Revision(db.getContextId(),db.getRevision());
            String state=org.kanger.DmzSourceMaterializationQualification.fingerprint(q);
            DmzSourcesQuery.Request base=projection(request(pin,"?answer(2);",1000,128,
                    new DmzSourcePlan.Selection("Short",shortSource),new DmzSourcePlan.Selection("Answer",answerSource)),3);
            DmzSourcesQuery.Result first=db.executeDmzSources(q,optIn(base,shortSource,trigger,Collections.<String>emptyList(),10000));
            DmzSourcesQuery.Request selected=first.chooseRestriction(1);
            try {
                db.executeDmzSources(q,selected);throw new AssertionError("Long native answer projected under three-node cap");
            } catch(DmzSourcesQuery.ProjectionRefusal refusal) {
                require(refusal.phase==DmzSourcesQuery.ProjectionPhase.QUERY_ANSWER && refusal.completedChoices==1
                        && refusal.projectionLimit==3 && refusal.restrictionBudget==10000 && refusal.allowance==3,
                        "completed branch refusal identifies query-answer projection and the actual selected prefix count");
            }
            require(state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q
                    && db.getRevision()==pin.getRevision() && !db.hasConnectedContexts(),"query-answer projection refusal preserves physical Q and provider boundary");
            DmzSourcesQuery.Result done=db.executeDmzSources(q,projection(selected,4));
            require(done.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE && Boolean.TRUE.equals(done.opinions.get(0).answer.value)
                    && done.opinions.get(0).answer.proofs.size()==1 && proofNodes(done.opinions.get(0).answer.proofs.get(0))==4
                    && done.request.restrictions.choices.equals(selected.restrictions.choices),
                    "one more answer node permits the same exact selected branch and four-node native proof");
            require(state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q)) && first.restrictionOutcome.trace.isEmpty(),
                    "successful answer replay leaves Q and retained initial evidence unchanged");
        } finally { user.setCurrentMind(q.closeStorage()); }
    }
    private static void currentChoicesImmutable(DmzSourcesQuery.Request base,DB db,Mind q,IContextResults.Revision a,long trigger) throws Exception {
        db.executeDmzSources(q,optIn(base,a,trigger,Collections.<String>emptyList(),10000)).restrictionOutcome.availableChoices.clear();
    }
    private static int proofNodes(DmzSourcesQuery.Proof proof) {
        int count=1;for(DmzSourcesQuery.Proof premise:proof.premises)count+=proofNodes(premise);return count;
    }
    private static void proofOrigins(DmzSourcesQuery.Proof proof,java.util.Set<java.util.UUID> origins) {
        origins.add(proof.source.revision.getContextId());
        for(DmzSourcesQuery.Proof premise:proof.premises)proofOrigins(premise,origins);
    }
    private static DmzSourcesQuery.Request projection(DmzSourcesQuery.Request base,int nodes) {
        DmzSourcesQuery.Limits l=base.limits;
        return base.withLimits(
                new DmzSourcesQuery.Limits(l.preparation,l.audit,l.lookup,l.recognition,nodes,l.application,l.query,
                        l.depth,l.nodes,l.work,l.maximumVisits));
    }
    private static DmzSourcesQuery.Request optIn(DmzSourcesQuery.Request base,IContextResults.Revision pin,long rule,java.util.List<String> choices,int budget) {
        return new DmzSourcesQuery.Request(base.sources,base.policy,base.queryStatement,base.limits,
                new DmzSourcesQuery.RestrictionRequest(pin,rule,choices,budget));
    }
    private static void causalChoices(boolean negative,boolean recursive,boolean protectedRule) throws Exception {
        Path dir=Files.createTempDirectory("dmz-public-causal-");System.setProperty("user.home",dir.toString());
        String sign=negative?"~":"",opposite=negative?"":"~";
        String chain=recursive?"!@x a(x) -> middle(x); !@x middle(x) -> "+sign+"p(x);":"!@x a(x) -> "+sign+"p(x);";
        create(dir,"Q","!a(1); !b(1); !a(2); !b(3); "+(protectedRule?chain:""));
        IContextResults.Revision a=create(dir,"A",protectedRule?"!@x unused(x) -> unusedResult(x);":chain);
        IContextResults.Revision b=create(dir,"B","!@x b(x) -> "+opposite+"p(x);");
        IContextResults.Revision triggerPin=protectedRule?b:a;
        long trigger=primaryRule(dir,protectedRule?"B":"A",triggerPin);
        User user=new User();user.setDatabaseDir(dir.toString()+java.io.File.separator);DB db=new DB();db.init(user);
        Mind initial=new Mind(user);user.setCurrentMind(initial);final Mind q=(Mind)initial.useStorage("Q");user.setCurrentMind(q);
        try {
            IContextResults.Revision pin=new IContextResults.Revision(db.getContextId(),db.getRevision());
            DmzSourcesQuery.Request base=request(pin,"?p(1);",1000,128,new DmzSourcePlan.Selection("A",a),new DmzSourcePlan.Selection("B",b));
            String state=org.kanger.DmzSourceMaterializationQualification.fingerprint(q);
            DmzSourcesQuery.Result ordinary=db.executeDmzSources(q,optIn(base,triggerPin,trigger,Collections.<String>emptyList(),10000));
            DmzSourcesQuery.Request causal=new DmzSourcesQuery.Request(base.sources,base.policy,base.queryStatement,base.limits,
                    new DmzSourcesQuery.RestrictionRequest(triggerPin,trigger,Collections.<String>emptyList(),10000,
                            DmzSourcesQuery.RestrictionChoiceScope.INITIAL_OBSERVED_CAUSAL_CHAINS));
            DmzSourcesQuery.Result offered=db.executeDmzSources(q,causal);
            require(ordinary.request.restrictions.choiceScope==DmzSourcesQuery.RestrictionChoiceScope.ENDPOINTS
                    && ordinary.restrictionOutcome.availableChoices.size()<=1 && (protectedRule || ordinary.restrictionOutcome.availableChoices.size()==1),"legacy request keeps endpoint-only choice");
            require(offered.restrictionOutcome.availableChoices.size()>=(protectedRule?1:2) && offered.opinions.isEmpty(),"explicit causal scope offers both external sides without choosing");
            boolean yes=false,no=false;
            for(int i=1;i<=offered.restrictionOutcome.availableChoices.size();++i) {
                DmzSourcesQuery.Request chosen=offered.chooseRestriction(i);
                require(chosen.restrictions.choiceScope==causal.restrictions.choiceScope,"saved choice preserves causal scope");
                DmzSourcesQuery.Result result=db.executeDmzSources(q,chosen);
                require(result.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE && result.opinions.size()==1,
                        "explicit causal choice reaches native completed opinion");
                require(result.restrictionProofs.get(0).identity().equals(offered.restrictionOutcome.availableChoices.get(i-1).identity())
                        && !result.opinions.get(0).answer.proofs.isEmpty(),"selected exact public proof and native answer proof survive closure");
                yes|=Boolean.TRUE.equals(result.opinions.get(0).answer.value);no|=Boolean.FALSE.equals(result.opinions.get(0).answer.value);
                require(state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q,
                        "each public sibling preserves physical Q and owner");
                require(offered.restrictionOutcome.availableConflicts.stream().anyMatch(pair->pair.containsObservedChain(result.restrictionProofs.get(0).identity())),
                        "public causal choice remains tied to an actual historical conflict chain");
                {
                    String identity=offered.restrictionOutcome.availableChoices.get(i-1).identity();
                    if(ordinary.restrictionOutcome.availableChoices.stream().noneMatch(proof->proof.identity().equals(identity)))
                        reject(() -> db.executeDmzSources(q,optIn(base,triggerPin,trigger,Collections.singletonList(identity),10000)),IllegalArgumentException.class);
                }
            }
            if(protectedRule) {
                require(negative?no && !yes:yes && !no,"Q production fixes the retained opinion without a Q cut");
                for(DmzSourcesQuery.Proof proof:offered.restrictionOutcome.availableChoices)
                    require(!proof.source.revision.getContextId().equals(pin.getContextId()),"Q productions are absent from public choices");
            } else require(yes && no,"public exact choices expose both signed native opinions");
            require(db.executeDmzSources(q,causal).restrictionOutcome.availableChoices.size()==offered.restrictionOutcome.availableChoices.size(),
                    "fresh replay retains sibling offer after all evaluations");
        } finally {user.setCurrentMind(q.closeStorage());}
    }
    private static void successiveCausal(boolean negative,boolean recursive) throws Exception {
        Path dir=Files.createTempDirectory("dmz-public-successive-causal-");System.setProperty("user.home",dir.toString());
        String sign=negative?"~":"",opposite=negative?"":"~";
        create(dir,"Q","!a(1); !a(2); !a(3); !b(4);");
        IContextResults.Revision a=create(dir,"A",recursive?"!@x a(x) -> middle(x); !@x middle(x) -> "+sign+"p(x);":"!@x a(x) -> "+sign+"p(x);");
        IContextResults.Revision b=create(dir,"B","!b(1); !b(2); !@x b(x) -> "+opposite+"p(x);");
        long trigger=primaryRule(dir,"A",a);
        User user=new User();user.setDatabaseDir(dir.toString()+java.io.File.separator);DB db=new DB();db.init(user);
        Mind initial=new Mind(user);user.setCurrentMind(initial);final Mind q=(Mind)initial.useStorage("Q");user.setCurrentMind(q);
        try {
            IContextResults.Revision pin=new IContextResults.Revision(db.getContextId(),db.getRevision());
            DmzSourcesQuery.Request base=request(pin,"?p(1);",1000,128,new DmzSourcePlan.Selection("A",a),new DmzSourcePlan.Selection("B",b));
            DmzSourcesQuery.Request all=new DmzSourcesQuery.Request(base.sources,base.policy,base.queryStatement,base.limits,
                    new DmzSourcesQuery.RestrictionRequest(a,trigger,Collections.<String>emptyList(),10000,DmzSourcesQuery.RestrictionChoiceScope.OBSERVED_CAUSAL_CHAINS));
            String state=org.kanger.DmzSourceMaterializationQualification.fingerprint(q);
            DmzSourcesQuery.Result first=db.executeDmzSources(q,all);
            require(first.restrictionOutcome.availableChoices.size()>=2,"initial successive offer has both external causal sides");
            java.util.Set<String> opinions=new java.util.HashSet<String>();
            boolean nestedSecond=false;
            for(int i=1;i<=first.restrictionOutcome.availableChoices.size();++i) {
                DmzSourcesQuery.Result second=db.executeDmzSources(q,first.chooseRestriction(i));
                require(second.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.NEXT_CONFLICT
                        && second.restrictionOutcome.trace.size()==1 && second.restrictionOutcome.availableChoices.size()>=2,
                        "first exact cut preserves the second real conflict and both choices");
                for(int j=1;j<=second.restrictionOutcome.availableChoices.size();++j) {
                    DmzSourcesQuery.Request selected=second.chooseRestriction(j);
                    require(selected.restrictions.choiceScope==DmzSourcesQuery.RestrictionChoiceScope.OBSERVED_CAUSAL_CHAINS
                            && selected.restrictions.choices.size()==2,"successive helper retains scope and complete exact prefix");
                    DmzSourcesQuery.Result done=db.executeDmzSources(q,selected);
                    require(done.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE && done.restrictionProofs.size()==2
                            && done.restrictionOutcome.trace.size()==2,"both selected conflicts complete only under the full prefix");
                    String identity=done.restrictionProofs.get(1).identity();
                    require(second.restrictionOutcome.availableConflicts.stream().anyMatch(pair->pair.containsObservedChain(identity)),
                            "second exact cut belongs to the second actual conflict ancestry");
                    nestedSecond|=second.restrictionOutcome.availableConflicts.stream().noneMatch(pair->pair.contains(identity));
                    if(second.restrictionOutcome.availableConflicts.stream().noneMatch(pair->pair.contains(identity))) {
                        DmzSourcesQuery.Request initialOnly=new DmzSourcesQuery.Request(selected.sources,selected.policy,selected.queryStatement,selected.limits,
                                new DmzSourcesQuery.RestrictionRequest(a,trigger,selected.restrictions.choices,10000,
                                        DmzSourcesQuery.RestrictionChoiceScope.INITIAL_OBSERVED_CAUSAL_CHAINS));
                        reject(() -> db.executeDmzSources(q,initialOnly),IllegalArgumentException.class);
                    }
                    DmzSourcesQuery.Request other=new DmzSourcesQuery.Request(selected.sources,selected.policy,"?p(2);",selected.limits,selected.restrictions);
                    DmzSourcesQuery.Result otherResult=db.executeDmzSources(q,other);
                    require(otherResult.restrictionOutcome.status==DmzSourcesQuery.RestrictionStatus.COMPLETE
                            && !done.opinions.get(0).answer.proofs.isEmpty() && !otherResult.opinions.get(0).answer.proofs.isEmpty(),
                            "both atom opinions have native proofs under identical two-cut prefix");
                    opinions.add(done.opinions.get(0).answer.value+"/"+otherResult.opinions.get(0).answer.value);
                    require(state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q)) && user.getCurrentMind()==q,
                            "successive sibling evaluations restore physical Q and owner");
                }
            }
            require(nestedSecond && opinions.size()==4 && !opinions.toString().contains("null"),
                    "later nested causal choices yield all four signed opinion combinations");
            require(first.restrictionOutcome.trace.isEmpty() && db.executeDmzSources(q,all).restrictionOutcome.availableChoices.size()
                    ==first.restrictionOutcome.availableChoices.size(),"saved initial offer remains detached and reproducible");
        } finally {user.setCurrentMind(q.closeStorage());}
    }
    private static long primaryRule(Path dir,String name,IContextResults.Revision revision) throws Exception {
        try(SnapshotMindRuntime snapshot=SnapshotMindRuntime.open(dir.resolve(name),new RevisionRef(revision.getContextId(),revision.getRevision()),"exact-trigger")) {
            return org.kanger.DmzSourceShape.inspect(snapshot.getMind(),100).declarations.get(0).sourceRule;
        }
    }
    private static IContextResults.Revision create(Path dir,String name,String source) throws Exception {
        User user=new User();user.setDatabaseDir(dir.toString()+java.io.File.separator);DB db=new DB();db.init(user);
        Mind mind=new Mind(user);user.setCurrentMind(mind);mind=(Mind)mind.useStorage(name);user.setCurrentMind(mind);
        try { if(!source.isEmpty())require(mind.compile(source,null,false),"fixture "+name); }finally { user.setCurrentMind(mind.closeStorage()); }
        try(ContextSnapshot snapshot=ContextSnapshot.open(dir.resolve(name))) { return new IContextResults.Revision(snapshot.getContextId(),snapshot.getRevision()); }
    }
    private static void reject(Attempt attempt,Class<? extends Exception> type) throws Exception {
        try { attempt.run();throw new AssertionError("Expected rejection "+type); }
        catch(Exception expected) { if(!type.isInstance(expected))throw expected; ++checks; }
    }
    private static void require(boolean value,String reason) { ++checks;if(!value)throw new AssertionError(reason); }
}
