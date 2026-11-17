/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InterruptedPublicationReclamationTest {
    @TempDir Path directory;
    private User user;private DB db;private Mind mind;
    private void open()throws Exception{
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(directory)){
            org.junit.jupiter.api.Assumptions.assumeTrue(entries instanceof SecureDirectoryStream,
                    "Reclamation requires secure relative directory operations");
        }
        user=new User();user.setDatabaseDir(directory.toString()+java.io.File.separator);db=new DB();db.init(user);Mind root=new Mind(user);user.setCurrentMind(root);mind=(Mind)root.useStorage("Source");user.setCurrentMind(mind);assertTrue(mind.compile("!marker(9);",null,false));user.checkpoint(mind);}
    private void close()throws Exception{user.setCurrentMind(mind.closeStorage());}
    private Path temp(String prefix)throws Exception{Path p=directory.resolve(prefix+UUID.randomUUID());Files.write(p,new byte[]{1,2,3});return p;}
    @Test void checkpointReclaimsRecognizableInterruptedFilesWithoutPublishing()throws Exception{
        open();try{long before=db.getRevision();Path revision=temp("Source.revision.next-"),connections=temp("Source.connections.tmp-");
            user.checkpoint(mind);
            assertFalse(Files.exists(revision),"checkpoint must reclaim interrupted marker temporary file");
            assertFalse(Files.exists(connections),"checkpoint must reclaim interrupted connection temporary file");
            assertEquals(before,db.getRevision());assertEquals(Boolean.TRUE,mind.query("?marker(9);"));
        }finally{close();}
    }

    private Path staging(String kind,long revision,String... files)throws Exception{
        Path p=ContextStore.stateRoot(directory.resolve("Source")).resolve("."+kind+"-"+revision+"-"+UUID.randomUUID());Files.createDirectory(p);
        for(String name:files)Files.write(p.resolve(name),new byte[]{3,4,5});return p;
    }
    private Map<String,byte[]> image(Path path)throws Exception{
        Map<String,byte[]> out=new TreeMap<>();try(java.util.stream.Stream<Path> files=Files.walk(path)){for(Path p:(Iterable<Path>)files.filter(Files::isRegularFile)::iterator)out.put(path.relativize(p).toString(),Files.readAllBytes(p));}return out;
    }
    private void unchanged(Path root,Map<String,byte[]> before)throws Exception{
        Map<String,byte[]> after=image(root);assertEquals(before.keySet(),after.keySet());for(String name:before.keySet())assertArrayEquals(before.get(name),after.get(name),name);
    }
    @Test void retainsPublishedHistoryNumericOrphansAndUnknownEntries()throws Exception{
        open();try{
            assertTrue(mind.compile("!second(1);",null,false));long current=db.getRevision();assertTrue(current>1);
            Path first=ContextStore.generationPath(directory.resolve("Source"),1),latest=ContextStore.generationPath(directory.resolve("Source"),current);
            Map<String,byte[]> old=image(first),now=image(latest);
            Path a=staging("staging",current,"rules.base","revision.manifest"),b=staging("topology",current+1,"rules.base"),c=staging("reindex",current+1);
            Path unknown=staging("staging",current+1,"rules.base","notes.txt"),future=staging("staging",current+2,"rules.base");
            Path numeric=ContextStore.generationPath(directory.resolve("Source"),current+1);Files.createDirectory(numeric);Files.write(numeric.resolve("evidence"),new byte[]{7});
            Path foreign=temp("Rules.revision.next-"),unrecognized=temp("Source.context.tmp-");
            Path malformed=directory.resolve("Source.revision.next-not-a-uuid");Files.write(malformed,new byte[]{8});
            user.checkpoint(mind);
            for(Path reclaimed:Arrays.asList(a,b,c))assertFalse(Files.exists(reclaimed));
            for(Path retained:Arrays.asList(unknown,unknown.resolve("rules.base"),future,numeric,numeric.resolve("evidence"),foreign,unrecognized,malformed))assertTrue(Files.exists(retained),retained.toString());
            assertEquals(current,db.getRevision());unchanged(first,old);unchanged(latest,now);
            try(ContextSnapshot pinned=ContextSnapshot.open(directory.resolve("Source"),1)){assertEquals(1,pinned.getRevision());}
            user.checkpoint(mind);assertEquals(current,db.getRevision());unchanged(first,old);unchanged(latest,now);
        }finally{close();}
    }
    @Test void neverFollowsLinksOrTraversesUnknownSubdirectories()throws Exception{
        open();try{
            long current=db.getRevision();Path generation=ContextStore.generationPath(directory.resolve("Source"),current);Map<String,byte[]> before=image(generation);
            Path outside=directory.resolve("keep.txt");Files.write(outside,new byte[]{9});
            Path marker=directory.resolve("Source.revision.next-"+UUID.randomUUID());Files.createSymbolicLink(marker,outside);
            Path linked=ContextStore.stateRoot(directory.resolve("Source")).resolve(".staging-"+(current+1)+"-"+UUID.randomUUID());Files.createSymbolicLink(linked,generation);
            Path child=staging("topology",current+1,"revision.manifest");Files.createSymbolicLink(child.resolve("rules.base"),outside);
            Path nested=staging("reindex",current+1,"rules.base");Files.createDirectory(nested.resolve("nested"));Files.write(nested.resolve("nested/keep"),new byte[]{5});
            user.checkpoint(mind);
            assertTrue(Files.isSymbolicLink(marker));assertTrue(Files.isSymbolicLink(linked));assertTrue(Files.isSymbolicLink(child.resolve("rules.base")));
            assertTrue(Files.exists(child.resolve("revision.manifest")));assertTrue(Files.exists(nested.resolve("rules.base")));assertTrue(Files.exists(nested.resolve("nested/keep")));
            assertArrayEquals(new byte[]{9},Files.readAllBytes(outside));unchanged(generation,before);
        }finally{close();}
    }
    @Test void corruptionOrChangedMarkerBlocksReclamationBeforeChildReservation()throws Exception{
        open();Path location=directory.resolve("Source"),marker=ContextStore.revisionPath(location),manifest=RevisionManifestStore.path(ContextStore.generationPath(location,db.getRevision()));byte[] validMarker=Files.readAllBytes(marker),validManifest=Files.readAllBytes(manifest);
        try{
            Path abandoned=temp("Source.revision.next-");long current=db.getRevision();
            byte[] bad=validMarker.clone();bad[8]^=1;Files.write(marker,bad);
            assertThrows(org.kanger.exception.StorageLifecycleException.class,()->user.checkpoint(mind));assertTrue(Files.exists(abandoned));DmzCandidateSession.requireSettledPhysicalQ(mind);
            Files.write(marker,validMarker);bad=validManifest.clone();bad[bad.length-1]^=1;Files.write(manifest,bad);
            assertThrows(org.kanger.exception.StorageLifecycleException.class,()->user.checkpoint(mind));assertTrue(Files.exists(abandoned));DmzCandidateSession.requireSettledPhysicalQ(mind);
            Files.write(manifest,validManifest);RevisionStore.advance(marker,current);
            assertThrows(org.kanger.exception.StorageLifecycleException.class,()->user.checkpoint(mind));assertTrue(Files.exists(abandoned));assertEquals(current,db.getRevision());DmzCandidateSession.requireSettledPhysicalQ(mind);
            Files.write(marker,validMarker);user.checkpoint(mind);assertFalse(Files.exists(abandoned));assertEquals(current,db.getRevision());
        }finally{Files.write(marker,validMarker);Files.write(manifest,validManifest);close();}
    }
    @Test void retainsLinkedStateRootAndRequiresLiveExclusiveOwner()throws Exception{
        open();Path state=ContextStore.stateRoot(directory.resolve("Source")),saved=directory.resolve("saved-state");
        try{
            Path artifact=staging("staging",db.getRevision()+1,"rules.base");
            assertThrows(org.kanger.exception.StorageLifecycleException.class,()->ContextStore.open(directory.resolve("Source")));assertTrue(Files.exists(artifact));
            Files.move(state,saved);Files.createSymbolicLink(state,saved);user.checkpoint(mind);
            assertTrue(Files.isSymbolicLink(state));assertTrue(Files.exists(saved.resolve(artifact.getFileName())));
            Files.delete(state);Files.move(saved,state);user.checkpoint(mind);assertFalse(Files.exists(artifact));
        }finally{if(Files.isSymbolicLink(state)){Files.delete(state);Files.move(saved,state);}close();}
        ContextStore closed=ContextStore.create(directory.resolve("Closed"));closed.close();assertThrows(IllegalStateException.class,closed::reclaimInterruptedPublicationFiles);
    }
    @Test void unsupportedSecureDirectoryOperationsRetainFiles()throws Exception{
        java.net.URI uri=java.net.URI.create("jar:"+directory.resolve("retention.zip").toUri());
        try(FileSystem fs=FileSystems.newFileSystem(uri,Collections.singletonMap("create","true"))){
            Path location=fs.getPath("/Source"),artifact=fs.getPath("/Source.revision.next-"+UUID.randomUUID());Files.write(artifact,new byte[]{1});
            assertEquals(0,InterruptedPublicationReclaimer.reclaim(location,UUID.randomUUID(),0));assertArrayEquals(new byte[]{1},Files.readAllBytes(artifact));
        }
    }
    @Test void historicalReadOnlyCheckpointDoesNotReclaimMutableContextFiles()throws Exception{
        open();long revision=db.getRevision();close();Path artifact=temp("Source.revision.next-");
        mind=(Mind)((Mind)user.getCurrentMind()).useStorage("Source@"+revision);user.setCurrentMind(mind);
        try{assertTrue(db.isReadOnly());user.checkpoint(mind);assertTrue(Files.exists(artifact));assertEquals(revision,db.getRevision());}
        finally{close();}
        mind=(Mind)((Mind)user.getCurrentMind()).useStorage("Source");user.setCurrentMind(mind);
        try{user.checkpoint(mind);assertFalse(Files.exists(artifact));assertEquals(revision,db.getRevision());}finally{close();}
    }
    @Test void changedContextMetadataCannotAuthorizeReclamation()throws Exception{
        open();Path metadata=ContextStore.contextPath(directory.resolve("Source"));byte[] valid=Files.readAllBytes(metadata);
        try{
            Path artifact=temp("Source.revision.next-");byte[] bad=valid.clone();bad[bad.length-1]^=1;Files.write(metadata,bad);
            assertThrows(org.kanger.exception.StorageLifecycleException.class,()->user.checkpoint(mind));assertTrue(Files.exists(artifact));DmzCandidateSession.requireSettledPhysicalQ(mind);
            Files.write(metadata,valid);user.checkpoint(mind);assertFalse(Files.exists(artifact));
        }finally{Files.write(metadata,valid);close();}
        Path location=directory.resolve("Empty"),context=ContextStore.contextPath(location);
        ContextStore empty=ContextStore.create(location);valid=Files.readAllBytes(context);
        try{
            Path artifact=temp("Empty.revision.next-");Files.delete(context);ContextManifestStore.create(context);
            assertThrows(org.kanger.exception.StorageLifecycleException.class,empty::reclaimInterruptedPublicationFiles);assertTrue(Files.exists(artifact));
            Files.write(context,valid);assertEquals(1,empty.reclaimInterruptedPublicationFiles());assertFalse(Files.exists(artifact));
        }finally{Files.write(context,valid);empty.close();}
    }
    private void copyGeneration(Path source,Path target)throws Exception{
        Files.createDirectory(target);try(DirectoryStream<Path> entries=Files.newDirectoryStream(source)){for(Path p:entries)Files.copy(p,target.resolve(p.getFileName()));}
    }
    @Test void checkpointReclaimsOnlySealedOwnedImmediateSuccessor()throws Exception{
        open();try{
            assertTrue(mind.compile("!second(1);",null,false));long current=db.getRevision();Path location=directory.resolve("Source");
            Path retained=ContextStore.generationPath(location,1),published=ContextStore.generationPath(location,current),orphan=ContextStore.generationPath(location,current+1);
            Map<String,byte[]> old=image(retained),now=image(published);copyGeneration(published,orphan);
            RevisionManifestStore.seal(orphan,db.getContextId(),current+1,current);
            try(ContextSnapshot pinned=ContextSnapshot.open(location,1)){
                user.checkpoint(mind);assertFalse(Files.exists(orphan),"sealed owned unpublished next generation must be reclaimed");
                assertEquals(current,db.getRevision());assertEquals(1,pinned.getRevision());unchanged(retained,old);unchanged(published,now);
            }
            user.checkpoint(mind);assertEquals(current,db.getRevision());
        }finally{close();}
    }
    @Test void ambiguousOrForeignNumericGenerationsAreRetainedWhole()throws Exception{
        open();try{
            assertTrue(mind.compile("!second(1);",null,false));long current=db.getRevision();Path location=directory.resolve("Source"),published=ContextStore.generationPath(location,current);Map<String,byte[]> stable=image(published);
            for(String kind:Arrays.asList("foreign","wrong-revision","wrong-parent","manifest-crc","base-digest","missing-manifest","unknown","nested","manifest-link","base-link","format","noncanonical-base","far","leading-zero","directory-link")){
                String name=kind.equals("far")?"revision-"+(current+2):kind.equals("leading-zero")?"revision-0"+(current+1):"revision-"+(current+1);
                Path orphan=ContextStore.stateRoot(location).resolve(name),extra=directory.resolve("manifest-copy-"+kind);
                if(kind.equals("directory-link"))Files.createSymbolicLink(orphan,published);
                else{
                    copyGeneration(published,orphan);
                    RevisionManifestStore.seal(orphan,kind.equals("foreign")?UUID.randomUUID():db.getContextId(),kind.equals("wrong-revision")?current+2:current+1,kind.equals("wrong-parent")?current-1:current);
                    Path manifest=RevisionManifestStore.path(orphan),base;
                    try(java.util.stream.Stream<Path> files=Files.list(orphan)){base=files.filter(p->p.getFileName().toString().endsWith(".base")).findFirst().get();}
                    if(kind.equals("manifest-crc")){byte[] bytes=Files.readAllBytes(manifest);bytes[bytes.length-1]^=1;Files.write(manifest,bytes);}
                    if(kind.equals("base-digest")){byte[] bytes=Files.readAllBytes(base);bytes[0]^=1;Files.write(base,bytes);}
                    if(kind.equals("missing-manifest"))Files.delete(manifest);
                    if(kind.equals("unknown"))Files.write(orphan.resolve("notes.txt"),new byte[]{1});
                    if(kind.equals("nested"))Files.createDirectory(orphan.resolve("nested"));
                    if(kind.equals("manifest-link")){Files.move(manifest,extra);Files.createSymbolicLink(manifest,extra);}
                    if(kind.equals("base-link")){Files.delete(base);Files.createSymbolicLink(base,published.resolve(base.getFileName()));}
                    if(kind.equals("format")){byte[] bytes=Files.readAllBytes(manifest);java.nio.ByteBuffer.wrap(bytes).putInt(8,RevisionManifestStore.STORAGE_FORMAT_VERSION+1);java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(bytes,0,bytes.length-4);java.nio.ByteBuffer.wrap(bytes).putInt(bytes.length-4,(int)crc.getValue());Files.write(manifest,bytes);}
                    if(kind.equals("noncanonical-base")){Files.write(orphan.resolve("unknown?.base"),new byte[]{1});RevisionManifestStore.seal(orphan,db.getContextId(),current+1,current);}
                }
                Map<String,byte[]> before=image(orphan);user.checkpoint(mind);
                assertTrue(Files.exists(orphan,LinkOption.NOFOLLOW_LINKS),kind);unchanged(orphan,before);assertEquals(current,db.getRevision());unchanged(published,stable);
                ContextStore.deleteRecursively(orphan);Files.deleteIfExists(extra);
            }
        }finally{close();}
    }
    @Test void sealedFirstGenerationCanBeReclaimedWithoutInventingAnInitialRevision()throws Exception{
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(directory)){org.junit.jupiter.api.Assumptions.assumeTrue(entries instanceof SecureDirectoryStream);}
        Path location=directory.resolve("EmptyNext");ContextStore empty=ContextStore.create(location);
        try{
            Path orphan=ContextStore.generationPath(location,1);Files.createDirectories(orphan);Files.write(orphan.resolve("rules.base"),new byte[]{1,2,3});
            RevisionManifestStore.seal(orphan,empty.getContextId(),1,0);
            assertTrue(empty.reclaimInterruptedPublicationFiles()>0);assertFalse(Files.exists(orphan));assertEquals(0,empty.getRevision());
            try(ContextSnapshot initial=ContextSnapshot.open(location,0)){assertEquals(empty.getContextId(),initial.getContextId());assertEquals(0,initial.getRevision());}
        }finally{empty.close();}
    }
    @Test void nonRegularManifestIsRetainedWithoutOpeningABlockingStream()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(Paths.get("/usr/bin/mkfifo")));
        open();Path location=directory.resolve("Source"),orphan=ContextStore.generationPath(location,db.getRevision()+1);Files.createDirectory(orphan);Path fifo=RevisionManifestStore.path(orphan);
        java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{
            Process maker=new ProcessBuilder("/usr/bin/mkfifo",fifo.toString()).start();assertTrue(maker.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,maker.exitValue());
            java.util.concurrent.Future<Integer> result=executor.submit(()->InterruptedPublicationReclaimer.reclaim(location,db.getContextId(),db.getRevision()));
            boolean blocked=false;int removed;
            try{removed=result.get(5,java.util.concurrent.TimeUnit.SECONDS);}
            catch(java.util.concurrent.TimeoutException timeout){blocked=true;try(java.io.OutputStream writer=Files.newOutputStream(fifo,StandardOpenOption.WRITE)){}removed=result.get(5,java.util.concurrent.TimeUnit.SECONDS);}
            assertFalse(blocked,"unknown manifest entry must be classified before opening its byte stream");assertEquals(0,removed);assertTrue(Files.exists(orphan));
        }finally{Files.deleteIfExists(fifo);executor.shutdownNow();close();}
    }
    @Test void checkpointPrunesOnlyImmediateFutureConnectionVector()throws Exception{
        open();try{
            long current=db.getRevision();assertEquals(1,current);Path location=directory.resolve("Source");UUID owner=db.getContextId();ConnectionVector empty=ConnectionVector.empty();
            ConnectionStore.writeTransition(location,new RevisionRef(owner,0),empty,new RevisionRef(owner,1),empty);
            ConnectionStore.writeTransition(location,new RevisionRef(owner,1),empty,new RevisionRef(owner,2),empty);
            ConnectionStore.writeTransition(location,new RevisionRef(owner,2),empty,new RevisionRef(owner,3),empty);
            byte[] marker=Files.readAllBytes(ContextStore.revisionPath(location));
            user.checkpoint(mind);
            assertTrue(Files.exists(ConnectionStore.path(location)));assertEquals(empty,ConnectionStore.read(location,new RevisionRef(owner,0)));assertEquals(empty,ConnectionStore.read(location,new RevisionRef(owner,current)));assertEquals(empty,ConnectionStore.read(location,new RevisionRef(owner,3)));
            assertThrows(org.kanger.exception.StorageLifecycleException.class,()->ConnectionStore.read(location,new RevisionRef(owner,2)),"unpublished immediate successor vector must be pruned");
            byte[] once=Files.readAllBytes(ConnectionStore.path(location));user.checkpoint(mind);assertArrayEquals(once,Files.readAllBytes(ConnectionStore.path(location)));assertArrayEquals(marker,Files.readAllBytes(ContextStore.revisionPath(location)));assertEquals(current,db.getRevision());
        }finally{close();}
    }
    @Test void futureVectorIsRetainedWithUnresolvedArtifactsOrLinkedSidecar()throws Exception{
        open();try{
            Path location=directory.resolve("Source");long current=db.getRevision();UUID owner=db.getContextId();ConnectionVector empty=ConnectionVector.empty();RevisionRef next=new RevisionRef(owner,current+1);
            ConnectionStore.writeTransition(location,new RevisionRef(owner,current),empty,next,empty);
            Path orphan=ContextStore.generationPath(location,current+1);Files.createDirectory(orphan);Files.write(orphan.resolve("evidence"),new byte[]{1});
            user.checkpoint(mind);assertEquals(empty,ConnectionStore.read(location,next));assertTrue(Files.exists(orphan.resolve("evidence")));ContextStore.deleteRecursively(orphan);
            Path unknown=staging("staging",current+1,"rules.base","notes.txt");user.checkpoint(mind);assertEquals(empty,ConnectionStore.read(location,next));assertTrue(Files.exists(unknown.resolve("rules.base")));ContextStore.deleteRecursively(unknown);
            Path dubious=directory.resolve("Source.connections.tmp-unrecognized");Files.write(dubious,new byte[]{1});user.checkpoint(mind);assertTrue(Files.exists(dubious));assertEquals(empty,ConnectionStore.read(location,next));Files.delete(dubious);
            Path official=ConnectionStore.path(location),saved=directory.resolve("connections.saved");byte[] before=Files.readAllBytes(official);Files.move(official,saved);Files.createSymbolicLink(official,saved);
            try{user.checkpoint(mind);assertTrue(Files.isSymbolicLink(official));assertArrayEquals(before,Files.readAllBytes(saved));assertEquals(empty,ConnectionStore.read(location,next));}
            finally{Files.delete(official);Files.move(saved,official);}
            user.checkpoint(mind);assertThrows(org.kanger.exception.StorageLifecycleException.class,()->ConnectionStore.read(location,next));assertEquals(current,db.getRevision());
        }finally{close();}
    }
    @Test void corruptForeignOrMissingCurrentVectorBlocksAllCheckpointCleanup()throws Exception{
        open();Path location=directory.resolve("Source"),official=ConnectionStore.path(location);long current=db.getRevision();UUID owner=db.getContextId();ConnectionVector empty=ConnectionVector.empty();
        ConnectionStore.writeTransition(location,new RevisionRef(owner,current),empty,new RevisionRef(owner,current+1),empty);byte[] valid=Files.readAllBytes(official);
        try{
            Path artifact=temp("Source.revision.next-");byte[] bad=valid.clone();bad[bad.length-1]^=1;Files.write(official,bad);
            assertThrows(org.kanger.exception.StorageLifecycleException.class,()->user.checkpoint(mind));assertTrue(Files.exists(artifact));assertArrayEquals(bad,Files.readAllBytes(official));DmzCandidateSession.requireSettledPhysicalQ(mind);
            Files.delete(official);UUID foreign=UUID.randomUUID();ConnectionStore.writeTransition(location,new RevisionRef(foreign,current),empty,new RevisionRef(foreign,current+1),empty);byte[] foreignBytes=Files.readAllBytes(official);
            assertThrows(org.kanger.exception.StorageLifecycleException.class,()->user.checkpoint(mind));assertTrue(Files.exists(artifact));assertArrayEquals(foreignBytes,Files.readAllBytes(official));DmzCandidateSession.requireSettledPhysicalQ(mind);
            Files.delete(official);ConnectionStore.writeTransition(location,new RevisionRef(owner,current+1),empty,new RevisionRef(owner,current+2),empty);byte[] missingCurrent=Files.readAllBytes(official);
            assertThrows(org.kanger.exception.StorageLifecycleException.class,()->user.checkpoint(mind));assertTrue(Files.exists(artifact));assertArrayEquals(missingCurrent,Files.readAllBytes(official));DmzCandidateSession.requireSettledPhysicalQ(mind);
            Files.write(official,valid);user.checkpoint(mind);assertFalse(Files.exists(artifact));assertEquals(current,db.getRevision());
        }finally{Files.write(official,valid);close();}
    }
    @Test void firstFutureVectorCanBePrunedWhileKeepingAnExplicitEmptyR0()throws Exception{
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(directory)){org.junit.jupiter.api.Assumptions.assumeTrue(entries instanceof SecureDirectoryStream);}
        Path location=directory.resolve("EmptyVectors");ContextStore emptyStore=ContextStore.create(location);
        try{
            UUID owner=emptyStore.getContextId();ConnectionVector empty=ConnectionVector.empty();ConnectionStore.writeTransition(location,new RevisionRef(owner,0),empty,new RevisionRef(owner,1),empty);
            assertTrue(emptyStore.reclaimInterruptedPublicationFiles()>0);assertTrue(Files.exists(ConnectionStore.path(location)));assertEquals(empty,ConnectionStore.read(location,new RevisionRef(owner,0)));assertThrows(org.kanger.exception.StorageLifecycleException.class,()->ConnectionStore.read(location,new RevisionRef(owner,1)));assertEquals(0,emptyStore.getRevision());assertEquals(0,emptyStore.reclaimInterruptedPublicationFiles());
        }finally{emptyStore.close();}
    }
}
