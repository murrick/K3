/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.enums.QueryPass;

/** Every actual exclusion subset has a checked complement; no missing premise is invented. */
public final class ContextWideResidualSubsetRunner {
    static int checks;static void require(boolean v,String why){++checks;if(!v)throw new AssertionError(why);}
    static Source source(int id){return new Source(new UUID(0,0),1,id,true,"base");}
    static String atom(String name,boolean nullary){return name+(nullary?"":"(1)");}
    static Snapshot observe(int n,boolean negative,boolean nullary,int facts,int events)throws Exception{
        User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);m.setQueryPass(QueryPass.ACCEPT);
        try(ContextProductionCapture capture=ContextProductionCapture.begin(m,events)){
            capture.observeResidualSubsets();StringBuilder rule=new StringBuilder("!");for(int i=0;i<n;i++){if(i>0)rule.append("&&");rule.append(atom("p"+i,nullary));}rule.append("->").append(negative?"~":"").append(atom("out",nullary)).append(';');
            capture.replay(m,source(0),rule.toString());for(int i=0;i<facts;i++)capture.replay(m,source(i+1),"!"+atom("p"+i,nullary)+";");m.link(null,false);return capture.snapshot();
        }
    }
    static boolean exact(Residual r,int n,int mask,boolean negative,boolean nullary){
        if(r.premises.size()!=Integer.bitCount(mask)||r.conclusion.literals.size()!=n+1-Integer.bitCount(mask)||r.producerSupports.size()!=1||r.producerSupports.get(0).depth!=0||!r.producerSupports.get(0).sources.equals(Collections.singleton(source(0))))return false;
        Set<String> premises=new HashSet<>(),left=new HashSet<>();for(Fact f:r.premises){if(!f.positive||f.arguments.size()!=(nullary?0:1))return false;premises.add(f.predicate);}
        boolean head=false;for(Atom a:r.conclusion.literals){if(a.arguments.size()!=(nullary?0:1))return false;if(a.predicate.equals("out")){if(a.positive==negative)return false;head=true;}else{if(a.positive)return false;left.add(a.predicate);}}
        if(!head)return false;for(int i=0;i<n;i++){String p="p"+i;if(premises.contains(p)!=((mask&(1<<i))!=0)||left.contains(p)!=((mask&(1<<i))==0))return false;}return true;
    }
    public static void main(String[] args){try{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("wide-subset-").toString());
        for(int n:new int[]{3,7})for(boolean negative:new boolean[]{false,true})for(boolean nullary:new boolean[]{false,true}){
            Snapshot s=observe(n,negative,nullary,n,500000);require(s.gaps.isEmpty(),"wide actual observation has no missing scope: "+s.gaps);
            for(int mask=1;mask<(1<<n);mask++){final int wanted=mask;require(s.residuals.stream().anyMatch(r->exact(r,n,wanted,negative,nullary)),"exact exclusion subset/complement n="+n+" mask="+mask);}
            Clause owner=s.inputs.get(0).shapes.get(0);
            for(Residual r:s.residuals)require(ContextOrdinaryResidualVerifier.instance(owner,r,()->{}),"every observed subset authenticates against the whole owning clause");
            Residual joint=s.residuals.stream().filter(r->exact(r,n,(1<<n)-1,negative,nullary)).findFirst().get();
            require(!ContextOrdinaryResidualVerifier.instance(owner,new Residual(joint.conclusion,joint.premises.subList(1,joint.premises.size()),joint.producerSupports),()->{}),"missing premise cannot pass whole-owner authentication");
            List<Fact> wrong=new ArrayList<>(joint.premises);wrong.set(0,wrong.get(0).opposite());
            require(!ContextOrdinaryResidualVerifier.instance(owner,new Residual(joint.conclusion,wrong,joint.producerSupports),()->{}),"flipped premise sign cannot authenticate");
            require(!ContextOrdinaryResidualVerifier.instance(owner,new Residual(joint.conclusion,joint.premises,joint.producerSupports,2),()->{}),"one observed reduction cannot disguise multiple proof steps");
            Snapshot partial=observe(n,negative,nullary,1,500000);require(partial.residuals.stream().allMatch(r->r.premises.stream().allMatch(f->f.predicate.equals("p0"))),"unexcluded body literals cannot be invented as premise donors");
            require(observe(n,negative,nullary,0,500000).residuals.isEmpty(),"unresolved waiters yield no assumed premises");
        }
        require(observe(8,false,false,0,500000).gaps.stream().anyMatch(g->g.startsWith("residual-subset-scope:")),"nine-literal branch retains explicit scope gap");
        boolean stopped=false;try{observe(7,false,false,7,12);}catch(IllegalStateException expected){stopped=true;}require(stopped,"subset expansion consumes existing observation event budget");
        System.out.println("CONTEXT_WIDE_RESIDUAL_SUBSET_PASS checks="+checks);System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
