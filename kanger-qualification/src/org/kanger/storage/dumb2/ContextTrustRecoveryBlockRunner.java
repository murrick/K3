/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Cut at the saved trust vector acknowledgement and reopen in a fresh guest. */
public final class ContextTrustRecoveryBlockRunner {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", "/root");
        Path data = Paths.get("/store");
        switch (args[0]) {
            case "write-trust":
                ContextTrustRecoveryWitness.publish(data);
                System.out.println("KANGER_TRUST_ACK revision=2");
                break;
            case "read-trust":
                ContextTrustRecoveryWitness.verify(data);
                System.out.println("KANGER_TRUST_RECOVERED revision=2");
                break;
            default:
                throw new IllegalArgumentException("Unexpected trust phase");
        }
        for (;;) Thread.sleep(1000);
    }
}
