package org.kanger.storage.dumb2;
import java.util.*;import java.nio.file.*;import org.kanger.*;
import org.kanger.storage.dumb2.ContextNativeCandidateLeaseMatrix.Fixture;
/** Actual owning attempts and Console reports: text changes must not change semantics or settlement. */
public final class ContextModelRefusalDiagnosticsRunner {
 static int checks;static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static String rule(int n){StringBuilder s=new StringBuilder("!");for(int i=0;i<n;i++)s.append("$v").append(i).append(' ');s.append("wide(");for(int i=0;i<n;i++){if(i>0)s.append(',');s.append('v').append(i);}return s.append(");").toString();}
 static Map<String,Integer> limits(Object... pairs){Map<String,Integer> b=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)b.put((String)pairs[i],(Integer)pairs[i+1]);return b;}
 static Set<String> primary(Fixture f)throws Exception{return ContextNativeCommitGuardMatrix.primary(f);}
 static ContextSignedModelPublication attempt(Fixture f,String statement,Map<String,Integer> limits,String expected)throws Exception{
  long before=f.db.getRevision();Set<String> rows=primary(f);ContextSignedModelPublication r=f.db.publishSignedModelCandidate(f.mind,new ContextSignedModelRequest(statement,limits));
  require(r.status==ContextSignedModelPublication.Status.INCOMPLETE&&r.evidence==null,"resource refusal stays incomplete, not conflict or proof");
  require(r.reason!=null&&!r.reason.trim().isEmpty()&&r.reason.toLowerCase(Locale.ROOT).contains(expected),"actual refusal explains "+expected+": "+r.reason);
  require(r.phase!=null&&!r.phase.trim().isEmpty()&&r.description().contains(r.reason)&&!r.description().endsWith("\nnull"),"report retains actual phase and displays reason");
  require(before==f.db.getRevision()&&rows.equals(primary(f))&&r.before.getRevision()==r.after.getRevision(),"reason formatting leaves CURRENT and whole primary rows unchanged");
  return r;
 }
 public static void main(String[] args){try{Path d=Files.createTempDirectory("model-refusal-diagnostics-");System.setProperty("user.home",d.toString());
  try(Fixture f=new Fixture(d,"Rank")){attempt(f,rule(9),limits("rank",8,"witnesses",9),"rank");require(f.db.publishSignedModelCandidate(f.mind,new ContextSignedModelRequest(rule(9),limits("rank",9,"witnesses",9))).status==ContextSignedModelPublication.Status.PUBLISHED,"fresh corrected rank attempt still publishes");}
  try(Fixture f=new Fixture(d,"Witness")){attempt(f,rule(9),limits("rank",9,"witnesses",8),"witness");}
  try(Fixture f=new Fixture(d,"Basis")){attempt(f,"!p(1);",limits("basis-work",1),"work");}
  ContextNativeCandidateLeaseMatrix.create(d,"Seed","!$u seed(u);");
  try(Fixture f=new Fixture(d,"Imported")){require(f.mind.compile("!@x seed(x)->out(x);",null,false),"actual local producer");f.db.connectContext("Seed");f.db.saveConnections(f.mind);attempt(f,"!anchor(1);",limits("origins",0),"origin");}
  ContextNativeCandidateLeaseMatrix.create(d,"NoP","!@x ~p(x);");
  try(Fixture f=new Fixture(d,"Refuted")){f.db.connectContext("NoP");long before=f.db.getRevision();ContextSignedModelPublication r=f.db.publishSignedModelCandidate(f.mind,new ContextSignedModelRequest("!$u p(u);",limits("witnesses",0,"origins",0,"cells",0)));require(r.status==ContextSignedModelPublication.Status.REFUTED&&r.evidence!=null,"sourced refutation stays distinct and precedes model budgets");require(r.reason!=null&&r.reason.contains("refutation")&&r.description().contains(r.reason),"actual conflict explanation survives exception-to-report conversion");r.evidence.check(64000000,64);require(before==f.db.getRevision()&&primary(f).isEmpty(),"negative evidence remains independently valid and refusal atomic");}
  try(Fixture f=new Fixture(d,"Local")){require(f.mind.compile("!p(1);",null,false),"native local baseline");long before=f.db.getRevision();Set<String> rows=primary(f);ContextSignedModelPublication r=f.db.publishSignedModelCandidate(f.mind,new ContextSignedModelRequest("!~p(1);",Collections.emptyMap()));require(r.status==ContextSignedModelPublication.Status.NATIVE_REJECTED&&r.reason!=null&&!r.reason.trim().isEmpty()&&r.evidence==null,"native settlement refusal remains native, with its original text");require(before==f.db.getRevision()&&rows.equals(primary(f)),"local native refusal unchanged");}
  try(Fixture f=new Fixture(d,"Console")){String[] out=ContextAutomaticModelMatrix.console(f.mind,"ctx candidate model write \""+rule(9)+"\" rank=8 witnesses=9\n");require(out[0].contains("MODEL WRITE: INCOMPLETE")&&out[0].toLowerCase(Locale.ROOT).contains("rank")&&!out[0].contains("\nnull")&&out[1].trim().isEmpty(),"actual Console displays native rank reason instead of null");}
  System.out.println("CONTEXT_MODEL_REFUSAL_DIAGNOSTICS_PASS checks="+checks);System.exit(0);
 }catch(Throwable t){t.printStackTrace();System.exit(1);}}
}
