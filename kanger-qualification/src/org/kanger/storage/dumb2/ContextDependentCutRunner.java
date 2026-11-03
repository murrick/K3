/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentReplayInspection.Policy;
import org.kanger.interfaces.ITerm;
import org.kanger.units.*;

/** Physical namespaces, whole TRUST cuts, independent A/B isolation and original Source alternatives. */
public final class ContextDependentCutRunner {
    private static int checks;
    private interface Attempt{void run()throws Exception;}
    private static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void reject(Attempt a,String why)throws Exception{boolean no=false;try{a.run();}catch(Exception expected){no=true;}require(no,why);}
    private static Policy policy(){return new Policy(2000000,1000,100,64000000,200000);}
    private static final class View {
        final Mind mind;final UUID id;final boolean local;
        View(long id,boolean local)throws Exception{User user=new User();mind=new Mind(user);user.setCurrentMind(mind);this.id=new UUID(0,id);this.local=local;}
        Rule add(String source)throws Exception{return (Rule)mind.compileLine(source,false,new LinkedList<ITerm>());}
        Member member()throws Exception{return new Member(mind,ContextProductionInventory.inspect(mind,id,3,local,Collections.emptyList(),1000));}
        Ref ref(Rule rule){return new Ref(id,rule.getId());}
    }
    private static Map<String,List<Step>> empty(View x,Cut... cuts){Map<String,List<Step>> recipes=new LinkedHashMap<>();recipes.put(ContextDependentCutInspection.local(x.id),Collections.emptyList());for(Cut cut:cuts)recipes.put(cut.key,Collections.emptyList());return recipes;}
    private static Result inspect(View x,List<Cut> cuts,Map<String,List<Step>> recipes)throws Exception{return ContextDependentCutInspection.inspect(x.member(),cuts,new Request(policy(),2000,recipes));}
    private static Set<UUID> contexts(Set<ContextProductionCapture.Source> sources){Set<UUID> out=new HashSet<>();for(ContextProductionCapture.Source source:sources)out.add(source.context);return out;}
    private static void isolation()throws Exception {
        View x=new View(1,true),a=new View(2,false),b=new View(3,false);x.add("!anchor(1);");a.add("!p(1);");b.add("!~p(1);");Cut ca=new Cut(null,Collections.singletonList(a.member())),cb=new Cut(null,Collections.singletonList(b.member()));
        for(List<Cut> order:Arrays.asList(Arrays.asList(ca,cb),Arrays.asList(cb,ca))) {
            Result r=inspect(x,order,empty(x,ca,cb));require(r.cuts.size()==3,"local X and each independent target inspected");for(org.kanger.ContextDependentProductionInspection.Result cut:r.cuts.values())require(cut.finalUnitRefutations.isEmpty(),"independent A/B disagreement is not a cut collision");
            require(contexts(r.cuts.get(ca.key).originals).equals(new HashSet<>(Arrays.asList(x.id,a.id)))&&contexts(r.cuts.get(cb.key).originals).equals(new HashSet<>(Arrays.asList(x.id,b.id))),"each original manifest contains exactly X plus its own target");
        }
        Cut trust=new Cut("joint",Arrays.asList(a.member(),b.member()));Result joined=inspect(x,Collections.singletonList(trust),empty(x,trust));require(joined.cuts.get(trust.key).finalUnitRefutations.size()==1,"same facts conflict only when actual members are one TRUST theory");require(contexts(joined.cuts.get(trust.key).finalUnitRefutations.get(0).sources).equals(new HashSet<>(Arrays.asList(a.id,b.id))),"trusted contradiction keeps both physical Source contexts");
        Map<String,List<Step>> missing=empty(x,ca);reject(()->inspect(x,Arrays.asList(ca,cb),missing),"partial recipe manifest cannot silently omit B");
        reject(()->inspect(x,Arrays.asList(new Cut("joint",Collections.singletonList(a.member())),new Cut("joint",Collections.singletonList(b.member()))),empty(x,trust)),"same TRUST group cannot be split into independent checks");
    }
    private static void sharedWitnesses()throws Exception {
        View x=new View(10,true),a=new View(11,false),b=new View(12,false);Rule hp=x.add("!@u p(u)->tagged(u);"),hq=x.add("!@u q(u)->tagged(u);");x.add("!@u ~(p(u)&&q(u));");Rule pa=a.add("!$u p(u);"),qb=b.add("!$v q(v);");require(pa.getId()==qb.getId(),"physical native Rule IDs deliberately collide across contexts");Cut cut=new Cut("g",Arrays.asList(a.member(),b.member()));Map<String,List<Step>> recipes=empty(x,cut);recipes.put(cut.key,Arrays.asList(new Step(x.ref(hp),Collections.singletonList(Donor.original(a.ref(pa)))),new Step(x.ref(hq),Collections.singletonList(Donor.original(b.ref(qb))))));
        Result r=inspect(x,Collections.singletonList(cut),recipes);org.kanger.ContextDependentProductionInspection.Result result=r.cuts.get(cut.key);
        require(result.products.size()==2&&result.finalUnitRefutations.isEmpty(),"independent existential witnesses need not be one value even inside TRUST");require(result.products.get(0).arguments.get(0).symbolSource.context.equals(a.id)&&result.products.get(1).arguments.get(0).symbolSource.context.equals(b.id),"witness identity is physical Source plus declaration, never native ID alone");
        View duplicate=new View(13,false);Rule p2=duplicate.add("!$u p(u);");Cut same=new Cut("same",Arrays.asList(a.member(),duplicate.member()));Map<String,List<Step>> two=empty(x,same);two.put(same.key,Arrays.asList(new Step(x.ref(hp),Collections.singletonList(Donor.original(a.ref(pa)))),new Step(x.ref(hp),Collections.singletonList(Donor.original(duplicate.ref(p2))))));Result sameResult=inspect(x,Collections.singletonList(same),two);require(sameResult.cuts.get(same.key).originals.size()==5&&sameResult.cuts.get(same.key).products.size()==2,"identical source text from two physical members is not canonicalized into one original");
        require(!sameResult.cuts.get(same.key).products.get(0).arguments.get(0).symbolSource.equals(sameResult.cuts.get(same.key).products.get(1).arguments.get(0).symbolSource),"same existential declaration text retains two independent witness symbols");
    }
    private static void productsAndAlternatives()throws Exception {
        View x=new View(20,true),a=new View(21,false),b=new View(22,false);Rule h=x.add("!@v seed(v)->out(v);");x.add("!~out(1);");Rule sa=a.add("!seed(1);"),sb=b.add("!seed(1);");Cut joined=new Cut("both",Arrays.asList(a.member(),b.member()));Map<String,List<Step>> recipes=empty(x,joined);recipes.put(joined.key,Arrays.asList(new Step(x.ref(h),Collections.singletonList(Donor.original(a.ref(sa)))),new Step(x.ref(h),Collections.singletonList(Donor.original(b.ref(sb))))));Result r=inspect(x,Collections.singletonList(joined),recipes);org.kanger.ContextDependentProductionInspection.Result cut=r.cuts.get(joined.key);
        require(cut.products.size()==2&&cut.finalUnitRefutations.size()==2,"duplicate ordinary fact preserves both physical proof alternatives");Set<UUID> donors=new HashSet<>();for(org.kanger.ContextDependentProductionInspection.Refutation proof:cut.finalUnitRefutations)for(ContextProductionCapture.Source source:proof.sources)if(!source.localX)donors.add(source.context);require(donors.equals(new HashSet<>(Arrays.asList(a.id,b.id))),"refutations retain each original physical donor");
        Cut ca=new Cut(null,Collections.singletonList(a.member())),cb=new Cut(null,Collections.singletonList(b.member()));Map<String,List<Step>> wrong=empty(x,ca,cb);wrong.put(ca.key,Collections.singletonList(new Step(x.ref(h),Collections.singletonList(Donor.original(b.ref(sb))))));reject(()->inspect(x,Arrays.asList(ca,cb),wrong),"an independent cut cannot borrow a donor from its sibling");
        Map<String,List<Step>> proper=empty(x,ca,cb);proper.put(ca.key,Collections.singletonList(new Step(x.ref(h),Collections.singletonList(Donor.original(a.ref(sa))))));proper.put(cb.key,Collections.singletonList(new Step(x.ref(h),Collections.singletonList(Donor.original(b.ref(sb))))));Result separate=inspect(x,Arrays.asList(ca,cb),proper);require(separate.cuts.get(ca.key).finalUnitRefutations.size()==1&&separate.cuts.get(cb.key).finalUnitRefutations.size()==1,"each independent cut qualifies its own production");
        try{r.cuts.clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}try{r.members.get(joined.key).clear();throw new AssertionError();}catch(UnsupportedOperationException expected){checks++;}
    }
    private static void dependent()throws Exception {
        for(boolean later:new boolean[]{false,true}) {
            View x=new View(30,true),a=new View(31,false),b=new View(32,false);Rule p=x.add(later?"!@a @b $u rel(a,u,b);":"!@a $u @b rel(a,u,b);"),h=x.add("!@a @u @b rel(a,u,b)->tagged(a,u,b);");a.add("!@a @u ~(tagged(a,u,1)&&tagged(a,u,2));");b.add("!unrelated(1);");Cut ca=new Cut(null,Collections.singletonList(a.member())),cb=new Cut(null,Collections.singletonList(b.member()));Map<String,List<Step>> recipes=empty(x,ca,cb);Step step=new Step(x.ref(h),Collections.singletonList(Donor.original(x.ref(p))));for(String key:recipes.keySet())recipes.put(key,Collections.singletonList(step));Result r=inspect(x,Arrays.asList(ca,cb),recipes);
            require(r.cuts.get(ca.key).finalUnitRefutations.size()==(later?0:1)&&r.cuts.get(cb.key).finalUnitRefutations.isEmpty(),"dependent final constraint is qualified only in the affected independent cut");require(r.cuts.get(ca.key).products.get(0).arguments.get(1).symbolSource.equals(r.cuts.get(cb.key).products.get(0).arguments.get(1).symbolSource),"shared X function retains one physical identity across private cut arenas");
        }
    }
    private static void foreignHorn()throws Exception {
        View x=new View(60,true),a=new View(61,false),b=new View(62,false);Rule seed=x.add("!p(1);"),horn=a.add("!@v p(v)->q(v);");a.add("!~q(1);");b.add("!anchor(1);");Cut ca=new Cut(null,Collections.singletonList(a.member())),cb=new Cut(null,Collections.singletonList(b.member()));Map<String,List<Step>> recipes=empty(x,ca,cb);recipes.put(ca.key,Collections.singletonList(new Step(a.ref(horn),Collections.singletonList(Donor.original(x.ref(seed))))));Result r=inspect(x,Arrays.asList(cb,ca),recipes);org.kanger.ContextDependentProductionInspection.Result cut=r.cuts.get(ca.key);
        require(cut.products.size()==1&&cut.products.get(0).rule.context.equals(a.id)&&!cut.products.get(0).rule.localX,"foreign Horn head retains actual foreign owner despite overlapping native IDs");require(cut.finalUnitRefutations.size()==1&&contexts(cut.finalUnitRefutations.get(0).sources).equals(new HashSet<>(Arrays.asList(x.id,a.id)))&&r.cuts.get(cb.key).products.isEmpty(),"foreign rule consumes shared X only within its own cut");
    }
    private static void wideAndBoundaries()throws Exception {
        for(int width:new int[]{7,17,65}) {
            View x=new View(40,true),a=new View(41,false);StringBuilder prefix=new StringBuilder(),args=new StringBuilder();for(int i=0;i<width;i++){prefix.append("@x").append(i).append(' ');if(i>0)args.append(',');args.append('x').append(i);}
            Rule h=x.add("!"+prefix+"@u rel("+args+",u)->tagged("+args+",u);");x.add("!"+prefix+"@u ~tagged("+args+",u);");Rule seed=a.add("!"+prefix+"$u rel("+args+",u);");Cut cut=new Cut(null,Collections.singletonList(a.member()));Map<String,List<Step>> recipes=empty(x,cut);recipes.put(cut.key,Collections.singletonList(new Step(x.ref(h),Collections.singletonList(Donor.original(a.ref(seed))))));Result r=inspect(x,Collections.singletonList(cut),recipes);org.kanger.ContextDependentProductionInspection.Result scoped=r.cuts.get(cut.key);org.kanger.ContextDependentProductionInspection.TermView witness=scoped.products.get(0).arguments.get(width);
            require(scoped.finalUnitRefutations.size()==1&&witness.arguments.size()==width&&witness.symbolSource.context.equals(a.id)&&!witness.symbolSource.localX,"wide foreign witness keeps physical source and full dependency tuple");for(int i=0;i<width;i++)require(witness.arguments.get(i)==scoped.products.get(0).arguments.get(i),"qualified dependency coordinate "+i);
            Request low=new Request(policy(),1,recipes);String before=x.member().inventory.fingerprint;reject(()->ContextDependentCutInspection.inspect(x.member(),Collections.singletonList(cut),low),"late cut capacity exhaustion exposes no successful local-prefix result");require(before.equals(x.member().inventory.fingerprint),"failed cut leaves actual X unchanged");
        }
        View x=new View(50,true),a=new View(51,false),b=new View(52,false);x.add("!~p(\"2\");");a.add("!p(2);");b.add("!p(\"2\");");Cut ca=new Cut(null,Collections.singletonList(a.member())),cb=new Cut(null,Collections.singletonList(b.member()));Result r=inspect(x,Arrays.asList(ca,cb),empty(x,ca,cb));require(r.cuts.get(ca.key).finalUnitRefutations.isEmpty()&&r.cuts.get(cb.key).finalUnitRefutations.size()==1,"typed scalar identity survives physical-source namespace projection");
        Member old=a.member();Cut stale=new Cut(null,Collections.singletonList(old));a.add("!changed(9);");reject(()->inspect(x,Collections.singletonList(stale),empty(x,stale)),"stale actual foreign inventory cannot be refreshed silently");
        View local=new View(53,true);local.add("!p(1);");local.add("!~p(1);");Result own=inspect(local,Collections.emptyList(),empty(local));require(own.cuts.size()==1&&own.cuts.get(ContextDependentCutInspection.local(local.id)).finalUnitRefutations.size()==1,"local X check remains mandatory when no foreign targets exist");
    }
    private static final class Fixture implements AutoCloseable {
        final User user=new User();final DB db=new DB();final Mind root;
        Fixture(Path d,String n)throws Exception{user.setDatabaseDir(d+File.separator);user.setUserDir(Files.createDirectories(d.resolve("operator")).toString());db.init(user);Mind m=new Mind(user);user.setCurrentMind(m);root=(Mind)m.useStorage(n);user.setCurrentMind(root);}
        @Override public void close()throws Exception{if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    private static Rule raw(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static void owning()throws Exception {
        Path d=Files.createTempDirectory("dependent-cut-db-");for(String name:new String[]{"A","B"})try(Fixture f=new Fixture(d,name)){require(f.root.compile(name.equals("A")?"!left(1);":"!right(1);",null,false),"physical cut fixture");}
        for(boolean trust:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true})try(Fixture f=new Fixture(d,"X"+trust+reverse)) {
            require(f.root.compile("!anchor(1);",null,false),"source parent");f.db.connectContext(reverse?"B":"A",trust?"joint":null);f.db.connectContext(reverse?"A":"B",trust?"joint":null);long rev=f.db.getRevision();String parent=ContextProductionInventory.inspect(f.root,f.db.getContextId(),rev,true,Collections.emptyList(),1000).fingerprint;
            Mind child=new Mind(f.root);Rule horn=raw(child,"!@v left(v)&&right(v)->p(v);");raw(child,"!~p(1);");List<ContextConnection> topology=f.db.dmzTopology();ContextConnection left=null,right=null;for(ContextConnection c:topology)if(c.layer().getRules().size()>0){for(Object o:c.layer().getRules())if(((Rule)o).getOrigin().equals("!left(1);"))left=c;else if(((Rule)o).getOrigin().equals("!right(1);"))right=c;}
            require(left!=null&&right!=null,"actual initialized physical members located");Map<String,List<Step>> recipes=new LinkedHashMap<>();recipes.put(ContextDependentCutInspection.local(f.db.getContextId()),Collections.emptyList());
            if(trust){List<Donor> donors=new ArrayList<>();for(Domain domain:horn.getTree().get(0))if(!domain.isAntc())donors.add(Donor.original(new Ref(domain.getPredicate(child).getName(child).equals("left")?left.getTarget().getContextId():right.getTarget().getContextId(),0)));recipes.put(ContextDependentCutInspection.trusted("joint"),Collections.singletonList(new Step(new Ref(f.db.getContextId(),horn.getId()),donors)));}
            else for(ContextConnection c:topology)recipes.put(ContextDependentCutInspection.independent(c.getTarget().getContextId()),Collections.emptyList());
            Request request=new Request(policy(),2000,recipes);String pending=ContextProductionInventory.inspect(child,f.db.getContextId(),rev,true,Collections.emptyList(),1000).fingerprint;
            try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)) {
                Result r=lease.inspectDependentCuts(request);require(r.cuts.size()==(trust?2:3),"owning provider fixes exact local/independent/TRUST cut set");if(trust){org.kanger.ContextDependentProductionInspection.Result joined=r.cuts.get(ContextDependentCutInspection.trusted("joint"));require(joined.products.size()==1&&joined.finalUnitRefutations.size()==1,"whole TRUST members jointly produce a qualified local conflict");require(joined.finalUnitRefutations.get(0).sources.size()==4&&contexts(joined.finalUnitRefutations.get(0).sources).size()==3,"both physical TRUST origins and X originals retained");}else for(org.kanger.ContextDependentProductionInspection.Result cut:r.cuts.values())require(cut.products.isEmpty()&&cut.finalUnitRefutations.isEmpty(),"independent targets cannot supply left/right to one rule");
                require(pending.equals(ContextProductionInventory.inspect(child,f.db.getContextId(),rev,true,Collections.emptyList(),1000).fingerprint)&&parent.equals(ContextProductionInventory.inspect(f.root,f.db.getContextId(),rev,true,Collections.emptyList(),1000).fingerprint)&&f.db.getRevision()==rev,"cut inspection is read-only for live source and pending child");
                Map<String,List<Step>> partial=new LinkedHashMap<>(recipes);partial.remove(partial.keySet().iterator().next());reject(()->lease.inspectDependentCuts(new Request(policy(),2000,partial)),"caller cannot omit provider-local or physical cut obligations");
                ContextConnection second=topology.get(1);raw(second.layer(),"!changedsecond(9);");reject(()->lease.inspectDependentCuts(request),"actual second physical member invalidates whole owning baseline");
            }finally{f.root.release(child);}
            require(parent.equals(ContextProductionInventory.inspect(f.root,f.db.getContextId(),rev,true,Collections.emptyList(),1000).fingerprint),"cut-qualified candidate is never published by diagnostic");
        }
    }
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("dependent-cut-").toString());isolation();sharedWitnesses();productsAndAlternatives();dependent();foreignHorn();wideAndBoundaries();owning();System.out.println("CONTEXT_DEPENDENT_CUT_PASS checks="+checks);System.exit(0);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
