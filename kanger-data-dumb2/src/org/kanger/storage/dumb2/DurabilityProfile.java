/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.StorageLifecycleException;

/** Necessary eligibility checks, not a certificate that a particular device survives power loss. */
final class DurabilityProfile {
    static final String ID = "linux-ext4-ordered-barriers-v1";
    private DurabilityProfile() { }

    static Inspection inspect() throws StorageLifecycleException {
        if (!"Linux".equals(System.getProperty("os.name"))
                || !"sun.nio.fs.LinuxFileSystemProvider".equals(FileSystems.getDefault().provider().getClass().getName()))
            throw unsupported("storage", "requires the Linux default file provider");
        try {
            return new Inspection(parseMounts(Files.readAllLines(Paths.get("/proc/self/mountinfo"), StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException failure) {
            throw unavailable("storage", failure);
        }
    }

    static void requireContext(Path location) throws StorageLifecycleException {
        inspect().requireContext(location);
    }

    static void requireMarker(Path marker) throws StorageLifecycleException {
        Inspection inspection=inspect();
        String label=marker.getFileName().toString();
        inspection.requirePath(marker,label,true);
        for(Path parent=marker.toAbsolutePath().normalize().getParent();parent!=null;parent=parent.getParent())
            inspection.requirePath(parent,label,false);
    }

    static final class Inspection {
        private final List<Mount> mounts;
        private final Map<String,Set<String>> options = new HashMap<String,Set<String>>();
        Inspection(List<Mount> mounts) { this.mounts = mounts; }

        void requireContext(Path location) throws StorageLifecycleException {
            String label=location.getFileName().toString();
            for(Path path:new Path[]{ContextStore.contextPath(location),ContextStore.revisionPath(location),
                    ConnectionStore.path(location),ContextStore.stateRoot(location)}) requirePath(path,label,true);
            for(Path parent=location.toAbsolutePath().normalize().getParent();parent!=null;parent=parent.getParent())
                requirePath(parent,label,false);
        }

        void requireTree(Path path,String label) throws StorageLifecycleException {
            requirePath(path,label,true);
            if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)) {
                try(DirectoryStream<Path> entries=Files.newDirectoryStream(path)) {
                    for(Path entry:entries) requireTree(entry,label);
                } catch(IOException failure) { throw unavailable(label,failure); }
            }
        }

        private void requirePath(Path path,String label,boolean writable) throws StorageLifecycleException {
            if(path.getFileSystem()!=FileSystems.getDefault())
                throw unsupported(label,"non-default filesystem provider");
            try {
                Path existing=nearestExisting(path);
                FileStore store=Files.getFileStore(existing);
                if(!"ext4".equals(store.type())) throw unsupported(label,"filesystem "+store.type()+" is outside the profile");
                // FileStore.isReadOnly may describe the underlying mount rather than
                // this writable bind view. Use the effective per-path mount flags below.
                Mount mount=selectMount(mounts,existing);
                if(!"ext4".equals(mount.type)) throw unsupported(label,"mount evidence does not identify ext4");
                if(writable&&mount.flags.contains("ro")) throw unsupported(label,"mount view is read-only");
                Set<String> active=options.get(mount.device);
                if(active==null) {
                    Path device=Paths.get("/sys/dev/block",mount.device).toRealPath();
                    String name=device.getFileName().toString();
                    if(!name.matches("[A-Za-z0-9._-]+")||name.equals(".")||name.equals(".."))
                        throw new IOException("Invalid block-device evidence");
                    active=new HashSet<String>(Files.readAllLines(Paths.get("/proc/fs/ext4",name,"options"),StandardCharsets.UTF_8));
                    options.put(mount.device,active);
                }
                verifyOptions(active,label);
            } catch(IOException | RuntimeException failure) { throw unavailable(label,failure); }
        }
    }

    private static Path nearestExisting(Path path) throws IOException {
        Path candidate=path.toAbsolutePath().normalize();
        while(candidate!=null) {
            try { return candidate.toRealPath(); }
            catch(NoSuchFileException absent) { candidate=candidate.getParent(); }
        }
        throw new IOException("No existing filesystem ancestor");
    }

    static void verifyOptions(Set<String> options,String label) throws StorageLifecycleException {
        if(!(options.contains("barrier")||options.contains("barrier=1"))
                ||options.contains("nobarrier")||options.contains("barrier=0"))
            throw unsupported(label,"active ext4 journal barriers are not verified");
        if(!options.contains("data=ordered")||options.contains("data=writeback")||options.contains("data=journal")
                ||options.contains("noload")||options.contains("norecovery")||options.contains("journal_async_commit")
                ||options.contains("dax")||options.contains("dax=always"))
            throw unsupported(label,"active ext4 mode is outside the ordered-journal profile");
    }

    static final class Mount {
        final Path point;
        final String device,type;
        final Set<String> flags;
        Mount(Path point,String device,String type,Set<String> flags) {
            this.point=point;this.device=device;this.type=type;this.flags=flags;
        }
    }

    static List<Mount> parseMounts(List<String> lines) throws IOException {
        List<Mount> result=new ArrayList<Mount>();
        for(String line:lines) {
            int split=line.indexOf(" - ");
            if(split<0)throw new IOException("Malformed mount evidence");
            String[] before=line.substring(0,split).split(" +"),after=line.substring(split+3).split(" +");
            if(before.length<6||after.length<3||!before[2].matches("[0-9]+:[0-9]+"))
                throw new IOException("Malformed mount evidence");
            Path point=Paths.get(unescape(before[4]));
            if(!point.isAbsolute())throw new IOException("Non-absolute mount evidence");
            result.add(new Mount(point.normalize(),before[2],after[0],new HashSet<String>(Arrays.asList(before[5].split(",")))));
        }
        if(result.isEmpty())throw new IOException("Missing mount evidence");
        return result;
    }

    static Mount selectMount(List<Mount> mounts,Path path) throws IOException {
        Mount selected=null;int length=-1;boolean ambiguous=false;
        for(Mount mount:mounts)if(path.startsWith(mount.point)) {
            int size=mount.point.toString().length();
            if(size>length){selected=mount;length=size;ambiguous=false;}
            else if(size==length)ambiguous=true;
        }
        if(selected==null||ambiguous)throw new IOException("Effective mount is missing or ambiguous");
        return selected;
    }

    private static String unescape(String value) {
        return value.replace("\\040"," ").replace("\\011","\t").replace("\\012","\n").replace("\\134","\\");
    }

    private static StorageLifecycleException unavailable(String label,Throwable cause) {
        StorageLifecycleException refusal=unsupported(label,"cannot verify current filesystem settings");
        refusal.addSuppressed(cause);return refusal;
    }

    private static StorageLifecycleException unsupported(String label,String reason) {
        return new StorageLifecycleException(StorageLifecycleErrorCode.STORAGE_DURABILITY_UNSUPPORTED,
                "Durability profile "+ID+" is unavailable for "+label+": "+reason);
    }
}
