/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;
import org.kanger.ContextWitnessResultTransport.Cut;
/** QA ONLY package bridge: never installed as a provider API or write gate. */
public final class ContextSignedModelAdmissionProbe {
 private static final int WORK=64000000;
 public static final class Historical {
  private final ContextSignedUnaryBranchModelAudit.Receipt receipt;
  private Historical(ContextSignedUnaryBranchModelAudit.Receipt receipt){this.receipt=receipt;}
  public void check(){ContextSignedUnaryBranchModel.Ledger l=receipt.ledger;ContextSignedUnaryModel.Ledger o=l.original.ledger;ContextSignedUnaryModelAudit.check(l.original,o.x,o.foreign,o.models,o.policy,WORK);ContextSignedUnaryBranchModelAudit.check(receipt,l.original,l.branch,l.extensions,l.policy,WORK);}
  public String proposedFingerprint(){return receipt.ledger.original.ledger.x.inventory.fingerprint;}
  public Map<UUID,String> foreignFingerprints(){Map<UUID,String> out=new LinkedHashMap<>();for(Snapshot cut:receipt.ledger.original.ledger.foreign)for(Snapshot s:cut.physicalMembers())out.put(s.inventory.context,s.inventory.fingerprint);return Collections.unmodifiableMap(out);}
 }
 public static Historical construct(Snapshot x,List<Snapshot> foreign)throws Exception{ContextSignedWitnessNativeBranch.Result branch=ContextSignedWitnessNativeBranch.run(x,foreign,ContextSignedWitnessNativeMatrix.policy());if(branch.status!=ContextSignedWitnessNativeBranch.Status.QUIESCENT_SUPPLIED)throw new AssertionError(branch.phase+": "+branch.reason);Map<Cut,List<SemanticTermSnapshot>> bases=new LinkedHashMap<>();bases.put(Cut.of(x),Collections.singletonList(ContextSignedUnaryModelMatrix.one()));for(Snapshot s:foreign)bases.put(Cut.of(s),Collections.singletonList(ContextSignedUnaryModelMatrix.one()));ContextSignedUnaryModelSearch.Result r=ContextSignedUnaryModelSearch.run(branch.output,bases,ContextSignedUnaryModelSearchMatrix.policy());if(r.status!=ContextSignedUnaryModelSearch.Status.CONSTRUCTED_FINAL_UNARY)throw new AssertionError(r.reason);Historical h=new Historical(r.output);h.check();return h;}
 private static int checks;
 private static void require(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  Snapshot x=ContextSignedUnaryModelMatrix.bind(true,"!anchor(1); !@u seed(u)->~blocked(u);"),a=ContextSignedUnaryModelMatrix.bind(false,"!$u seed(u)&&~blocked(u);");Historical h=construct(x,Collections.singletonList(a));h.check();require(true,"constructed ORIGINAL plus ACTUAL FINAL model chain freshly verified");ContextProductionFamilyAssembly.Result old=ContextProductionFamilyAssembly.assemble(x,Collections.singletonList(a),WORK);require(!old.assembled&&old.artifact==null&&old.reason.equals("Positive private witness bundle required"),"SAME modelled signed private family still refused by existing selector");
  x=ContextSignedUnaryModelMatrix.bind(true,"!$u seed(u)&&~blocked(u);");a=ContextSignedUnaryModelMatrix.bind(false,"!p(1);");construct(x,Collections.singletonList(a)).check();require(true,"modelled common signed pool");old=ContextProductionFamilyAssembly.assemble(x,Collections.singletonList(a),WORK);require(!old.assembled&&old.reason.equals("Positive common witness pool required"),"separate unchanged COMMON sign barrier still active");
  x=ContextSignedUnaryModelMatrix.bind(true,"!anchor(1); !@u absent(u)->~anchor(u);");a=ContextSignedUnaryModelMatrix.bind(false,"!$u seed(u);");construct(x,Collections.singletonList(a)).check();require(true,"original AND final headless constraint actually model checked");old=ContextProductionFamilyAssembly.assemble(x,Collections.singletonList(a),WORK);require(old.assembled,old.reason);ContextProductionFamilyAssembly.Evaluation evaluated=old.artifact.evaluate(8,16,WORK,500000);require(evaluated.status==ContextProductionFamilyAssembly.Status.INCOMPLETE&&evaluated.reason.equals("No supported self-X producer snapshot; full-cut coverage remains incomplete"),"model does not silently rewrite existing full-family eligibility contract");require(evaluated.qualification.uncovered.equals(Collections.singleton(x.primary.get(1).source)),"exact uncovered original headless Source remains explicit");
  System.out.println("SIGNED_MODEL_ADMISSION_ENVELOPE actualConstructedOriginalAndFinal=true distinctOldBarriers=true noAdmissionMigration=true");System.out.println("CONTEXT_SIGNED_MODEL_ADMISSION_ENVELOPE_PASS checks="+checks);
 }
}
