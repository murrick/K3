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
        try { run();System.out.println("DMZ_SOURCES_QUERY_PASS checks="+checks);System.exit(0); }
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
