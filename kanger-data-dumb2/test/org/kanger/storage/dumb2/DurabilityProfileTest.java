/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.kanger.exception.StorageLifecycleException;
import static org.junit.jupiter.api.Assertions.*;

class DurabilityProfileTest {
    private Set<String> settings(String... options){return new HashSet<String>(Arrays.asList(options));}
    @Test void successRequiresExplicitActiveBarrierAndOrderedMode()throws Exception{
        assertDoesNotThrow(()->DurabilityProfile.verifyOptions(settings("rw","barrier","data=ordered"),"Rules"));
        for(Set<String> options:Arrays.asList(settings("rw","data=ordered"),settings("rw","nobarrier","data=ordered"),
                settings("rw","barrier","barrier=0","data=ordered"),settings("rw","barrier","data=writeback"),
                settings("rw","barrier","data=journal"),settings("rw","barrier","data=ordered","noload"),
                settings("rw","barrier","data=ordered","journal_async_commit"),settings("rw","barrier","data=ordered","dax"))){
            StorageLifecycleException failure=assertThrows(StorageLifecycleException.class,()->DurabilityProfile.verifyOptions(options,"Rules"));
            assertEquals("STORAGE_DURABILITY_UNSUPPORTED",failure.getCode());
            assertEquals("SELECT_SUPPORTED_DURABILITY_STORAGE",failure.getRequiredAction());assertTrue(failure.toString().contains("Rules"));
        }
    }
    @Test void nestedMountAndWritableBindViewOverrideTheirAncestors()throws Exception{
        List<DurabilityProfile.Mount> mounts=DurabilityProfile.parseMounts(Arrays.asList(
                "10 1 8:1 / / ro - ext4 /dev/sda1 rw",
                "11 10 8:1 /tmp /tmp rw - ext4 /dev/sda1 rw",
                "12 11 0:27 / /tmp/ram rw - tmpfs tmpfs rw"));
        assertTrue(DurabilityProfile.selectMount(mounts,Paths.get("/tmp/Rules")).flags.contains("rw"));
        assertEquals("tmpfs",DurabilityProfile.selectMount(mounts,Paths.get("/tmp/ram/Rules")).type);
        assertEquals("ext4",DurabilityProfile.selectMount(mounts,Paths.get("/tmp/ramdisk/Rules")).type);
    }
    @Test void mountEscapesAreDecodedOnce()throws Exception{
        List<DurabilityProfile.Mount> mounts=DurabilityProfile.parseMounts(Arrays.asList(
                "10 1 8:1 / / rw - ext4 /dev/sda1 rw",
                "11 10 8:1 / /project\\040data rw - ext4 /dev/sda1 rw",
                "12 10 8:1 / /literal\\134040 rw - ext4 /dev/sda1 rw"));
        assertEquals(Paths.get("/project data"),DurabilityProfile.selectMount(mounts,Paths.get("/project data/Rules")).point);
        assertEquals(Paths.get("/literal\\040"),DurabilityProfile.selectMount(mounts,Paths.get("/literal\\040/Rules")).point);
    }
    @Test void missingMalformedAndAmbiguousMountEvidenceRefusesInsteadOfGuessing()throws Exception{
        assertThrows(IOException.class,()->DurabilityProfile.parseMounts(Collections.emptyList()));
        assertThrows(IOException.class,()->DurabilityProfile.parseMounts(Collections.singletonList("not mountinfo")));
        List<DurabilityProfile.Mount> mounts=DurabilityProfile.parseMounts(Arrays.asList(
                "10 1 8:1 / /data rw - ext4 /dev/sda1 rw",
                "11 1 0:27 / /data rw - tmpfs tmpfs rw"));
        assertThrows(IOException.class,()->DurabilityProfile.selectMount(mounts,Paths.get("/data/Rules")));
        assertThrows(IOException.class,()->DurabilityProfile.selectMount(mounts,Paths.get("/other/Rules")));
    }
}
