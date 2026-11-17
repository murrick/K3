/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Version;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.storage.Step;
import java.nio.file.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class DisconnectedHistoryTest {
    @TempDir Path root;
    private void publish(ContextStore store,long id)throws Exception {
        Step s=new Step();s.setId(id);s.setHash((int)id+17);s.setData(id);
        if(id>0)s.setNext(store.getBase("index").get(id-1));
        store.getBase("index").add(s);store.flush();
    }
    private ConnectionVector connection(ContextStore source,ContextStore target) {
        RevisionRef s=new RevisionRef(source.getContextId(),source.getRevision());
        RevisionRef t=new RevisionRef(target.getContextId(),target.getRevision());
        return new ConnectionVector(Arrays.asList(new ContextConnection(root.resolve("Target"),t,
                new CompatibilityCertificate(s,t,Version.CORE_VERSION_S))));
    }
    @Test void firstConnectionPreservesOlderDisconnectedRevisionAndDoesNotRewriteMetadata()throws Exception {
        Path location=root.resolve("Source");
        try(ContextStore source=ContextStore.create(location);ContextStore target=ContextStore.create(root.resolve("Target"))) {
            publish(source,0);publish(source,1);publish(target,0);
            try(ContextSnapshot old=ContextSnapshot.open(location,1)){assertEquals(1,old.getRevision());}
            source.publishTopology(connection(source,target),"first connection");
            byte[] sidecar=Files.readAllBytes(ConnectionStore.path(location));
            assertTrue(ConnectionStore.read(location,new RevisionRef(source.getContextId(),1)).isEmpty());
            try(ContextSnapshot old=ContextSnapshot.open(location,1)){
                assertEquals(Long.valueOf(0),old.getBase("index").get(0).getData());
                assertNull(old.getBase("index").get(1));
            }
            assertArrayEquals(sidecar,Files.readAllBytes(ConnectionStore.path(location)));
            assertEquals(3,source.getRevision());
            assertThrows(StorageLifecycleException.class,()->ConnectionStore.read(location,new RevisionRef(source.getContextId(),0)));
        }
    }
    @Test void unpublishedFirstSidecarAndSuccessorPruningKeepDisconnectedHistory()throws Exception {
        Path location=root.resolve("Source");
        try(ContextStore source=ContextStore.create(location)) {
            publish(source,0);publish(source,1);
            assertFalse(Files.exists(ConnectionStore.path(location)));
            ConnectionStore.writeTransition(location,new RevisionRef(source.getContextId(),2),ConnectionVector.empty(),
                    new RevisionRef(source.getContextId(),3),ConnectionVector.empty());
            byte[] staged=Files.readAllBytes(ConnectionStore.path(location));
            try(ContextSnapshot old=ContextSnapshot.open(location,1)){assertEquals(1,old.getRevision());}
            assertTrue(ConnectionStore.read(location,new RevisionRef(source.getContextId(),1)).isEmpty());
            assertThrows(StorageLifecycleException.class,()->ContextSnapshot.open(location,3));
            assertArrayEquals(staged,Files.readAllBytes(ConnectionStore.path(location)));
            // This fixture owns the writer lock and has no next-generation artifacts.
            assertTrue(ConnectionStore.pruneUnpublishedSuccessor(location,new RevisionRef(source.getContextId(),2)));
            try(ContextSnapshot old=ContextSnapshot.open(location,1)){assertEquals(1,old.getRevision());}
            assertEquals(2,source.getRevision());
        }
    }
    @Test void missingCurrentAndFutureBindingsAreNotInferredFromEmptyHistory()throws Exception {
        Path location=root.resolve("Source");
        try(ContextStore source=ContextStore.create(location)) {
            publish(source,0);publish(source,1);
            ConnectionStore.writeTransition(location,new RevisionRef(source.getContextId(),0),ConnectionVector.empty(),
                    new RevisionRef(source.getContextId(),1),ConnectionVector.empty());
            assertThrows(StorageLifecycleException.class,()->ConnectionStore.read(location,new RevisionRef(source.getContextId(),2)));
            Path future=ContextStore.generationPath(location,3);Files.createDirectory(future);
            Files.copy(ContextStore.generationPath(location,1).resolve("index.base"),future.resolve("index.base"));
            RevisionManifestStore.seal(future,source.getContextId(),3,2);
            assertThrows(StorageLifecycleException.class,()->ConnectionStore.read(location,new RevisionRef(source.getContextId(),3)),
                    "Even a valid empty seal cannot authorize an unpublished revision");
        }
    }
    @Test void missingNonemptyHistoricalBindingIsRejected()throws Exception {
        Path location=root.resolve("Source");
        try(ContextStore source=ContextStore.create(location);ContextStore target=ContextStore.create(root.resolve("Target"))) {
            publish(source,0);publish(target,0);
            source.publishTopology(connection(source,target),"connected history");
            publish(source,1);
            // Retain only the current nonempty binding, simulating a lost historical entry.
            RevisionRef current=new RevisionRef(source.getContextId(),source.getRevision());
            ConnectionStore.write(location,current,ConnectionStore.read(location,current));
            assertThrows(StorageLifecycleException.class,()->ConnectionStore.read(location,new RevisionRef(source.getContextId(),2)));
            assertThrows(StorageLifecycleException.class,()->ContextSnapshot.open(location,2));
        }
    }
    @Test void missingOrCorruptHistoricalManifestDoesNotAuthorizeEmptyBinding()throws Exception {
        Path location=root.resolve("Source");
        try(ContextStore source=ContextStore.create(location);ContextStore target=ContextStore.create(root.resolve("Target"))) {
            publish(source,0);publish(source,1);publish(target,0);
            source.publishTopology(connection(source,target),"first connection");
            Path manifest=RevisionManifestStore.path(ContextStore.generationPath(location,1));
            byte[] good=Files.readAllBytes(manifest);Files.delete(manifest);
            assertThrows(Exception.class,()->ConnectionStore.read(location,new RevisionRef(source.getContextId(),1)));
            Files.write(manifest,new byte[]{1,2,3});
            assertThrows(Exception.class,()->ConnectionStore.read(location,new RevisionRef(source.getContextId(),1)));
            Path generation=manifest.getParent();
            RevisionManifestStore.seal(generation,java.util.UUID.randomUUID(),1,0);
            assertThrows(StorageLifecycleException.class,()->ConnectionStore.read(location,new RevisionRef(source.getContextId(),1)));
            Files.write(manifest,good);
            // A valid codec-v1 seal proves data integrity, but has no dependency digest.
            java.io.ByteArrayInputStream bytes=new java.io.ByteArrayInputStream(good);
            java.io.DataInputStream input=new java.io.DataInputStream(bytes);
            input.skipBytes(4*3+8*4);
            input.skipBytes(input.readInt()); // semantic fingerprint
            input.readInt(); // base count
            input.skipBytes(input.readInt()); // generation digest
            int oldPayload=good.length-bytes.available();
            byte[] legacy=Arrays.copyOf(good,oldPayload+4);
            java.nio.ByteBuffer.wrap(legacy).putInt(4,1);
            java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(legacy,0,oldPayload);
            java.nio.ByteBuffer.wrap(legacy).putInt(oldPayload,(int)crc.getValue());
            Files.write(manifest,legacy);
            assertFalse(RevisionManifestStore.validate(generation,source.getContextId(),1,0).hasDependencyDigest());
            assertThrows(StorageLifecycleException.class,()->ConnectionStore.read(location,new RevisionRef(source.getContextId(),1)));
            Files.write(manifest,good);
            Path base=generation.resolve("index.base");byte[] original=Files.readAllBytes(base);
            byte[] damaged=original.clone();damaged[damaged.length-1]^=1;Files.write(base,damaged);
            assertThrows(StorageLifecycleException.class,()->ConnectionStore.read(location,new RevisionRef(source.getContextId(),1)));
            Files.write(base,original);
            assertTrue(ConnectionStore.read(location,new RevisionRef(source.getContextId(),1)).isEmpty());
            byte[] sidecar=Files.readAllBytes(ConnectionStore.path(location));
            Files.delete(ConnectionStore.path(location));
            java.util.UUID foreign=java.util.UUID.randomUUID();
            ConnectionStore.writeTransition(location,new RevisionRef(foreign,2),ConnectionVector.empty(),
                    new RevisionRef(foreign,3),ConnectionVector.empty());
            assertThrows(StorageLifecycleException.class,()->ConnectionStore.read(location,new RevisionRef(source.getContextId(),1)));
            Files.write(ConnectionStore.path(location),sidecar);
        }
    }
}
