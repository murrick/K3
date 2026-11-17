/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.User;
import org.kanger.Version;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.storage.Step;
import java.nio.file.*;
import java.nio.*;
import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalBindingRecoveryTest {
    @TempDir Path root;
    private Path source, target;
    private long historical;
    private void publish(ContextStore s,long id)throws Exception {
        Step row=new Step();row.setId(id);row.setHash(113+(int)id);row.setData(id);
        if(id>0)row.setNext(s.getBase("index").get(id-1));s.getBase("index").add(row);s.flush();
    }
    private void v1(long revision)throws Exception {
        Path p=RevisionManifestStore.path(ContextStore.generationPath(source,revision));byte[] good=Files.readAllBytes(p);
        ByteArrayInputStream bytes=new ByteArrayInputStream(good);DataInputStream in=new DataInputStream(bytes);
        in.skipBytes(4*3+8*4);in.skipBytes(in.readInt());in.readInt();in.skipBytes(in.readInt());
        int length=good.length-bytes.available();byte[] old=Arrays.copyOf(good,length+4);ByteBuffer.wrap(old).putInt(4,1);
        CRC32 crc=new CRC32();crc.update(old,0,length);ByteBuffer.wrap(old).putInt(length,(int)crc.getValue());Files.write(p,old);
    }
    private void unsealed(long revision)throws Exception {
        Path p=ContextStore.contextPath(source);byte[] good=Files.readAllBytes(p),old=new byte[good.length-8];
        assertEquals(0,ByteBuffer.wrap(good).order(ByteOrder.LITTLE_ENDIAN).getInt(24));
        System.arraycopy(good,0,old,0,28);System.arraycopy(good,36,old,28,good.length-40);
        ByteBuffer.wrap(old).order(ByteOrder.LITTLE_ENDIAN).putInt(4,2);CRC32 crc=new CRC32();crc.update(old,0,old.length-4);
        ByteBuffer.wrap(old).order(ByteOrder.LITTLE_ENDIAN).putInt(old.length-4,(int)crc.getValue());Files.write(p,old);
        Files.delete(RevisionManifestStore.path(ContextStore.generationPath(source,revision)));
    }
    private ConnectionVector vector(ContextStore s,ContextStore t) {
        RevisionRef sr=new RevisionRef(s.getContextId(),s.getRevision()),tr=new RevisionRef(t.getContextId(),t.getRevision());
        return new ConnectionVector(Arrays.asList(new ContextConnection(target,tr,new CompatibilityCertificate(sr,tr,Version.CORE_VERSION_S))));
    }
    private void fixture(String format,boolean nonempty)throws Exception {
        Path dir=Files.createTempDirectory(root,"case-");source=dir.resolve("Source");target=dir.resolve("Target");
        try(ContextStore anchor=ContextStore.create(dir.resolve("Anchor"));ContextStore s=ContextStore.create(source);ContextStore t=ContextStore.create(target)) {
            publish(s,0);publish(t,0);
            if(nonempty){s.publishTopology(vector(s,t),"connected history");historical=2;publish(s,1);}
            else{historical=1;publish(s,1);s.publishTopology(vector(s,t),"first connection");}
            assertEquals(3,s.getRevision());
        }
        if(format.equals("v1"))v1(historical);if(format.equals("legacy"))unsealed(historical);
    }
    @SuppressWarnings("unchecked") private void drop(long revision)throws Exception {
        UUID id=ContextManifestStore.read(ContextStore.contextPath(source)).getContextId();
        java.lang.reflect.Method read=ConnectionStore.class.getDeclaredMethod("readState",Path.class,UUID.class);read.setAccessible(true);Object state=read.invoke(null,source,id);
        java.lang.reflect.Field rows=state.getClass().getDeclaredField("byRevision");rows.setAccessible(true);assertNotNull(((Map<Long,ConnectionVector>)rows.get(state)).remove(revision));
        java.lang.reflect.Method write=ConnectionStore.class.getDeclaredMethod("writeState",Path.class,state.getClass());write.setAccessible(true);write.invoke(null,source,state);
    }
    private Map<String,byte[]> image()throws Exception {
        Map<String,byte[]> out=new TreeMap<>();
        for(Path p:Arrays.asList(ContextStore.contextPath(source),ContextStore.revisionPath(source),ConnectionStore.path(source)))if(Files.exists(p))out.put(p.getFileName().toString(),Files.readAllBytes(p));
        Path state=ContextStore.stateRoot(source);try(java.util.stream.Stream<Path> files=Files.walk(state)){
            for(Path p:(Iterable<Path>)files.filter(Files::isRegularFile)::iterator)out.put("state/"+state.relativize(p),Files.readAllBytes(p));
        }return out;
    }
    private void same(Map<String,byte[]> before)throws Exception {
        Map<String,byte[]> after=image();assertEquals(before.keySet(),after.keySet());for(String p:before.keySet())assertArrayEquals(before.get(p),after.get(p),p);
    }
    private void observe(long revision,boolean available,int connections,String code)throws Exception {
        Map<String,byte[]> before=image();User u=new User();u.setDatabaseDir(source.getParent()+File.separator);DB db=new DB();db.init(u);db.use("Anchor");UUID active=db.getContextId();
        try {
            assertEquals(available,db.exists("Source@"+revision));
            if(available){db.use("Source@"+revision);assertEquals(revision,db.getRevision());assertEquals(connections,db.federationSnapshot().getConnections().size());}
            else{StorageLifecycleException error=assertThrows(StorageLifecycleException.class,()->db.use("Source@"+revision));assertEquals(code,error.getCode());assertFalse(db.isClosed());assertEquals(active,db.getContextId());}
        }finally{db.close();}same(before);
    }
    @Test void missingHistoricalRowsUseEvidenceOrTypedRefusal()throws Exception {
        for(String format:Arrays.asList("sealed","v1","legacy"))for(boolean nonempty:new boolean[]{false,true}) {
            fixture(format,nonempty);drop(historical);boolean provedEmpty=format.equals("sealed")&&!nonempty;
            observe(historical,provedEmpty,0,"STORAGE_CONTEXT_CONFLICT");
        }
    }
    @Test void originalBackupRestoresExactRowsWithoutPublishing()throws Exception {
        for(String format:Arrays.asList("sealed","v1","legacy"))for(boolean nonempty:new boolean[]{false,true}) {
            fixture(format,nonempty);Map<String,byte[]> original=image();byte[] backup=Files.readAllBytes(ConnectionStore.path(source));drop(historical);
            // Offline fixture restoration of the exact original bytes, not a repair API.
            Files.write(ConnectionStore.path(source),backup);observe(historical,true,nonempty?1:0,null);same(original);
            assertEquals(3,RevisionStore.read(ContextStore.revisionPath(source)));
        }
    }
    @Test void missingCurrentAndR0AreNotInferred()throws Exception {
        for(long missing:new long[]{0,3}){fixture("sealed",false);drop(missing);observe(missing,false,0,"STORAGE_CONTEXT_CONFLICT");}
    }
    @Test void corruptMetadataCannotUseTheEmptyFallback()throws Exception {
        fixture("sealed",false);drop(historical);Path sidecar=ConnectionStore.path(source),manifest=RevisionManifestStore.path(ContextStore.generationPath(source,historical));
        byte[] good=Files.readAllBytes(sidecar),bad=good.clone();bad[bad.length-1]^=1;Files.write(sidecar,bad);observe(historical,false,0,"STORAGE_SEMANTIC_CORRUPTION");
        bad=good.clone();bad[8]^=1;CRC32 crc=new CRC32();crc.update(bad,0,bad.length-4);ByteBuffer.wrap(bad).putInt(bad.length-4,(int)crc.getValue());
        Files.write(sidecar,bad);observe(historical,false,0,"STORAGE_SEMANTIC_CORRUPTION");Files.write(sidecar,good);
        byte[] proof=Files.readAllBytes(manifest);bad=proof.clone();bad[bad.length-1]^=1;Files.write(manifest,bad);observe(historical,false,0,"STORAGE_SEMANTIC_CORRUPTION");
        Files.write(manifest,proof);observe(historical,true,0,null);
        // Whole-file absence is different from a missing row: exact positive seals
        // still distinguish empty from nonempty selected dependency identities.
        fixture("sealed",true);
        try(ContextStore s=ContextStore.open(source)){s.publishTopology(ConnectionVector.empty(),"disconnect");assertEquals(4,s.getRevision());}
        Files.delete(ConnectionStore.path(source));
        observe(4,true,0,null);observe(1,true,0,null);
        observe(historical,false,0,"STORAGE_SEMANTIC_CORRUPTION");
    }
    @Test void dependencyIdentityDigestDoesNotReconstructPrivateRecipe()throws Exception {
        fixture("sealed",true);UUID id=ContextManifestStore.read(ContextStore.contextPath(source)).getContextId();ConnectionVector a=ConnectionStore.read(source,new RevisionRef(id,historical));
        ContextConnection c=a.getConnections().get(0);ConnectionVector b=new ConnectionVector(Arrays.asList(new ContextConnection(c.getTargetLocation(),c.getTarget(),c.getCertificate(),Arrays.asList("!private(7);"),"team")));
        assertNotEquals(a,b);assertNotEquals(c.getInitialization(),b.getConnections().get(0).getInitialization());
        assertArrayEquals(RevisionManifestStore.dependencyDigest(a),RevisionManifestStore.dependencyDigest(b));
    }
    @Test void wholeLegacySidecarLossIsIndistinguishableFromAnEmptyContract()throws Exception {
        for(String format:Arrays.asList("v1","legacy")) {
            Path dir=Files.createTempDirectory(root,"absence-");source=dir.resolve("Source");target=dir.resolve("Target");
            ConnectionVector connected;RevisionRef ref;
            try(ContextStore anchor=ContextStore.create(dir.resolve("Anchor"));ContextStore s=ContextStore.create(source);ContextStore t=ContextStore.create(target)) {
                publish(s,0);publish(t,0);connected=vector(s,t);ref=new RevisionRef(s.getContextId(),1);
            }
            if(format.equals("v1"))v1(1);else unsealed(1);
            assertFalse(Files.exists(ConnectionStore.path(source)));Map<String,byte[]> empty=image();observe(1,true,0,null);
            ConnectionStore.write(source,ref,connected);observe(1,true,1,null);Files.delete(ConnectionStore.path(source));
            same(empty);observe(1,true,0,null);
        }
    }
}
