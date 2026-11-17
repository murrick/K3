/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Version;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.storage.Step;
import static org.junit.jupiter.api.Assertions.*;

class DurablePinsTest {
    @TempDir Path root;
    private void append(ContextStore store,long id)throws Exception{
        Step step=new Step();step.setId(id);step.setHash((int)id+19);step.setData(id);
        if(id>0)step.setNext(store.getBase("index").get(id-1));
        store.getBase("index").add(step);store.flush();
    }
    private ConnectionVector pin(ContextStore source,Path location,long revision)throws Exception{
        try(ContextSnapshot target=ContextSnapshot.open(location,revision)){
            RevisionRef s=new RevisionRef(source.getContextId(),source.getRevision()),t=new RevisionRef(target.getContextId(),revision);
            return new ConnectionVector(Arrays.asList(new ContextConnection(location,t,new CompatibilityCertificate(s,t,Version.CORE_VERSION_S))));
        }
    }
    @Test void publishesAnExactHistoricalPinWithoutAdvancingOrRewritingTarget()throws Exception{
        Path target=root.resolve("producer/Target");
        try(ContextStore p=ContextStore.create(target);ContextStore c=ContextStore.create(root.resolve("consumer/Source"))){
            append(p,0);append(p,1);append(c,0);
            byte[] marker=Files.readAllBytes(ContextStore.revisionPath(target));
            byte[] metadata=Files.readAllBytes(ContextStore.contextPath(target));
            byte[] retained=Files.readAllBytes(ContextStore.generationPath(target,1).resolve("index.base"));
            c.publishTopology(pin(c,target,1),"historical pin");
            assertEquals(1,ConnectionStore.read(root.resolve("consumer/Source"),new RevisionRef(c.getContextId(),c.getRevision())).getConnections().get(0).getTarget().getRevision());
            assertArrayEquals(marker,Files.readAllBytes(ContextStore.revisionPath(target)));
            assertArrayEquals(metadata,Files.readAllBytes(ContextStore.contextPath(target)));
            assertArrayEquals(retained,Files.readAllBytes(ContextStore.generationPath(target,1).resolve("index.base")));
        }
    }
    @Test void publishesInitialZeroPinWithoutInventingAGeneration()throws Exception{
        Path target=root.resolve("producer/Target");
        try(ContextStore p=ContextStore.create(target);ContextStore c=ContextStore.create(root.resolve("consumer/Source"))){
            append(c,0);c.publishTopology(pin(c,target,0),"zero pin");
            assertEquals(0,p.getRevision());assertFalse(Files.exists(ContextStore.generationPath(target,0)));
            assertEquals(0,ConnectionStore.read(root.resolve("consumer/Source"),new RevisionRef(c.getContextId(),c.getRevision())).getConnections().get(0).getTarget().getRevision());
        }
    }
    @Test void directPublicationDoesNotAcquireTargetsAdvisoryDependencies()throws Exception{
        Path a=root.resolve("producer/A"),b=root.resolve("advisory/B"),c=root.resolve("consumer/Source");
        try(ContextStore target=ContextStore.create(b)){append(target,0);}
        try(ContextStore target=ContextStore.create(a)){append(target,0);target.publishTopology(pin(target,b,1),"A declares B");}
        Files.delete(ContextStore.contextPath(b));
        try(ContextStore source=ContextStore.create(c)){
            append(source,0);source.publishTopology(pin(source,a,2),"direct A only");
            ConnectionVector saved=ConnectionStore.read(c,new RevisionRef(source.getContextId(),source.getRevision()));
            assertEquals(1,saved.size());assertEquals(a,saved.getConnections().get(0).getTargetLocation());
            assertFalse(Files.exists(ContextStore.contextPath(b)),"no implicit repair or acquisition of B");
        }
    }
    @Test void stabilizationRejectsStaleIdentityWithoutPrintingIdentifiers()throws Exception{
        Path target=root.resolve("producer/Target");
        try(ContextStore p=ContextStore.create(target)){
            RevisionRef wrong=new RevisionRef(UUID.randomUUID(),0),source=new RevisionRef(UUID.randomUUID(),1);
            ConnectionVector vector=new ConnectionVector(Arrays.asList(new ContextConnection(target,wrong,new CompatibilityCertificate(source,wrong,Version.CORE_VERSION_S))));
            StorageLifecycleException failure=assertThrows(StorageLifecycleException.class,()->DurablePins.stabilize(vector));
            assertTrue(failure.toString().contains("Target@0"));assertFalse(failure.toString().contains(wrong.getContextId().toString()));
            assertEquals(0,RevisionStore.read(ContextStore.revisionPath(target)));
        }
    }
}
