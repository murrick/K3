/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.kanger.Mind;

/** Guest entry points for a cold cut around the canonical publication. */
public final class ContextPrivatePublicationBlockRunner {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("A guest phase is required");
        System.setProperty("user.home", "/root");
        Path directory = Paths.get("/store");
        switch (args[0]) {
            case "write-native-private":
                write(directory, false);
                break;
            case "write-resume-native-private":
                write(directory, true);
                break;
            case "read-native-private":
                long selected = RevisionStore.read(ContextStore.revisionPath(directory.resolve("Source")));
                ContextPrivatePublicationWitness.verifyRecovery(directory, selected);
                System.out.println("KANGER_BLOCK_NATIVE_RECOVERED operation=private previous=2 selected=" + selected);
                break;
            default:
                throw new IllegalArgumentException("Unknown guest phase: " + args[0]);
        }
        for (;;) Thread.sleep(1000);
    }

    private static void write(Path directory, boolean resume) throws Exception {
        DB source = new DB();
        Mind mind = resume
            ? ContextPrivatePublicationWitness.open(source, directory, "Source")
            : ContextPrivatePublicationWitness.seed(source, directory);
        if (source.getRevision() != 2) throw new AssertionError("Acknowledged Source R2 required");
        System.out.println("KANGER_BLOCK_PREVIOUS_ACK operation=private previous=2");
        if (System.in.read() < 0) throw new AssertionError("Host release required");
        ContextPrivatePublicationWitness.publish(source, mind);
        System.out.println("KANGER_BLOCK_NEXT_ACK operation=private revision=3");
    }
}
