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
            assertEquals(0,InterruptedPublicationReclaimer.reclaim(location,0));assertArrayEquals(new byte[]{1},Files.readAllBytes(artifact));
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
}
