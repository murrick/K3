/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextDependentProductionInspection.*;
import org.kanger.ContextDependentReplayInspection.Policy;
import org.kanger.interfaces.ITerm;
import org.kanger.units.*;

/** Signed residuals preserve polarity, dependency functions, complete original ancestry and cut boundaries. */
public final class ContextDependentSignedResidualRunner {
    private static int checks;
    private interface Attempt{void run()throws Exception;}
    private static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void reject(Attempt a,String why)throws Exception{boolean no=false;try{a.run();}catch(Exception expected){no=true;}require(no,why);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static Rule raw(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static Policy policy(){return new Policy(2000000,1000,100,64000000,200000);}
    private static ContextProductionInventory inventory(Mind m)throws Exception{return ContextProductionInventory.inspect(m,new UUID(0,512),3,true,Collections.emptyList(),1000);}
    private static Request request(Step... steps){return new Request(policy(),10000,Arrays.asList(steps));}
    private static Step residual(Mind mind,Rule rule,String kept,Map<String,Donor> donors)throws Exception {
        int retained=-1,index=0;List<Donor> selected=new ArrayList<>();for(Domain domain:rule.getTree().get(0)){String name=domain.getPredicate(mind).getName(mind);if(name.equals(kept)){if(retained>=0)throw new AssertionError("ambiguous test pivot");retained=index;}else selected.add(donors.get(name));index++;}return new Step(rule.getId(),retained,selected);
    }
    private static Result inspect(Mind m,Request request)throws Exception{return ContextDependentProductionInspection.inspect(m,inventory(m),request);}
    private static Set<Long> ids(Set<ContextProductionCapture.Source> sources){Set<Long> ids=new HashSet<>();for(ContextProductionCapture.Source s:sources)ids.add(s.rule);return ids;}
    private static void originalCase()throws Exception {
        for(boolean reverse:new boolean[]{false,true}) {
            Mind m=memory();Rule h,r,n;if(reverse){n=raw(m,"!~p(1);");r=raw(m,"!right(1);");h=raw(m,"!@x left(x)&&right(x)->p(x);");}else{h=raw(m,"!@x left(x)&&right(x)->p(x);");r=raw(m,"!right(1);");n=raw(m,"!~p(1);");}
            Map<String,Donor> donors=new HashMap<>();donors.put("right",Donor.original(r.getId()));donors.put("p",Donor.original(n.getId()));Step step=residual(m,h,"left",donors);String before=inventory(m).fingerprint;Result result=inspect(m,request(step));Row row=result.products.get(0);
            require(result.signed&&!row.positive&&row.predicate.equals("left")&&row.arguments.get(0).value.materialize().equalsTo(r.getDomain().get(0).getValue(m)),"original rule/right/~p produce exactly negative left(1)");require(row.depth==1&&row.retainedLiteral==step.retainedLiteral&&ids(row.sources).equals(new HashSet<>(Arrays.asList(h.getId(),r.getId(),n.getId()))),"retained literal, grade and all three whole Sources audited");
            require(result.originalUnitRefutations.isEmpty()&&result.finalUnitRefutations.isEmpty(),"missing positive left does not create a local collision");require(before.equals(inventory(m).fingerprint),"signed materialization never writes into actual native X");
            Rule next=raw(m,"!@x next(x)->left(x);");Map<String,Donor> prior=new HashMap<>();prior.put("left",Donor.product(0));Step second=residual(m,next,"next",prior);Result chained=inspect(m,request(step,second));require(!chained.products.get(1).positive&&chained.products.get(1).predicate.equals("next")&&chained.products.get(1).depth==2,"negative generated unit stays negative when donated to next residual");
            Rule fact=raw(m,"!next(1);");Result qualified=inspect(m,request(step,second));require(qualified.originalUnitRefutations.isEmpty()&&!qualified.finalUnitRefutations.isEmpty(),"new signed production is qualified against original positive unit");require(qualified.finalUnitRefutations.stream().anyMatch(p->p.depth==3&&ids(p.sources).containsAll(Arrays.asList(h.getId(),r.getId(),n.getId(),next.getId(),fact.getId()))),"final collision retains entire signed production ancestry");
            Rule wrong=raw(m,"!p(1);");Map<String,Donor> invalid=new HashMap<>(donors);invalid.put("p",Donor.original(wrong.getId()));Step sameSign=residual(m,h,"left",invalid);reject(()->inspect(m,request(sameSign)),"same-polarity donor cannot resolve the positive p pivot");
            reject(()->ContextDependentProductionReplay.capture(m,inventory(m),request(step)),"K3HP/v1 cannot silently drop a signed recipe selector");
        }
    }
    private static void polarityOracle()throws Exception {
        int counterexamples=0;for(boolean a:new boolean[]{false,true})for(boolean b:new boolean[]{false,true})if((a||!b)&&a&&b)counterexamples++;require(counterexamples==1,"a OR not-b and positive a do not entail not-b: a=b=true is a concrete model");
        Mind m=memory();Rule clause=raw(m,"!@x b(x)->a(x);"),positive=raw(m,"!a(1);"),negative=raw(m,"!~a(1);");Map<String,Donor> wrong=new HashMap<>();wrong.put("a",Donor.original(positive.getId()));Step bad=residual(m,clause,"b",wrong);reject(()->inspect(m,request(bad)),"explicit truth-table countermodel forbids same-sign residual");Map<String,Donor> right=new HashMap<>();right.put("a",Donor.original(negative.getId()));Result result=inspect(m,request(residual(m,clause,"b",right)));require(!result.products.get(0).positive,"opposite negative a correctly produces negative b");
    }
    private static void dependencies()throws Exception {
        for(String prefix:new String[]{"@a $u @b","@a @b $u","$u @a @b"}) {
            Mind m=memory();Rule seed=raw(m,"!"+prefix+" rel(a,u,b);"),h=raw(m,"!@a @u @b rel(a,u,b)&&allow(a,u)->tagged(a,u,b);"),no=raw(m,"!@a @u @b ~tagged(a,u,b);");Map<String,Donor> donors=new HashMap<>();donors.put("rel",Donor.original(seed.getId()));donors.put("tagged",Donor.original(no.getId()));Step step=residual(m,h,"allow",donors);Result result=inspect(m,request(step));Row row=result.products.get(0);int width=prefix.startsWith("$")?0:prefix.contains("@b $u")?2:1;
            require(!row.positive&&row.arguments.get(1).kind==Kind.APPLICATION&&row.arguments.get(1).arguments.size()==width,"negative output keeps exact global/earlier/later witness dependency");require(row.arguments.get(1).symbolSource.rule==seed.getId(),"negative row retains original seed's witness identity");
            raw(m,"!@a @u allow(a,u);");Result collision=inspect(m,request(step));require(!collision.finalUnitRefutations.isEmpty(),"signed final qualification handles dependent negative product versus positive universal");
        }
        for(int width:new int[]{7,17,65}) {
            Mind m=memory();StringBuilder prefix=new StringBuilder(),args=new StringBuilder();for(int i=0;i<width;i++){prefix.append("@x").append(i).append(' ');if(i>0)args.append(',');args.append('x').append(i);}
            Rule seed=raw(m,"!"+prefix+"$u rel("+args+",u);"),h=raw(m,"!"+prefix+"@u rel("+args+",u)&&allow("+args+",u)->tagged("+args+",u);"),no=raw(m,"!"+prefix+"@u ~tagged("+args+",u);");Map<String,Donor> donors=new HashMap<>();donors.put("rel",Donor.original(seed.getId()));donors.put("tagged",Donor.original(no.getId()));Row row=inspect(m,request(residual(m,h,"allow",donors))).products.get(0);require(!row.positive&&row.arguments.get(width).arguments.size()==width,"wide signed witness tuple intact");for(int i=0;i<width;i++)require(row.arguments.get(width).arguments.get(i)==row.arguments.get(i),"signed shared dependency coordinate "+i);
        }
    }
    private static void residualClausesAndBounds()throws Exception {
        Mind m=memory();Rule c=raw(m,"!@x ~(a(x)&&b(x));"),a=raw(m,"!a(1);");Map<String,Donor> d=new HashMap<>();d.put("a",Donor.original(a.getId()));Step step=residual(m,c,"b",d);Result result=inspect(m,request(step));require(!result.products.get(0).positive&&result.products.get(0).predicate.equals("b"),"headless clause emits a negative unit residual");
        raw(m,"!b(1);");Result conflict=inspect(m,request(step));require(!conflict.finalUnitRefutations.isEmpty(),"negative residual opposed by original unit is checked");String before=inventory(m).fingerprint;reject(()->inspect(m,new Request(policy(),1,Collections.singletonList(step))),"signed state cap exposes no successful prefix");require(before.equals(inventory(m).fingerprint),"failed signed inspection is read-only");
        reject(()->inspect(m,request(new Step(c.getId(),100,Collections.singletonList(Donor.original(a.getId()))))),"retained index belongs to exact whole clause");reject(()->inspect(m,request(new Step(c.getId(),step.retainedLiteral,Collections.emptyList()))),"all nonretained literals must be resolved");
        Mind empty=memory();Rule zero=raw(empty,"!x()->y();"),no=raw(empty,"!~y();");Map<String,Donor> donor=new HashMap<>();donor.put("y",Donor.original(no.getId()));Row nullary=inspect(empty,request(residual(empty,zero,"x",donor))).products.get(0);require(!nullary.positive&&nullary.arguments.isEmpty(),"nullary negative residual needs no fake value");
    }
    private static final class Fixture implements AutoCloseable {
        final User user=new User();final DB db=new DB();final Mind root;
        Fixture(Path d,String name)throws Exception{user.setDatabaseDir(d+File.separator);user.setUserDir(Files.createDirectories(d.resolve("operator")).toString());db.init(user);Mind m=new Mind(user);user.setCurrentMind(m);root=(Mind)m.useStorage(name);user.setCurrentMind(root);}
        @Override public void close()throws Exception{if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    private static void owning()throws Exception {
        Path dir=Files.createTempDirectory("signed-residual-db-");try(Fixture a=new Fixture(dir,"A")){require(a.root.compile("!left(1); !p(1);",null,false),"A native original fixture");}try(Fixture b=new Fixture(dir,"B")){require(b.root.compile("!right(1); !~p(1);",null,false),"B native original fixture");}
        for(boolean reverse:new boolean[]{false,true})try(Fixture f=new Fixture(dir,"X"+reverse)) {
            require(f.root.compile("!anchor(1);",null,false),"actual parent");f.db.connectContext(reverse?"B":"A");f.db.connectContext(reverse?"A":"B");long rev=f.db.getRevision();String parent=ContextProductionInventory.inspect(f.root,f.db.getContextId(),rev,true,Collections.emptyList(),1000).fingerprint;Mind child=new Mind(f.root);Rule h=raw(child,"!@x left(x)&&right(x)->p(x);");ContextConnection b=null;Rule right=null,no=null;
            for(ContextConnection connection:f.db.dmzTopology())for(Object o:connection.layer().getRules()){Rule r=(Rule)o;if(r.getOrigin().equals("!right(1);")){b=connection;right=r;}if(r.getOrigin().equals("!~p(1);"))no=r;}
            require(b!=null&&right!=null&&no!=null,"exact initialized B originals");List<ContextDependentCutInspection.Donor> body=new ArrayList<>();int retained=-1,index=0;for(Domain domain:h.getTree().get(0)){String name=domain.getPredicate(child).getName(child);if(name.equals("left"))retained=index;else body.add(ContextDependentCutInspection.Donor.original(new ContextDependentCutInspection.Ref(b.getTarget().getContextId(),name.equals("right")?right.getId():no.getId())));index++;}
            Map<String,List<ContextDependentCutInspection.Step>> recipes=new LinkedHashMap<>();recipes.put(ContextDependentCutInspection.local(f.db.getContextId()),Collections.emptyList());for(ContextConnection connection:f.db.dmzTopology())recipes.put(ContextDependentCutInspection.independent(connection.getTarget().getContextId()),Collections.emptyList());recipes.put(ContextDependentCutInspection.independent(b.getTarget().getContextId()),Collections.singletonList(new ContextDependentCutInspection.Step(new ContextDependentCutInspection.Ref(f.db.getContextId(),h.getId()),retained,body)));
            String pending=ContextProductionInventory.inspect(child,f.db.getContextId(),rev,true,Collections.emptyList(),1000).fingerprint;
            try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)) {
                ContextDependentCutInspection.Result r=lease.inspectDependentCuts(new ContextDependentCutInspection.Request(policy(),10000,recipes));Row row=r.cuts.get(ContextDependentCutInspection.independent(b.getTarget().getContextId())).products.get(0);require(!row.positive&&row.predicate.equals("left")&&row.sources.size()==3,"actual X-B cut produces qualified negative left with all Sources");for(Map.Entry<String,ContextDependentProductionInspection.Result> cut:r.cuts.entrySet())if(!cut.getKey().equals(ContextDependentCutInspection.independent(b.getTarget().getContextId())))require(cut.getValue().products.isEmpty(),"negative B product is not silently imported into A or local X");require(pending.equals(ContextProductionInventory.inspect(child,f.db.getContextId(),rev,true,Collections.emptyList(),1000).fingerprint)&&f.db.getRevision()==rev,"signed cut remains read-only");
            }finally{f.root.release(child);}
            require(parent.equals(ContextProductionInventory.inspect(f.root,f.db.getContextId(),rev,true,Collections.emptyList(),1000).fingerprint),"no admission/publication from negative-production diagnostic");
        }
    }
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("signed-residual-").toString());originalCase();polarityOracle();dependencies();residualClausesAndBounds();owning();System.out.println("CONTEXT_DEPENDENT_SIGNED_RESIDUAL_PASS checks="+checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
