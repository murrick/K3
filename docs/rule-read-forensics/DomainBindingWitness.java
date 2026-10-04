package org.kanger;
import java.nio.file.Files;
import org.kanger.interfaces.ITerm;
import org.kanger.primitives.Argument;
import org.kanger.units.*;

/** Exact built-ins: same Domain context does not imply same variable context. */
public final class DomainBindingWitness {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private static String run(boolean naive,int mode)throws Exception {
        Mind root=new Mind(new User()),child=new Mind(root);
        ITerm a=root.getTerms().add("a"),b=root.getTerms().add("b");
        TVariable variable=root.getTVars().createTVar(new Rule(root),a);
        root.getTValues().set(variable,new TValue(variable,a));
        child.getTValues().set(variable,new TValue(variable,b));
        Domain domain=new Domain(root);domain.add(new Argument(variable));domain.setMind(root);
        Rule rule=new Rule(root);rule.getTree().get(0).add(domain);rule.setMind(root);
        if(mode==1){Domain sibling=new Domain(child);sibling.add(new Argument(variable));sibling.setMind(child);}
        else variable.setMind(child);
        check(domain.getMind()==root && variable.getMind()==child,"same-domain stale-variable precondition");
        if(mode==2){if(!naive || rule.getMind()!=root)rule.setMind(root);}
        else if(!naive || domain.getMind()!=root)domain.setMind(root);
        check(variable.getValue()==(naive?b:a),"binding visibility after reselection");
        check(domain.getMind()==root,"domain context stays root");
        return naive?"child-b":"root-a";
    }
    public static void main(String[] ignored)throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("domain-binding-witness-").toString());
        for(int mode=0;mode<3;mode++){
            String reference=run(false,mode),naive=run(true,mode);
            check(!reference.equals(naive),"guard divergence");
            System.out.println("WITNESS mode="+mode+" reference="+reference+" naive="+naive);
        }
        System.out.println("DOMAIN_BINDING_WITNESS_OK scenarios=3 checks="+checks);
    }
}
