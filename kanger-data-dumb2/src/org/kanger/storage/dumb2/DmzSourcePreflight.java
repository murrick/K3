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
    static final class Inputs {
        final DmzSourcePlan plan;
        final DmzSourceShape.Observation authority;
        final List<DmzSourceShape.Observation> external;
        final List<DmzSourceShape.Observation> components;
        Inputs(DmzSourcePlan plan,DmzSourceShape.Observation authority,List<DmzSourceShape.Observation> components,List<DmzSourceShape.Observation> external) {
            this.plan=plan; this.authority=authority;this.components=java.util.Collections.unmodifiableList(new ArrayList<DmzSourceShape.Observation>(components));
            this.external=java.util.Collections.unmodifiableList(new ArrayList<DmzSourceShape.Observation>(external));
        }
    }
    static DmzSourcePlan inspect(Mind q,String qLocator,DmzSourcePlan.Request request,
            java.util.function.Function<String,Path> resolve) throws Exception {
        return acquire(q,qLocator,request,resolve).plan;
    }
    /** Internal operation-time inputs; never accept a caller's historical metadata as permission. */
    static Inputs acquire(Mind q,String qLocator,DmzSourcePlan.Request request,
            java.util.function.Function<String,Path> resolve) throws Exception {
        return acquire(q,qLocator,request,resolve,java.util.Collections.<ContextConnection>emptyList(),false);
    }
    static Inputs acquire(Mind q,String qLocator,DmzSourcePlan.Request request,
            java.util.function.Function<String,Path> resolve,List<ContextConnection> connections,boolean connectedViews) throws Exception {
        DmzSourceShape.Observation before=DmzSourceShape.inspect(q,request.maximumRows);
        DmzSourcePlan.Source authority=source(qLocator,request.expectedQ,DmzSourcePlan.Role.AUTHORITY_Q,before);
        List<DmzSourcePlan.Source> external=new ArrayList<DmzSourcePlan.Source>();
        List<DmzSourceShape.Observation> inputs=new ArrayList<DmzSourceShape.Observation>();
        int remaining=request.maximumRows-before.inspectedRows;
        List<DmzSourcePlan.Source> components=new ArrayList<DmzSourcePlan.Source>();
        List<DmzSourceShape.Observation> componentInputs=new ArrayList<DmzSourceShape.Observation>();
        List<ContextConnection> ordered=new ArrayList<ContextConnection>(connections);
        ordered.sort(java.util.Comparator.comparing(c -> c.getTarget().getContextId().toString()));
        for(ContextConnection connection:ordered)if(connection.getTrustGroup()!=null) {
            DmzSourceShape.Observation observation=DmzSourceShape.inspect(connection.layer(),remaining);
            IContextResults.Revision pin=new IContextResults.Revision(connection.getTarget().getContextId(),connection.getTarget().getRevision());
            components.add(source(connection.getTargetLocation().getFileName().toString(),pin,DmzSourcePlan.Role.AUTHORITY_COMPONENT,
                    observation,!connection.getInitialization().isEmpty()));
            componentInputs.add(observation);remaining-=observation.inspectedRows;
        }
        for(DmzSourcePlan.Selection selected:request.external) {
            RevisionRef pin=new RevisionRef(selected.revision.getContextId(),selected.revision.getRevision());
            Path location=resolve.apply(selected.locator);
            // Refuse missing/foreign pins before creating a native snapshot User.
            try(ContextSnapshot identity=ContextSnapshot.open(location,pin.getRevision())) {
                if(!identity.getContextId().equals(pin.getContextId()) || identity.getRevision()!=pin.getRevision())
                    throw new IllegalStateException("DMZ exact source identity mismatch");
            }
            ContextConnection view=null;
            if(connectedViews)for(ContextConnection connection:connections)
                if(connection.getTargetLocation().equals(location.toAbsolutePath().normalize())
                        && connection.getTarget().equals(pin))view=connection;
            if(view!=null) {
                DmzSourceShape.Observation observation=DmzSourceShape.inspect(view.layer(),remaining);
                inputs.add(observation);
                external.add(source(selected.locator,selected.revision,DmzSourcePlan.Role.EXTERNAL,observation,!view.getInitialization().isEmpty()));
                remaining-=observation.inspectedRows;continue;
            }
            try(SnapshotMindRuntime snapshot=SnapshotMindRuntime.open(location,pin,"dmz-source-"+pin.getContextId())) {
                DmzSourceShape.Observation observation=DmzSourceShape.inspect(snapshot.getMind(),remaining);
                inputs.add(observation);
                external.add(source(selected.locator,selected.revision,DmzSourcePlan.Role.EXTERNAL,observation));
                remaining-=observation.inspectedRows;
            }
        }
        DmzSourceShape.Observation after=DmzSourceShape.inspect(q,request.maximumRows);
        if(before.inspectedRows!=after.inspectedRows || !before.primaryDigest.equals(after.primaryDigest))
            throw new IllegalStateException("DMZ Q declarations changed during source preflight");
        return new Inputs(new DmzSourcePlan(authority,components,external,request.maximumRows,q.getTransactionLevel()),before,componentInputs,inputs);
    }
    private static DmzSourcePlan.Source source(String locator,IContextResults.Revision pin,DmzSourcePlan.Role role,DmzSourceShape.Observation observation) {
        return source(locator,pin,role,observation,false);
    }
    private static DmzSourcePlan.Source source(String locator,IContextResults.Revision pin,DmzSourcePlan.Role role,DmzSourceShape.Observation observation,boolean configured) {
        return new DmzSourcePlan.Source(locator,pin,role,observation.primaryRules,observation.inspectedRows,observation.primaryDigest,configured);
    }
}
