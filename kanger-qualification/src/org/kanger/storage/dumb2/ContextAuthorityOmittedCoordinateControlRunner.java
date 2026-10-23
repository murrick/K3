/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;
import java.nio.file.*;
import java.util.*;
import org.kanger.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextUniversalFamilyInspection.*;

/** Off-diagonal input cannot fire a repeated-argument Bridge; diagonal input can. */
public final class ContextAuthorityOmittedCoordinateControlRunner {
    static int checks; static final int WORK=64000000;
    static void require(boolean yes,String why){++checks;if(!yes)throw new AssertionError(why);}
    static void one(boolean diagonal,boolean negative,boolean reverse)throws Exception {
        Path dir=Files.createTempDirectory("omitted-coordinate-control-");System.setProperty("user.home",dir.toString());String sign=negative?"~":"";
        ContextAuthorityChain.create(dir,"Donor","!@x "+sign+"q(x,"+(diagonal?"9":"2")+",9);");
        ContextAuthorityChain.create(dir,"Bridge","!@x @y @z "+sign+"out(x,y,z)->"+sign+"mid(z,2,x);");
        ContextAuthorityChain.create(dir,"Deny","!"+(negative?"":"~")+"mid(9,2,1);");
        String text="!@x @y @z "+sign+"q(x,y,z)&&~p1(x)->"+sign+"out(x,y,z);";
        Set<String> origins;long settled;List<RevisionRef> pins;
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")) {
            require(f.root.compile("!~p1(1);",null,false),"original premise");List<String> names=new ArrayList<>(Arrays.asList("Donor","Bridge","Deny"));if(reverse)Collections.reverse(names);for(String name:names)f.db.connectContext(name);f.db.saveConnections(f.root);
            ContextProductionInventory before=ContextSevenPremiseNativeWriteRunner.inventory(f);long revision=f.db.getRevision();pins=ContextSevenPremiseNativeWriteRunner.pins(f);
            ContextUniversalCandidate.Evaluation e=f.db.qualifyUniversalProductionCandidate(f.root,text,1000,16,16,16,65536,1000000,WORK,500000,WORK);
            require(e.status==Status.COLLISION,"equality-sensitive verdict: "+e.reason);
            require(e.qualification.collision!=null&&e.qualification.certificate==null&&!e.qualification.collision.oppositions.isEmpty(),"either middle input value must reach a complete conflict");
            require(f.db.getRevision()==revision&&ContextSevenPremiseNativeWriteRunner.inventory(f).fingerprint.equals(before.fingerprint),"diagnostic is read-only");
            DB.UniversalProductionWriteResult write=f.db.writeUniversalProductionCandidate(f.root,text,1000,16,16,16,65536,1000000,WORK,500000,WORK,WORK,false);
            require(write.status==DB.ProductionWriteStatus.COLLISION&&write.audit!=null&&write.audit.verified&&write.trial!=e,"fresh public writer independently settles equality-sensitive result");
            ContextProductionInventory after=ContextSevenPremiseNativeWriteRunner.inventory(f);settled=f.db.getRevision();origins=ContextSevenPremiseNativeWriteRunner.origins(after);
            require(after.primary.size()==1&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"only whole candidate may become primary; pins unchanged");
            require(settled==revision&&after.fingerprint.equals(before.fingerprint),"refusal preserves physical state");
        }
        try(ContextSevenPremiseNativeWriteRunner.Fixture f=new ContextSevenPremiseNativeWriteRunner.Fixture(dir,"X")){ContextProductionInventory i=ContextSevenPremiseNativeWriteRunner.inventory(f);require(i.primary.size()==1&&ContextSevenPremiseNativeWriteRunner.origins(i).equals(origins)&&f.db.getRevision()>=settled&&ContextSevenPremiseNativeWriteRunner.pins(f).equals(pins),"ordinary reopen retains exact origins and topology");}
    }
    public static void main(String[] args){try{for(boolean diagonal:new boolean[]{false,true})for(boolean negative:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true})one(diagonal,negative,reverse);System.out.println("CONTEXT_AUTHORITY_OMITTED_COORDINATE_CONTROL_PASS checks="+(checks+ContextAuthorityChain.checks));System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
