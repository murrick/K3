/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionNativeBinding.Snapshot;

/** Independent exact native ingress composition; never invokes the ingress builder/controller. */
final class ContextSignedWitnessNativeBranchAudit {
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static void check(ContextSignedWitnessNativeBranch.Receipt r,Snapshot x,List<Snapshot> foreign,ContextSignedWitnessNativeBranch.Policy p,int work,int capacity){
  require(r!=null&&p!=null&&x!=null&&foreign!=null&&r.x==x&&r.foreign.equals(foreign)&&r.policy==p&&r.status!=ContextSignedWitnessNativeBranch.Status.INCOMPLETE&&work>0&&capacity>=1&&capacity<=4096,"Exact bounded native ingress receipt/request required");new ContextSignedWitnessNativeBranch.Policy(p.rows,p.rank,p.rounds,p.work,p.capacity,p.kernel,p.local);
  require(r.plan!=null&&r.seeds!=null&&r.seeds.frame.plan==r.plan,"Actual original plan/seed lineage required");ContextSignedWitnessPlanAudit.check(r.plan,x,foreign,p.rows,work);ContextSignedWitnessFrameAudit.check(r.seeds,r.plan,x,foreign,p.rows,p.rank,work,work);
  if(r.initialRejection!=null){require(r.status==ContextSignedWitnessNativeBranch.Status.REFUTED&&r.start==null&&r.termination==null&&r.initialRejection.status==ContextSignedWitnessReceiver.Status.REFUTED,"Initial refutation cannot lend a stable/continuation prefix");ContextSignedWitnessReceiver.check(r.initialRejection,r.seeds,p.rows,p.kernel,work);return;}
  require(r.start!=null&&r.start.generation==1&&r.start.seeds==r.seeds&&r.start.rows==p.rows&&r.start.limits==p.kernel&&r.termination!=null,"Actual original root required, not an equal or later scope");
  ContextSignedWitnessUntilStop.check(r.termination,r.start,p.rows,p.kernel,p.local,p.rounds,work,capacity);
  require(r.status==ContextSignedWitnessNativeBranch.Status.QUIESCENT_SUPPLIED?r.termination.result.status==ContextSignedWitnessUntilStop.Status.QUIESCENT:r.status==ContextSignedWitnessNativeBranch.Status.REFUTED&&r.termination.result.status==ContextSignedWitnessUntilStop.Status.REFUTED,"Native verdict must equal actual owned termination");
 }
}
