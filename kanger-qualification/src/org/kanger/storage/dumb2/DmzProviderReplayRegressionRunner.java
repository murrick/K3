/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

/** Both source transports share one JVM to verify observation owner cleanup. */
public final class DmzProviderReplayRegressionRunner {
    private DmzProviderReplayRegressionRunner() { }
    public static void main(String[] args) {
        try {
            DmzPinnedReplayRunner.main(args);
            DmzPortableReplayRunner.main(args);
            System.out.println("DMZ_PROVIDER_REPLAY_REGRESSION_PASS");
            System.exit(0);
        } catch(Throwable failure) {
            failure.printStackTrace();System.exit(1);
        }
    }
}
