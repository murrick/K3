package org.kanger.storage.dumb2;
import java.util.*;import java.nio.file.*;import org.kanger.*;
import org.kanger.storage.dumb2.ContextNativeCandidateLeaseMatrix.Fixture;
public final class ContextMultiOriginPublicationRunner {
 static int checks;static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static ContextSignedModelRequest req(String s){return new ContextSignedModelRequest(s,Collections.emptyMap());}
 static Set<String> primary(Fixture f)throws Exception{return ContextNativeCommitGuardMatrix.primary(f);}
 public static void main(String[] args){try{Path d=Files.createTempDirectory("multi-origin-publication-");System.setProperty("user.home",d.toString());String original="!$u $v rel(u,v)&&~rel(v,u);";
  try(Fixture f=new Fixture(d,"Ordered")){String[] out=ContextAutomaticModelMatrix.console(f.mind,"ctx candidate model write \""+original+"\"\n");require(out[0].contains("MODEL WRITE: PUBLISHED")&&out[1].trim().isEmpty(),"actual Console full signed multi-Origin original published through owning attempt");}
  try(Fixture f=new Fixture(d,"Ordered")){require(primary(f).equals(Collections.singleton(original)),"durable single whole original retains swapped binder sharing");require(f.mind.query("?rel(0,1);",null,false)==null&&f.mind.query("?rel(1,0);",null,false)==null,"model coordinates never become named tuple facts after reopen");}
  String wide="!$a $b $c $d wide(a,7,b,a,c,\"7\",d,b);";
  try(Fixture f=new Fixture(d,"Four")){require(f.db.publishSignedModelCandidate(f.mind,req(wide)).status==ContextSignedModelPublication.Status.PUBLISHED,"four Origins/repeated/typed fixed coordinates in one atom publish");}
  try(Fixture f=new Fixture(d,"Four")){require(primary(f).equals(Collections.singleton(wide)),"full typed mixed tuple persists unchanged");}
  try(Fixture f=new Fixture(d,"Limited")){long revision=f.db.getRevision();ContextSignedModelPublication no=f.db.publishSignedModelCandidate(f.mind,new ContextSignedModelRequest(original,Collections.singletonMap("witnesses",1)));require(no.status==ContextSignedModelPublication.Status.INCOMPLETE,"insufficient two-binder choice budget does not prove conflict");require(primary(f).isEmpty()&&f.db.getRevision()==revision,"incomplete attempt atomically preserves revision and originals");}
  ContextNativeCandidateLeaseMatrix.create(d,"Negative","!@x @y ~rel(x,y);");
  try(Fixture f=new Fixture(d,"Refuted")){f.db.connectContext("Negative");long revision=f.db.getRevision();ContextSignedModelPublication no=f.db.publishSignedModelCandidate(f.mind,new ContextSignedModelRequest("!$u $v rel(u,v);",Collections.singletonMap("cells",0)));require(no.status==ContextSignedModelPublication.Status.REFUTED&&no.evidence!=null,"fresh two-Origin receiver proof precedes unavailable model table budget");no.evidence.check(64000000,64);require(primary(f).isEmpty()&&f.db.getRevision()==revision,"exact foreign conflict leaves whole original unpublished");}
  System.out.println("CONTEXT_MULTI_ORIGIN_PUBLICATION_PASS checks="+checks);System.exit(0);
 }catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
