/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextDependentReplayInspection.Policy;
import org.kanger.ContextDependentProductionInspection.*;
import org.kanger.interfaces.ITerm;
import org.kanger.units.*;

/** Native whole Horn rows -> typed dependent candidate units -> original/final unit refutations. */
public final class ContextDependentProductionRunner {
    private static int checks;
    private static final UUID CONTEXT=new UUID(0,509);
    private interface Attempt{void run()throws Exception;}
    private static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void reject(Attempt attempt,String why)throws Exception{boolean no=false;try{attempt.run();}catch(Exception expected){no=true;}require(no,why);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static Rule raw(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static Policy policy(){return new Policy(2000000,1000,100,64000000,200000);}
    private static Request request(Step... steps){return new Request(policy(),2000,Arrays.asList(steps));}
    private static ContextProductionInventory inventory(Mind m)throws Exception{return ContextProductionInventory.inspect(m,CONTEXT,3,true,Collections.emptyList(),1000);}
    private static Result inspect(Mind m,Request request)throws Exception{return ContextDependentProductionInspection.inspect(m,inventory(m),request);}
    private static Step step(Mind m,Rule rule,Map<String,Donor> byPredicate)throws Exception{List<Donor> donors=new ArrayList<>();for(Domain domain:rule.getTree().get(0))if(!domain.isAntc())donors.add(byPredicate.get(domain.getPredicate(m).getName(m)));return new Step(rule.getId(),donors);}
    private static Step one(Rule rule,Donor donor){return new Step(rule.getId(),Collections.singletonList(donor));}
    private static Set<Long> ids(Set<ContextProductionCapture.Source> sources){Set<Long> ids=new HashSet<>();for(ContextProductionCapture.Source s:sources)ids.add(s.rule);return ids;}
    private static void prefixes()throws Exception {
        String[] roots={"!@a $u @b rel(a,u,b);","!@a @b $u rel(a,u,b);","!$u @a @b rel(a,u,b);","!@i $w @j rel(i,w,j);"};
        for(int i=0;i<roots.length;i++)for(boolean cross:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true}) {
            Mind m=memory();String constraint=cross?"!@u ~(tagged(1,u,1)&&tagged(2,u,1));":"!@a @u ~(tagged(a,u,1)&&tagged(a,u,2));";
            Rule p,h,c;if(reverse){c=raw(m,constraint);h=raw(m,"!@a @u @b rel(a,u,b)->tagged(a,u,b);");p=raw(m,roots[i]);}else{p=raw(m,roots[i]);h=raw(m,"!@a @u @b rel(a,u,b)->tagged(a,u,b);");c=raw(m,constraint);}
            String before=inventory(m).fingerprint;int terms=m.getTerms().size();Result r=inspect(m,request(one(h,Donor.original(p.getId()))));boolean conflict=cross?i==2:i!=1;
            require(r.products.size()==1&&r.originalUnitRefutations.isEmpty(),"no fake tagged fact before Horn production");
            require(r.finalUnitRefutations.size()==(conflict?1:0),"qualified derived units distinguish global/earlier/later scope in both orders");
            Row row=r.products.get(0);require(row.predicate.equals("tagged")&&row.depth==1&&ids(row.sources).equals(new HashSet<>(Arrays.asList(p.getId(),h.getId()))),"actual Horn product retains complete original support and depth");
            TermView witness=row.arguments.get(1);require(witness.kind==Kind.APPLICATION&&witness.arguments.size()==(i==2?0:i==1?2:1)&&witness.symbolSource.rule==p.getId(),"production keeps original witness symbol and dependency tuple");
            if(conflict)require(ids(r.finalUnitRefutations.get(0).sources).equals(new HashSet<>(Arrays.asList(p.getId(),h.getId(),c.getId())))&&r.finalUnitRefutations.get(0).depth==2,"new collision requires production and both whole originals");
            require(before.equals(inventory(m).fingerprint)&&terms==m.getTerms().size(),"materializing typed candidate rows leaves actual native factory/dictionary unchanged");
        }
    }
    private static void chainsAndRefinement()throws Exception {
        for(String value:new String[]{"2","\"2\""}) {
            Mind m=memory();Rule p=raw(m,"!@a $u @b rel(a,u,b);"),q=raw(m,"!choose("+value+");"),h=raw(m,"!@a @u @b rel(a,u,b)&&choose(a)->mid(a,u,b);"),next=raw(m,"!@a @u @b mid(a,u,b)->tagged(a,u,b);"),c=raw(m,"!@a @u ~(tagged(a,u,1)&&tagged(a,u,2));");
            Map<String,Donor> body=new HashMap<>();body.put("rel",Donor.original(p.getId()));body.put("choose",Donor.original(q.getId()));Result r=inspect(m,request(step(m,h,body),one(next,Donor.product(0))));
            Row first=r.products.get(0),last=r.products.get(1);require(first.arguments.get(0).kind==Kind.VALUE&&first.arguments.get(1).arguments.get(0)==first.arguments.get(0),"later body binding refines earlier dependent witness");
            require(first.arguments.get(0).value.semanticallyEquals(SemanticTermSnapshot.capture(q.getDomain().get(0).getValue(m))),"typed binding survives derivation and independent final audit");
            require(last.depth==2&&r.finalUnitRefutations.size()==1&&r.finalUnitRefutations.get(0).depth==3,"earlier generated row is a real donor in a second Horn inference");
            require(ids(last.sources).equals(new HashSet<>(Arrays.asList(p.getId(),q.getId(),h.getId(),next.getId()))),"product chain preserves all underlying original Sources");
            require(last.arguments.get(1).symbolSource.rule==p.getId(),"derived donor never replaces original Skolem symbol with generated owner");
            reject(()->inspect(m,request(one(next,Donor.product(0)))),"forward/self product references cannot impersonate proved donors");
        }
        Mind m=memory();Rule a=raw(m,"!pair(1,2);"),b=raw(m,"!pair(2,1);"),h=raw(m,"!@x @y pair(x,y)&&pair(y,x)->ok(x,y);");
        reject(()->inspect(m,request(new Step(h.getId(),Arrays.asList(Donor.original(a.getId()),Donor.original(a.getId()))))),"whole body MGU rejects split/inconsistent variable correlation");
        Result r=inspect(m,request(new Step(h.getId(),Arrays.asList(Donor.original(a.getId()),Donor.original(b.getId())))));require(r.products.get(0).arguments.size()==2&&r.products.get(0).sources.size()==3,"correlated multi-premise production includes both actual donors");
    }
    private static void alternativesAndBounds()throws Exception {
        Mind m=memory();Rule p=raw(m,"!@a $u @b rel(a,u,b);"),h=raw(m,"!@a @u @b rel(a,u,b)->tagged(a,u,b);"),aux=raw(m,"!aux();"),again=raw(m,"!@a @u @b tagged(a,u,b)&&aux()->tagged(a,u,b);"),c=raw(m,"!@a @u ~(tagged(a,u,1)&&tagged(a,u,2));");
        Map<String,Donor> body=new HashMap<>();body.put("tagged",Donor.product(0));body.put("aux",Donor.original(aux.getId()));Request request=request(one(h,Donor.original(p.getId())),step(m,again,body));Result r=inspect(m,request);
        require(r.products.size()==2&&r.products.get(1).sources.containsAll(r.products.get(0).sources)&&r.products.get(1).sources.size()==4,"Source superset production is retained separately");
        require(r.finalUnitRefutations.size()==4,"all finite unit-donor alternatives survive final qualification");Set<Integer> sizes=new HashSet<>();for(Refutation proof:r.finalUnitRefutations)sizes.add(proof.sources.size());require(sizes.equals(new HashSet<>(Arrays.asList(3,5))),"small and superset collision supports are not minimized away");
        String before=inventory(m).fingerprint;reject(()->inspect(m,new Request(policy(),1,request.steps)),"state exhaustion yields no partially qualified candidate");require(before.equals(inventory(m).fingerprint),"bounded qualification failure never writes candidate units into native X");
        try{r.products.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}try{r.products.get(0).arguments.get(1).arguments.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
        raw(m,"!@x unsupported(x)->other(x+1);");reject(()->inspect(m,request),"later unsupported original refuses whole preparation despite earlier usable sources");
        Mind n=memory();Rule plain=raw(n,"!plain();"),head=raw(n,"!plain()->done();");raw(n,"!~done();");Result empty=inspect(n,request(one(head,Donor.original(plain.getId()))));require(empty.products.get(0).arguments.isEmpty()&&empty.finalUnitRefutations.size()==1,"nullary Horn production and final qualification need no fake values");
    }
    private static void universalAndTamper()throws Exception {
        Mind m=memory();Rule seed=raw(m,"!seed(1);"),h=raw(m,"!@a @b seed(a)->out(a,b);");Result r=inspect(m,request(one(h,Donor.original(seed.getId()))));
        require(r.products.get(0).arguments.get(1).kind==Kind.VARIABLE,"head-only universal stays arbitrary, not an invented existential value");
        ContextProductionInventory old=inventory(m);Request recipe=request(one(h,Donor.original(seed.getId())));
        reject(()->inspect(m,new Request(new Policy(1,1000,100,64000000,200000),2000,recipe.steps)),"combined graph/header byte limit cannot expose candidate prefix");
        ContextProductionInventory other=ContextProductionInventory.inspect(m,new UUID(0,510),3,true,Collections.emptyList(),1000);Result changed=ContextDependentProductionInspection.inspect(m,other,recipe);require(!r.auditDigest.equals(changed.auditDigest),"audit digest binds exact original context/view, not only graph shape");
        h.setOrigin(m.getTerms().add("!@a @b seed(a)->other(a,b);"));reject(()->inspect(m,recipe),"native Horn head/text mismatch refuses even with fresh inventory");
        reject(()->ContextDependentProductionInspection.inspect(m,old,recipe),"stale expected original view cannot be refreshed silently");
    }
    private static void wide()throws Exception {
        for(int width:new int[]{7,17,65}) {
            Mind m=memory();StringBuilder prefix=new StringBuilder(),args=new StringBuilder();for(int i=0;i<width;i++){prefix.append("@x").append(i).append(' ');if(i>0)args.append(',');args.append("x").append(i);}
            Rule p=raw(m,"!"+prefix+"$u rel("+args+",u);"),h=raw(m,"!"+prefix+"@u rel("+args+",u)->tagged("+args+",u);");raw(m,"!"+prefix+"@u ~tagged("+args+",u);");
            Result r=inspect(m,request(one(h,Donor.original(p.getId()))));TermView witness=r.products.get(0).arguments.get(width);require(witness.arguments.size()==width&&r.finalUnitRefutations.size()==1,"full dependency tuple survives production at width "+width);
            for(int i=0;i<width;i++)require(witness.arguments.get(i)==r.products.get(0).arguments.get(i),"shared coordinate in materialized production "+i);
        }
    }
    private static final class Fixture implements AutoCloseable {
        final User user=new User();final DB db=new DB();final Mind root;
        Fixture(Path d,String name)throws Exception{user.setDatabaseDir(d+File.separator);user.setUserDir(Files.createDirectories(d.resolve("operator")).toString());db.init(user);Mind m=new Mind(user);user.setCurrentMind(m);root=(Mind)m.useStorage(name);user.setCurrentMind(root);}
        @Override public void close()throws Exception{if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    private static void owning()throws Exception {
        Path d=Files.createTempDirectory("dependent-products-db-");for(String name:new String[]{"A","B"})try(Fixture f=new Fixture(d,name)){require(f.root.compile("!"+name.toLowerCase()+"(1);",null,false),"physical member fixture");}
        for(boolean trust:new boolean[]{false,true})try(Fixture f=new Fixture(d,"X"+trust)) {
            require(f.root.compile("!anchor(1);",null,false),"physical parent fixture");f.db.connectContext("A",trust?"t":null);f.db.connectContext("B",trust?"t":null);long rev=f.db.getRevision();String before=ContextProductionInventory.inspect(f.root,f.db.getContextId(),rev,true,Collections.emptyList(),1000).fingerprint;
            Mind child=new Mind(f.root);Rule p=raw(child,"!@a $u @b rel(a,u,b);"),h=raw(child,"!@a @u @b rel(a,u,b)->tagged(a,u,b);");raw(child,"!@a @u ~(tagged(a,u,1)&&tagged(a,u,2));");Request request=request(one(h,Donor.original(p.getId())));String prepared=ContextProductionInventory.inspect(child,f.db.getContextId(),rev,true,Collections.emptyList(),1000).fingerprint;
            try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)) {
                Result r=lease.inspectDependentProductions(request);require(r.products.size()==1&&r.finalUnitRefutations.size()==1,"actual owned pending candidate materializes typed product then detects final local conflict");
                for(ContextProductionCapture.Source source:r.products.get(0).sources)require(source.context.equals(f.db.getContextId())&&source.revision==rev&&source.view.equals("inventory:"+prepared),"production source ownership comes from actual pending provider view");
                require(prepared.equals(ContextProductionInventory.inspect(child,f.db.getContextId(),rev,true,Collections.emptyList(),1000).fingerprint)&&before.equals(ContextProductionInventory.inspect(f.root,f.db.getContextId(),rev,true,Collections.emptyList(),1000).fingerprint)&&f.db.getRevision()==rev,"no scalar CVar/legacy generated Rule or durable change escapes private candidate");
                raw(child,"!changed(9);");reject(()->lease.inspectDependentProductions(request),"stale actual child cannot reuse production inspection authority");
            }finally{f.root.release(child);}
            require(before.equals(ContextProductionInventory.inspect(f.root,f.db.getContextId(),rev,true,Collections.emptyList(),1000).fingerprint),"release preserves physical parent after refuted candidate");
        }
    }
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("dependent-production-").toString());prefixes();chainsAndRefinement();alternativesAndBounds();universalAndTamper();wide();owning();System.out.println("CONTEXT_DEPENDENT_PRODUCTION_PASS checks="+checks);System.exit(0);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
