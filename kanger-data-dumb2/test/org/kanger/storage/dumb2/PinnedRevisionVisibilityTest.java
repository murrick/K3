/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.User;
import org.kanger.Version;
import org.kanger.storage.Step;
import java.nio.file.*;
import java.nio.ByteBuffer;
import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
import static org.junit.jupiter.api.Assertions.*;

class PinnedRevisionVisibilityTest {
    @TempDir Path root;
    private DB inspector()throws Exception {
        User u=new User();u.setDatabaseDir(root+File.separator);DB db=new DB();db.init(u);return db;
    }
    private void publish(ContextStore s,long id)throws Exception {
        Step row=new Step();row.setId(id);row.setHash(91+(int)id);row.setData(id);
        if(id>0)row.setNext(s.getBase("index").get(id-1));s.getBase("index").add(row);s.flush();
    }
    private void prepare()throws Exception {
        try(ContextStore empty=ContextStore.create(root.resolve("Root"));ContextSnapshot s=ContextSnapshot.open(root.resolve("Root"));
            ContextStore fork=s.fork(root.resolve("EmptyFork"))){assertEquals(0,fork.getRevision());}
        try(ContextStore bare=ContextStore.create(root.resolve("Bare"))){
            publish(bare,0);try(ContextSnapshot s=ContextSnapshot.open(root.resolve("Bare"));ContextStore f=s.fork(root.resolve("BareFork"))){assertEquals(1,f.getRevision());}
        }
        try(ContextStore s=ContextStore.create(root.resolve("Source"));ContextStore t=ContextStore.create(root.resolve("Target"))){
            publish(s,0);publish(t,0);RevisionRef sr=new RevisionRef(s.getContextId(),1),tr=new RevisionRef(t.getContextId(),1);
            s.publishTopology(new ConnectionVector(Arrays.asList(new ContextConnection(root.resolve("Target"),tr,
                    new CompatibilityCertificate(sr,tr,Version.CORE_VERSION_S)))),"pin");
            try(ContextSnapshot snap=ContextSnapshot.open(root.resolve("Source"));ContextStore f=snap.fork(root.resolve("PinnedFork"))){assertEquals(1,f.getRevision());}
        }
    }
    private void sealV1(long revision)throws Exception {
        Path p=RevisionManifestStore.path(ContextStore.generationPath(root.resolve("Source"),revision));byte[] good=Files.readAllBytes(p);
        ByteArrayInputStream bytes=new ByteArrayInputStream(good);DataInputStream in=new DataInputStream(bytes);
        in.skipBytes(4*3+8*4);in.skipBytes(in.readInt());in.readInt();in.skipBytes(in.readInt());
        int length=good.length-bytes.available();byte[] old=Arrays.copyOf(good,length+4);ByteBuffer.wrap(old).putInt(4,1);
        CRC32 crc=new CRC32();crc.update(old,0,length);ByteBuffer.wrap(old).putInt(length,(int)crc.getValue());Files.write(p,old);
    }
    @Test void missingForkInitialBindingIsNotAdvertisedAsAvailable()throws Exception {
        prepare();DB db=inspector();
        assertFalse(db.exists("PinnedFork@0"),"header-only R0 must not advertise an unreadable connection vector");
        org.kanger.exception.StorageLifecycleException failure=assertThrows(org.kanger.exception.StorageLifecycleException.class,()->db.use("PinnedFork@0"));
        assertEquals("STORAGE_CONTEXT_CONFLICT",failure.getCode());
        assertTrue(failure.toString().contains("PinnedFork@0"));
        assertFalse(failure.toString().contains(ContextManifestStore.read(ContextStore.contextPath(root.resolve("PinnedFork"))).getContextId().toString()));
        assertTrue(db.isClosed());
    }
    @Test void rejectedHistoricalPreflightKeepsCurrentStorageOpen()throws Exception {
        prepare();DB db=inspector();db.use("BareFork");UUID id=db.getContextId();long revision=db.getRevision();
        try {
            assertThrows(Exception.class,()->db.use("PinnedFork@0"));
            assertFalse(db.isClosed(),"unavailable pin must be rejected before closing the active storage");
            assertEquals(id,db.getContextId());assertEquals(revision,db.getRevision());
        }finally{db.close();}
    }
    @Test void legacySealWithoutHistoricalVectorIsUnavailableBeforeSwitch()throws Exception {
        prepare();Path p=root.resolve("Source");sealV1(1);
        RevisionRef current;
        try(ContextSnapshot s=ContextSnapshot.open(p)){current=new RevisionRef(s.getContextId(),s.getRevision());}
        ConnectionStore.write(p,current,ConnectionStore.read(p,current));
        DB db=inspector();db.use("BareFork");UUID active=db.getContextId();
        try {
            assertFalse(db.exists("Source@1"),"legacy generation alone does not establish an exact pinned Context");
            assertThrows(Exception.class,()->db.use("Source@1"));
            assertFalse(db.isClosed());assertEquals(active,db.getContextId());
            // History is a retained-generation catalog, not an availability certificate.
            assertEquals(2,db.versionHistory("Source").getRevisions().size());
        }finally{db.close();}
    }
    @Test void rootAndForkMatrixDistinguishesCatalogAvailabilityAndFuture()throws Exception {
        prepare();DB db=inspector();
        for(String name:Arrays.asList("Root","EmptyFork","BareFork","PinnedFork")) {
            long current;try(ContextSnapshot s=ContextSnapshot.open(root.resolve(name))){current=s.getRevision();}
            List<Long> catalog=new ArrayList<>();for(org.kanger.interfaces.internal.IContextFederation.RevisionVersion r:db.versionHistory(name).getRevisions())catalog.add(r.getRevision());
            assertEquals(current==0?Arrays.asList(0L):Arrays.asList(1L),catalog,name);
            assertTrue(db.exists(name+"@"+current));assertFalse(db.exists(name+"@"+(current+1)));
            db.use(name+"@"+current);assertEquals(current,db.getRevision());
            assertEquals(name.equals("PinnedFork")?1:0,db.federationSnapshot().getConnections().size());db.close();
            boolean zeroExpected=!name.equals("PinnedFork");assertEquals(zeroExpected,db.exists(name+"@0"),name);
            if(zeroExpected){db.use(name+"@0");assertEquals(0,db.getRevision());assertTrue(ConnectionStore.read(root.resolve(name),new RevisionRef(db.getContextId(),0)).isEmpty());db.close();}
            try(ContextStore s=ContextStore.open(root.resolve(name))){publish(s,current==0?0:1);}
            List<Long> after=new ArrayList<>();
            for(org.kanger.interfaces.internal.IContextFederation.RevisionVersion r:db.versionHistory(name).getRevisions())after.add(r.getRevision());
            assertEquals(current==0?Arrays.asList(1L):Arrays.asList(2L,1L),after,name);
            assertTrue(db.exists(name+"@"+(current+1)));
            db.use(name+"@"+(current+1));assertEquals(current+1,db.getRevision());
            assertEquals(name.equals("PinnedFork")?1:0,db.federationSnapshot().getConnections().size());db.close();
            assertEquals(zeroExpected,db.exists(name+"@0"),"R0 after publication: "+name);
            if(zeroExpected){db.use(name+"@0");assertEquals(0,db.getRevision());db.close();}
            assertTrue(db.exists(name+"@"+current),"old selected revision remains readable: "+name);
            db.use(name+"@"+current);assertEquals(current,db.getRevision());
            assertEquals(name.equals("PinnedFork")?1:0,db.federationSnapshot().getConnections().size());db.close();
            assertFalse(db.exists(name+"@"+(current+2)));
        }
    }
}
