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
        try { run();for(boolean negative:new boolean[]{false,true})for(boolean recursive:new boolean[]{false,true})restrictions(negative,recursive);
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
            require(current.restrictionOutcome.availableConflicts.size()==current.restrictionOutcome.availableChoices.size(),
                    "covered pending conflict preserves both proof endpoints for every source alternative");
            for(DmzSourcesQuery.RestrictionConflict pair:current.restrictionOutcome.availableConflicts) {
                require(pair.positive.conclusion.substring(1).equals(pair.negative.conclusion.substring(2)),
                        "public endpoints are exact opposite signed grounds");
                DmzSourcesQuery.Proof protectedQ=negative?pair.positive:pair.negative;
                require(protectedQ.premises.isEmpty() && protectedQ.source.revision.getContextId().equals(pin.getContextId())
                        && protectedQ.source.revision.getRevision()==pin.getRevision(),"opposing Q endpoint retains its exact primary source pin");
            }
            DmzSourcesQuery.Result initialResult=current;
            reject(() -> initialResult.restrictionOutcome.availableConflicts.clear(),UnsupportedOperationException.class);
            DmzSourcesQuery.RestrictionConflict firstPair=current.restrictionOutcome.availableConflicts.get(0);
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
    private static void currentChoicesImmutable(DmzSourcesQuery.Request base,DB db,Mind q,IContextResults.Revision a,long trigger) throws Exception {
        db.executeDmzSources(q,optIn(base,a,trigger,Collections.<String>emptyList(),10000)).restrictionOutcome.availableChoices.clear();
    }
    private static DmzSourcesQuery.Request optIn(DmzSourcesQuery.Request base,IContextResults.Revision pin,long rule,java.util.List<String> choices,int budget) {
        return new DmzSourcesQuery.Request(base.sources,base.policy,base.queryStatement,base.limits,
                new DmzSourcesQuery.RestrictionRequest(pin,rule,choices,budget));
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
