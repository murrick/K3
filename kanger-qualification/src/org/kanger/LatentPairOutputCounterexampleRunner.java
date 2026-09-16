/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import org.kanger.udf.UDF;

/** Regression for the rejected argument-only output-replay hypothesis. */
public final class LatentPairOutputCounterexampleRunner {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("latent-pair-counterexample-").toString());
        System.setProperty("kanger.experiment.verifyPairOutputs", "true");
        boolean rejected = false;
        try {
            fixture();
        } catch (AssertionError expected) {
            String message = expected.getMessage();
            if (message == null || !message.startsWith("Pair output mismatch:")
                    || !message.contains("first=result=true,commit=true")
                    || !message.contains("actual=result=false,commit=false")) throw expected;
            rejected = true;
            System.out.println(message);
        } finally {
            System.clearProperty("kanger.experiment.verifyPairOutputs");
        }
        if (!rejected) throw new AssertionError("Expected replay counterexample was not detected");
        fixture(); // The underlying inference remains valid without the rejected assertion.
        System.out.println("LATENT_PAIR_OUTPUT_COUNTEREXAMPLE_PASS");
    }

    private static void fixture() throws Exception {
        User user = new User();
        new UDF().init(user);
        Mind mind = new Mind(user);
        if (!mind.compile("!num(0); !@x num(x) && x < 10 -> num(++x);"))
            throw new AssertionError("Fixture compilation failed");
        if (!Boolean.TRUE.equals(mind.query("?$x num(x);", null, false)))
            throw new AssertionError("Fixture query failed");
        if (mind.getValues().isEmpty()) throw new AssertionError("Fixture lost values");
    }
}
