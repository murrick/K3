package org.kanger.storage.dumb2;
import java.util.*;import java.nio.file.*;import org.kanger.*;
import org.kanger.storage.dumb2.ContextNativeCandidateLeaseMatrix.Fixture;
/** Exercises actual owning automatic attempts, native settlement and durable originals. */
public final class ContextMultiBinderModelPublicationRunner {
 static int checks;static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static ContextSignedModelRequest req(String s){return new ContextSignedModelRequest(s,Collections.emptyMap());}
 static Set<String> primary(Fixture f)throws Exception{return ContextNativeCommitGuardMatrix.primary(f);}
 public static void main(String[] args){try{Path d=Files.createTempDirectory("multi-binder-publication-");System.setProperty("user.home",d.toString());
  String bundle="!$u $v p(u)&&~p(v);";
  try(Fixture f=new Fixture(d,"Opposite")){String[] out=ContextAutomaticModelMatrix.console(f.mind,"ctx candidate model write \""+bundle+"\"\n");require(out[0].contains("MODEL WRITE: PUBLISHED")&&out[1].trim().isEmpty(),"actual Console publishes distinct Source/binder model through fresh owning attempt");}
  try(Fixture f=new Fixture(d,"Opposite")){require(primary(f).equals(Collections.singleton(bundle)),"cold reopen retains whole correlated multi-binder original");require(f.mind.query("?p(0);",null,false)==null&&f.mind.query("?p(1);",null,false)==null,"chosen positive/negative denotations do not become named facts after reopen");}
  String mixed="!$u $v rel(u,1)&&~rel(1,v);";
  try(Fixture f=new Fixture(d,"Mixed")){require(f.db.publishSignedModelCandidate(f.mind,req(mixed)).status==ContextSignedModelPublication.Status.PUBLISHED,"relational fixed arguments plus distinct existential binders publish");}
  try(Fixture f=new Fixture(d,"Mixed")){require(primary(f).equals(Collections.singleton(mixed)),"mixed tuple original durable without extra ground tuples");}
  String four="!$u $v $w $z p(u)&&q(v)&&r(w)&&s(z);";
  try(Fixture f=new Fixture(d,"Four")){require(f.db.publishSignedModelCandidate(f.mind,req(four)).status==ContextSignedModelPublication.Status.PUBLISHED,"four captured binder choices publish");}
  try(Fixture f=new Fixture(d,"Four")){require(primary(f).equals(Collections.singleton(four)),"all four binder occurrences retained after reopen");}
  try(Fixture f=new Fixture(d,"Limited")){long revision=f.db.getRevision();ContextSignedModelPublication no=f.db.publishSignedModelCandidate(f.mind,new ContextSignedModelRequest(bundle,Collections.singletonMap("witnesses",1)));require(no.status==ContextSignedModelPublication.Status.INCOMPLETE,"two-binder budget exhaustion remains incomplete");require(primary(f).isEmpty()&&revision==f.db.getRevision(),"budget refusal leaves CURRENT and originals unchanged");}
  try(Fixture f=new Fixture(d,"MultiOrigin")){long revision=f.db.getRevision();ContextSignedModelPublication no=f.db.publishSignedModelCandidate(f.mind,req("!$u $v rel(u,v);"));require(no.status==ContextSignedModelPublication.Status.PUBLISHED,"multiple Origins inside one native literal publish");require(primary(f).equals(Collections.singleton("!$u $v rel(u,v);"))&&revision<f.db.getRevision(),"whole multi-Origin original published once");}
  ContextNativeCandidateLeaseMatrix.create(d,"NoP","!@x ~p(x);");try(Fixture f=new Fixture(d,"Refuted")){f.db.connectContext("NoP");long revision=f.db.getRevision();ContextSignedModelPublication no=f.db.publishSignedModelCandidate(f.mind,new ContextSignedModelRequest(bundle,Collections.singletonMap("witnesses",0)));require(no.status==ContextSignedModelPublication.Status.REFUTED&&no.evidence!=null,"sourced native refutation precedes zero model witness budget");no.evidence.check(64000000,64);require(primary(f).isEmpty()&&revision==f.db.getRevision(),"negative publication refusal atomic");}
  System.out.println("CONTEXT_MULTI_BINDER_MODEL_PUBLICATION_PASS checks="+checks);System.exit(0);
 }catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
