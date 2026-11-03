/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextDependentCutInspection.*;
import org.kanger.ContextDependentRoundInspection.*;
import org.kanger.ContextDependentRoundInspection.Request;
import org.kanger.ContextDependentReplayInspection.Policy;
import org.kanger.interfaces.ITerm;
import org.kanger.units.*;

public final class ContextDependentDiscoveryRunner {
    private static int checks;
    private interface Attempt{void run()throws Exception;}
    private static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    private static void reject(Attempt a,String why)throws Exception{boolean no=false;try{a.run();}catch(Exception expected){no=true;}require(no,why);}
    private static Policy policy(){return new Policy(2000000,1000,1000,64000000,200000);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static Rule raw(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static Member member(Mind m,int id,boolean x)throws Exception{return new Member(m,ContextProductionInventory.inspect(m,new UUID(0,id),3,x,Collections.emptyList(),1000));}
    private static Request input(Round... rounds){return new Request(policy(),10000,Arrays.asList(rounds));}
    private static Discovery discover(Member x,List<Cut> cuts,Round... rounds)throws Exception{return ContextDependentRoundInspection.discoverNext(x,cuts,input(rounds));}
    private static void oracle()throws Exception{
        String[] names={"a","b","r"};
        for(int combination=0;combination<27;combination++){
            Mind m=memory();raw(m,"!a()&&b()->r();");int[] states=new int[3];int n=combination;for(int i=0;i<3;i++){states[i]=n%3;n/=3;if(states[i]!=0)raw(m,"!"+(states[i]==2?"~":"")+names[i]+"();");}Member x=member(m,100+combination,true);Discovery found=discover(x,Collections.emptyList());int expected=(states[1]==1&&states[2]==2?1:0)+(states[0]==1&&states[2]==2?1:0)+(states[0]==1&&states[1]==1?1:0);require(found.matchedRecipes==expected,"independent complete nullary pivot oracle "+combination);List<ContextDependentProductionInspection.Row> rows=found.checked.barriers.get(0).cuts.get(ContextDependentCutInspection.local(x.inventory.context)).products;require(rows.size()==expected,"every oracle match replayed");for(ContextDependentProductionInspection.Row row:rows){require(row.sources.size()==3,"whole rule and both exact unit donors retained");for(int model=0;model<8;model++){boolean a=(model&1)!=0,b=(model&2)!=0,r=(model&4)!=0;boolean premises=(!a||!b||r);for(int i=0;i<3;i++)if(states[i]!=0)premises&=(((model>>i)&1)!=0)==(states[i]==1);boolean value=row.predicate.equals("a")?a:row.predicate.equals("b")?b:r;require(!premises||value==row.positive,"truth-table validates every automatically discovered signed residual");}}require(found.next.recipes.size()==1,"complete local barrier even without matches");
        }
    }
    private static void correlationsAndBounds()throws Exception{
        Mind m=memory();raw(m,"!@x a(x)&&b(x)->r(x);");raw(m,"!a(1);");raw(m,"!a(2);");raw(m,"!b(1);");raw(m,"!b(3);");Member x=member(m,201,true);Discovery d=discover(x,Collections.emptyList());require(d.matchedRecipes==1,"Cartesian donor search preserves shared variable correlations");require(d.checked.barriers.get(0).cuts.values().iterator().next().products.get(0).predicate.equals("r"),"only matching joined tuple materializes");String before=x.inventory.fingerprint;
        reject(()->ContextDependentRoundInspection.discoverNext(x,Collections.emptyList(),new Request(policy(),1,Collections.emptyList())),"search state exhaustion refuses complete output");require(before.equals(ContextProductionInventory.inspect(m,x.inventory.context,3,true,Collections.emptyList(),1000).fingerprint),"discovery failure is read-only");reject(()->d.next.recipes.clear(),"discovered recipes immutable");
        Mind chain=memory();raw(chain,"!seed(1);");raw(chain,"!@x seed(x)->p(x);");raw(chain,"!@x p(x)->q(x);");Member c=member(chain,202,true);Discovery first=discover(c,Collections.emptyList());require(first.matchedRecipes==1,"new same-round head is excluded from discovery donor pool");Discovery second=discover(c,Collections.emptyList(),first.next);require(second.matchedRecipes==2,"next frozen pool finds old application and newly enabled q");require(second.checked.barriers.get(1).cuts.values().iterator().next().products.stream().anyMatch(row->row.predicate.equals("q")),"generated donor recipe replays at next barrier");reject(()->ContextDependentRoundInspection.discoverNext(c,Collections.emptyList(),new Request(new Policy(2000000,1000,1,64000000,200000),10000,Collections.singletonList(first.next))),"no barrier/ancestry room refuses rather than claiming coverage");
    }
    private static void alternativesAndDependencies()throws Exception{
        Mind xm=memory(),am=memory(),bm=memory();raw(xm,"!@x seed(x)->p(x);");raw(am,"!seed(1);");raw(bm,"!seed(1);");Member x=member(xm,301,true),a=member(am,302,false),b=member(bm,303,false);Cut ca=new Cut(null,Collections.singletonList(a)),cb=new Cut(null,Collections.singletonList(b));Discovery distinct=discover(x,Arrays.asList(cb,ca));require(distinct.matchedRecipes==2,"independent cuts retain two physical proof alternatives");require(distinct.checked.barriers.get(0).common.size()==2,"both equal conclusions retain their original proof tickets");Cut trust=new Cut("both",Arrays.asList(b,a));Discovery together=discover(x,Collections.singletonList(trust));require(together.matchedRecipes==2&&together.checked.barriers.get(0).common.size()==2,"TRUST search enumerates duplicate-text/native-ID donors separately");
        Mind px=memory(),pa=memory(),pb=memory();raw(px,"!anchor(1);");raw(pa,"!seed(1);");raw(pa,"!@x seed(x)->p(x);");raw(pb,"!~p(1);");Member xx=member(px,311,true),aa=member(pa,312,false),bb=member(pb,313,false);List<Cut> privateCuts=Arrays.asList(new Cut(null,Collections.singletonList(aa)),new Cut(null,Collections.singletonList(bb)));Discovery privateFirst=discover(xx,privateCuts);require(privateFirst.matchedRecipes==1&&privateFirst.checked.barriers.get(0).common.isEmpty(),"automatically discovered foreign-only consequence remains private");Discovery privateNext=discover(xx,privateCuts,privateFirst.next);require(privateNext.checked.barriers.get(1).cuts.values().stream().allMatch(c->c.finalUnitRefutations.isEmpty()),"automatic next round never borrows private sibling p to refute B negative p");
        for(int width:new int[]{1,7,17,65}){
            Mind m=memory();StringBuilder prefix=new StringBuilder(),args=new StringBuilder();for(int i=0;i<width;i++){prefix.append("@x").append(i).append(' ');if(i>0)args.append(',');args.append('x').append(i);}Rule seed=raw(m,"!"+prefix+"$u @b rel("+args+",u,b);");raw(m,"!"+prefix+"@u @b rel("+args+",u,b)&&allow("+args+",u)->tagged("+args+",u,b);");raw(m,"!"+prefix+"@u @b ~tagged("+args+",u,b);");Member local=member(m,400+width,true);Discovery found=discover(local,Collections.emptyList());require(found.matchedRecipes==1,"complete dependent residual enumeration width "+width);ContextDependentProductionInspection.Row row=found.checked.barriers.get(0).cuts.values().iterator().next().products.get(0);require(!row.positive&&row.predicate.equals("allow")&&row.arguments.get(width).arguments.size()==width,"automatic result preserves negative polarity and dependency rank");require(row.arguments.get(width).symbolSource.rule==seed.getId(),"automatic witness remains owned by original declaration");for(int i=0;i<width;i++)require(row.arguments.get(width).arguments.get(i)==row.arguments.get(i),"automatic witness coordinate shares whole MGU "+i);
        }
    }
    private static final class Fixture implements AutoCloseable{
        final User user=new User();final DB db=new DB();final Mind root;
        Fixture(Path d,String name)throws Exception{user.setDatabaseDir(d+File.separator);user.setUserDir(Files.createDirectories(d.resolve("operator")).toString());db.init(user);Mind m=new Mind(user);user.setCurrentMind(m);root=(Mind)m.useStorage(name);user.setCurrentMind(root);}
        @Override public void close()throws Exception{if(user.getCurrentMind().isStorageUsed())user.setCurrentMind(user.getCurrentMind().closeStorage());}
    }
    private static void owning()throws Exception{
        Path dir=Files.createTempDirectory("discovery-db-");try(Fixture a=new Fixture(dir,"A")){require(a.root.compile("!left(1); !p(1);",null,false),"A fixture");}try(Fixture b=new Fixture(dir,"B")){require(b.root.compile("!right(1); !~p(1);",null,false),"B fixture");}
        for(boolean reverse:new boolean[]{false,true})try(Fixture f=new Fixture(dir,"X"+reverse)){
            require(f.root.compile("!anchor(1);",null,false),"parent fixture");f.db.connectContext(reverse?"B":"A");f.db.connectContext(reverse?"A":"B");long revision=f.db.getRevision();Mind child=new Mind(f.root);raw(child,"!@x left(x)&&right(x)->p(x);");String before=ContextProductionInventory.inspect(child,f.db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint;
            try(ContextPendingProductionSnapshotSession lease=ContextPendingProductionSnapshotSession.open(f.db,child,1000)){
                Discovery first=lease.discoverDependentRound(input());require(first.matchedRecipes==1&&first.checked.barriers.get(0).common.size()==1,"actual complete first round discovers negative left without caller recipe");require(first.checked.barriers.get(0).cuts.values().stream().allMatch(c->c.finalUnitRefutations.isEmpty()),"no same-barrier sibling leakage");Discovery second=lease.discoverDependentRound(input(first.next));require(second.checked.barriers.get(1).cuts.values().stream().anyMatch(c->c.finalUnitRefutations.stream().anyMatch(proof->proof.sources.size()==4)),"automatic next round qualifies original signed counterexample with complete proof");require(f.db.getRevision()==revision&&before.equals(ContextProductionInventory.inspect(child,f.db.getContextId(),revision,true,Collections.emptyList(),1000).fingerprint),"automatic owning discovery is read-only");raw(child,"!changed(1);");reject(()->lease.discoverDependentRound(input()),"whole child currentness remains mandatory");
            }finally{f.root.release(child);}
        }
    }
    public static void main(String[] args){try{System.setProperty("user.home",Files.createTempDirectory("dependent-discovery-").toString());oracle();correlationsAndBounds();alternativesAndDependencies();owning();System.out.println("CONTEXT_DEPENDENT_DISCOVERY_PASS checks="+checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
