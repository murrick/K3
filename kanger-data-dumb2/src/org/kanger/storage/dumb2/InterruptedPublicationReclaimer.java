/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.*;

/** Reserved temporary namespaces only; caller owns the validated Context writer lock. */
final class InterruptedPublicationReclaimer {
    private static final Pattern STAGING=Pattern.compile("\\.(staging|topology|reindex|reclaim)-([1-9][0-9]*)-([0-9a-f-]{36})");
    private static final Pattern BASE=Pattern.compile("(?:[a-zA-Z0-9_.-]|%[0-9A-F]{2})+\\.base");
    private InterruptedPublicationReclaimer(){}

    static int reclaim(Path location,UUID contextId,long revision)throws IOException {
        int removed=0;
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(location.getParent())){
            // Retention is preferable to path-based traversal when secure relative operations are unavailable.
            if(!(entries instanceof SecureDirectoryStream))return 0;
            SecureDirectoryStream<Path> parent=(SecureDirectoryStream<Path>)entries;
            String marker=ContextStore.revisionPath(location).getFileName()+".next-";
            String connections=ConnectionStore.path(location).getFileName()+".tmp-";
            String state=ContextStore.stateRoot(location).getFileName().toString();
            for(Path entry:entries){
                Path name=entry.getFileName();String text=name.toString();
                if(temporary(text,marker)||temporary(text,connections)){
                    if(attributes(parent,name).isRegularFile()){parent.deleteFile(name);removed++;}
                }else if(text.equals(state)&&attributes(parent,name).isDirectory()){
                    try(SecureDirectoryStream<Path> root=parent.newDirectoryStream(name,LinkOption.NOFOLLOW_LINKS)){
                        List<Path> candidates=new ArrayList<>();for(Path candidate:root)candidates.add(candidate.getFileName());
                        for(Path relative:candidates){
                            String candidate=relative.toString();
                            if(staging(candidate,revision)&&attributes(root,relative).isDirectory())removed+=removeStaging(root,relative,location);
                            else if(revision<Long.MAX_VALUE&&candidate.equals("revision-"+(revision+1))&&attributes(root,relative).isDirectory())
                                removed+=removeOrphan(root,relative,location,contextId,revision);
                        }
                    }
                }
            }
        }
        return removed;
    }
    /** Retain transition evidence if any immediate-successor artifact could not be reclaimed. */
    static boolean mayPruneFutureVector(Path location,long revision)throws IOException {
        if(revision==Long.MAX_VALUE)return false;
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(location.getParent())){
            if(!(entries instanceof SecureDirectoryStream))return false;
            SecureDirectoryStream<Path> parent=(SecureDirectoryStream<Path>)entries;
            for(Path entry:entries){String name=entry.getFileName().toString();
                if(name.startsWith(ContextStore.revisionPath(location).getFileName()+".next-")||name.startsWith(ConnectionStore.path(location).getFileName()+".tmp-"))return false;
            }
            Path state=ContextStore.stateRoot(location).getFileName();
            BasicFileAttributes info;
            try{info=attributes(parent,state);}catch(NoSuchFileException absent){return true;}
            if(!info.isDirectory())return false;
            try(SecureDirectoryStream<Path> root=parent.newDirectoryStream(state,LinkOption.NOFOLLOW_LINKS)){
                String next=Long.toString(revision+1);
                for(Path entry:root){String name=entry.getFileName().toString();
                    if(name.equals("revision-"+next))return false;
                    for(String kind:new String[]{"staging","topology","reindex","reclaim"})if(name.startsWith("."+kind+"-"+next+"-"))return false;
                }
            }
            return true;
        }
    }
    private static int removeOrphan(SecureDirectoryStream<Path> root,Path name,Path location,UUID contextId,long revision)throws IOException {
        try(SecureDirectoryStream<Path> candidate=root.newDirectoryStream(name,LinkOption.NOFOLLOW_LINKS)){
            RevisionManifestStore.validate(candidate,ContextStore.stateRoot(location).resolve(name),contextId,revision+1,revision);
        }catch(IOException|org.kanger.exception.StorageLifecycleException uncertain){return 0;}
        try(SecureDirectoryStream<Path> candidate=root.newDirectoryStream(name,LinkOption.NOFOLLOW_LINKS)){
            for(Path child:candidate){String text=child.getFileName().toString();if(!(text.equals(RevisionManifestStore.FILE_NAME)||BASE.matcher(text).matches())||!attributes(candidate,child.getFileName()).isRegularFile())return 0;}
        }
        // A retirement rename preserves the ownership decision across partial deletion or process death.
        Path retired=name.getFileSystem().getPath(".reclaim-"+(revision+1)+"-"+UUID.randomUUID());
        root.move(name,root,retired);
        return removeStaging(root,retired,location);
    }
    private static int removeStaging(SecureDirectoryStream<Path> root,Path name,Path location)throws IOException {
        List<Path> files=new ArrayList<>();
        try(SecureDirectoryStream<Path> staging=root.newDirectoryStream(name,LinkOption.NOFOLLOW_LINKS)){
            for(Path entry:staging){
                Path relative=entry.getFileName();String text=relative.toString();
                // Scan the whole directory before touching it. Unknown entries, subdirectories
                // or links retain the entire candidate rather than partially consuming evidence.
                if(!attributes(staging,relative).isRegularFile()||!(text.equals(RevisionManifestStore.FILE_NAME)||BASE.matcher(text).matches()))return 0;
                files.add(relative);
            }
            for(Path file:files)staging.deleteFile(file);
        }
        root.deleteDirectory(name);
        return files.size()+1;
    }
    private static BasicFileAttributes attributes(SecureDirectoryStream<Path> directory,Path name)throws IOException {
        return directory.getFileAttributeView(name,BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS).readAttributes();
    }
    private static boolean temporary(String name,String prefix){return name.startsWith(prefix)&&uuid(name.substring(prefix.length()));}
    private static boolean uuid(String value){
        try{UUID id=UUID.fromString(value);return id.toString().equals(value)&&id.version()==4&&id.variant()==2;}catch(IllegalArgumentException failure){return false;}
    }
    private static boolean staging(String name,long revision){
        Matcher match=STAGING.matcher(name);if(!match.matches()||!uuid(match.group(3)))return false;
        try{long attempt=Long.parseLong(match.group(2));return attempt<=(revision==Long.MAX_VALUE?revision:revision+1);}catch(NumberFormatException failure){return false;}
    }
}
