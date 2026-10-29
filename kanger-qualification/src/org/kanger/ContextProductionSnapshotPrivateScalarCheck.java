/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Source;
import org.kanger.ContextWitnessResultTransport.*;

/** QA only: the already-supported whole ground private seed must stay local at full closure. */
public final class ContextProductionSnapshotPrivateScalarCheck {
    public static boolean keptLocal(ContextProductionFamilyAssembly.Evaluation evaluation,Source scalar){
        if(evaluation==null||evaluation.status!=ContextProductionFamilyAssembly.Status.FIXED_POINT||evaluation.qualification==null)return false;
        ContextWitnessFamilyQualification.Result q=evaluation.qualification;ContextWitnessFamilyFixedPoint.Result fixed=q.fixedPoint;
        if(!q.uncovered.isEmpty()||!q.collisions.isEmpty()||fixed==null||fixed.status!=ContextWitnessFamilyFixedPoint.Status.FIXED_POINT||fixed.packet.items.stream().anyMatch(i->i.atom.predicate.equals("private")))return false;
        PrivatePool pool=fixed.privatePools.get(Cut.of(scalar));
        return pool!=null&&pool.common==fixed.packet&&pool.sources.contains(scalar)&&pool.local.items.stream().anyMatch(i->i.atom.predicate.equals("private")&&i.atom.positive&&i.atom.arguments.size()==1&&i.atom.arguments.get(0).origin==null&&i.proofs.stream().anyMatch(p->p.depth==0&&p.sources.equals(Collections.singleton(scalar))));
    }
}
