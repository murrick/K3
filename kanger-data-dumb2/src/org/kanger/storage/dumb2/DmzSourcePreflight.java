/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import org.kanger.Mind;
import org.kanger.DmzSourceShape;
import org.kanger.interfaces.DmzSourcePlan;
import org.kanger.interfaces.IContextResults;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Exact physical source inspection. No connection, trust preparation, replay or joint qualification. */
final class DmzSourcePreflight {
    static DmzSourcePlan inspect(Mind q,String qLocator,DmzSourcePlan.Request request,
            java.util.function.Function<String,Path> resolve) throws Exception {
        DmzSourceShape.Observation before=DmzSourceShape.inspect(q,request.maximumRows);
        DmzSourcePlan.Source authority=source(qLocator,request.expectedQ,DmzSourcePlan.Role.AUTHORITY_Q,before);
        List<DmzSourcePlan.Source> external=new ArrayList<DmzSourcePlan.Source>();
        int remaining=request.maximumRows-before.inspectedRows;
        for(DmzSourcePlan.Selection selected:request.external) {
            RevisionRef pin=new RevisionRef(selected.revision.getContextId(),selected.revision.getRevision());
            Path location=resolve.apply(selected.locator);
            // Refuse missing/foreign pins before creating a native snapshot User.
            try(ContextSnapshot identity=ContextSnapshot.open(location,pin.getRevision())) {
                if(!identity.getContextId().equals(pin.getContextId()) || identity.getRevision()!=pin.getRevision())
                    throw new IllegalStateException("DMZ exact source identity mismatch");
            }
            try(SnapshotMindRuntime snapshot=SnapshotMindRuntime.open(location,pin,"dmz-source-"+pin.getContextId())) {
                DmzSourceShape.Observation observation=DmzSourceShape.inspect(snapshot.getMind(),remaining);
                external.add(source(selected.locator,selected.revision,DmzSourcePlan.Role.EXTERNAL,observation));
                remaining-=observation.inspectedRows;
            }
        }
        DmzSourceShape.Observation after=DmzSourceShape.inspect(q,request.maximumRows);
        if(before.inspectedRows!=after.inspectedRows || !before.primaryDigest.equals(after.primaryDigest))
            throw new IllegalStateException("DMZ Q declarations changed during source preflight");
        return new DmzSourcePlan(authority,external,request.maximumRows,q.getTransactionLevel());
    }
    private static DmzSourcePlan.Source source(String locator,IContextResults.Revision pin,DmzSourcePlan.Role role,DmzSourceShape.Observation observation) {
        return new DmzSourcePlan.Source(locator,pin,role,observation.primaryRules,observation.inspectedRows,observation.primaryDigest);
    }
}
