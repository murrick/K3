/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Version;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.storage.Step;
import java.nio.file.*;
import java.nio.*;
import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
import static org.junit.jupiter.api.Assertions.*;

class FirstConnectionHistoryTest {
    @TempDir Path root;
    private void publish(ContextStore store,long id)throws Exception {
        Step s=new Step();s.setId(id);s.setHash((int)id+71);s.setData(id);
        if(id>0)s.setNext(store.getBase("index").get(id-1));
        store.getBase("index").add(s);store.flush();
    }
    private ConnectionVector desired(ContextStore source,ContextStore target) {
        RevisionRef s=new RevisionRef(source.getContextId(),source.getRevision()),t=new RevisionRef(target.getContextId(),target.getRevision());
        return new ConnectionVector(Arrays.asList(new ContextConnection(root.resolve("Target"),t,new CompatibilityCertificate(s,t,Version.CORE_VERSION_S))));
    }
    private void prepare()throws Exception {
        try(ContextStore s=ContextStore.create(root.resolve("Source"));ContextStore t=ContextStore.create(root.resolve("Target"))){publish(s,0);publish(s,1);publish(t,0);}
    }
    private void legacy()throws Exception {
        Path path=ContextStore.contextPath(root.resolve("Source"));byte[] current=Files.readAllBytes(path);
        assertEquals(0,ByteBuffer.wrap(current).order(ByteOrder.LITTLE_ENDIAN).getInt(24),"fixture has no origin");
        // Codec v2 omits the eight-byte manifest baseline after the origin flag.
        byte[] old=new byte[current.length-8];System.arraycopy(current,0,old,0,28);System.arraycopy(current,36,old,28,current.length-36-4);
        ByteBuffer.wrap(old).order(ByteOrder.LITTLE_ENDIAN).putInt(4,2);CRC32 crc=new CRC32();crc.update(old,0,old.length-4);
        ByteBuffer.wrap(old).order(ByteOrder.LITTLE_ENDIAN).putInt(old.length-4,(int)crc.getValue());Files.write(path,old);
        for(int r=1;r<=2;r++)Files.delete(RevisionManifestStore.path(ContextStore.generationPath(root.resolve("Source"),r)));
    }
    private void sealV1(long revision)throws Exception {
        Path path=RevisionManifestStore.path(ContextStore.generationPath(root.resolve("Source"),revision));byte[] good=Files.readAllBytes(path);
        ByteArrayInputStream bytes=new ByteArrayInputStream(good);DataInputStream in=new DataInputStream(bytes);
        in.skipBytes(4*3+8*4);in.skipBytes(in.readInt());in.readInt();in.skipBytes(in.readInt());
        int length=good.length-bytes.available();byte[] old=Arrays.copyOf(good,length+4);ByteBuffer.wrap(old).putInt(4,1);
        CRC32 crc=new CRC32();crc.update(old,0,length);ByteBuffer.wrap(old).putInt(length,(int)crc.getValue());Files.write(path,old);
    }
    private void retainsHistory(String kind)throws Exception {
        prepare();if(kind.equals("unsealed"))legacy();if(kind.equals("v1")){sealV1(1);sealV1(2);}
        Path location=root.resolve("Source");
        try(ContextStore source=ContextStore.open(location);ContextStore target=ContextStore.open(root.resolve("Target"))) {
            assertFalse(Files.exists(ConnectionStore.path(location)));
            long old=kind.equals("r0")?0:1;RevisionRef ref=new RevisionRef(source.getContextId(),old);
            try(ContextSnapshot snapshot=ContextSnapshot.open(location,old)){assertEquals(old,snapshot.getRevision());}
            assertTrue(ConnectionStore.read(location,ref).isEmpty());
            source.publishTopology(desired(source,target),"first connection preserves "+kind);
            assertTrue(ConnectionStore.read(location,ref).isEmpty(),"first sidecar must retain the old observable topology");
            try(ContextSnapshot snapshot=ContextSnapshot.open(location,old)){assertEquals(old,snapshot.getRevision());}
            byte[] sidecar=Files.readAllBytes(ConnectionStore.path(location));
            assertTrue(ConnectionStore.read(location,ref).isEmpty());assertArrayEquals(sidecar,Files.readAllBytes(ConnectionStore.path(location)));
            source.publishTopology(ConnectionVector.empty(),"disconnect");
            assertTrue(ConnectionStore.read(location,ref).isEmpty());assertEquals(4,source.getRevision());
        }
    }
    @Test void preservesInitialEmptyRevision(){assertDoesNotThrow(()->retainsHistory("r0"));}
    @Test void preservesUnsealedLegacyHistory(){assertDoesNotThrow(()->retainsHistory("unsealed"));}
    @Test void preservesCodecV1History(){assertDoesNotThrow(()->retainsHistory("v1"));}
    @Test void existingIncompleteLegacySidecarIsNotReconstructed()throws Exception {
        prepare();legacy();Path location=root.resolve("Source");
        try(ContextStore source=ContextStore.open(location);ContextStore target=ContextStore.open(root.resolve("Target"))) {
            source.publishTopology(desired(source,target),"initial");
            RevisionRef current=new RevisionRef(source.getContextId(),3);
            ConnectionVector saved=ConnectionStore.read(location,current);
            Files.delete(ConnectionStore.path(location));
            ConnectionStore.writeTransition(location,new RevisionRef(source.getContextId(),2),ConnectionVector.empty(),current,saved);
            assertThrows(Exception.class,()->ConnectionStore.read(location,new RevisionRef(source.getContextId(),1)));
            assertThrows(StorageLifecycleException.class,()->ConnectionStore.read(location,new RevisionRef(source.getContextId(),0)));
            source.publishTopology(ConnectionVector.empty(),"disconnect without guessing missing history");
            assertThrows(Exception.class,()->ConnectionStore.read(location,new RevisionRef(source.getContextId(),1)));
            assertThrows(StorageLifecycleException.class,()->ConnectionStore.read(location,new RevisionRef(source.getContextId(),0)));
            assertEquals(4,source.getRevision());
        }
    }
    @Test void invalidRetainedHistoryBlocksFirstSidecarAndCanRetryAfterRestoration()throws Exception {
        prepare();Path location=root.resolve("Source"),base=ContextStore.generationPath(location,1).resolve("index.base");
        byte[] good=Files.readAllBytes(base),bad=good.clone();bad[bad.length-1]^=1;Files.write(base,bad);
        try(ContextStore source=ContextStore.open(location);ContextStore target=ContextStore.open(root.resolve("Target"))) {
            assertThrows(StorageLifecycleException.class,()->source.publishTopology(desired(source,target),"must refuse"));
            assertEquals(2,source.getRevision());assertEquals(2,RevisionStore.read(ContextStore.revisionPath(location)));
            assertFalse(Files.exists(ConnectionStore.path(location)));assertFalse(Files.exists(ContextStore.generationPath(location,3)));
            Files.write(base,good);source.publishTopology(desired(source,target),"retry");
            assertEquals(3,source.getRevision());assertTrue(ConnectionStore.read(location,new RevisionRef(source.getContextId(),0)).isEmpty());
        }
    }

}
