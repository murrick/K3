/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import org.kanger.ContextQualification;
import org.kanger.Mind;
import org.kanger.User;

/** Minimal native qualification gate for arithmetic function replay in both directions. */
public final class ContextFunctionPairDirectionRunner {
    private static final String FACTS = "!fun(1); !~next(2);";
    private static final String RULE = "!@x fun(x)->next(x+1);";

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", java.nio.file.Files.createTempDirectory("function-pair-direction-").toString());
        Mind positive = memory(RULE + " !fun(1);");
        if (!Boolean.TRUE.equals(positive.query("?next(2);", null, false))) {
            throw new AssertionError("native arithmetic produces next(2) from fun(1)");
        }
        Mind plain = memory("");
        PairQualification.PortableSource.capture(memory("!@x fun(x)->next(x);")).replay(plain);
        PairQualification.PortableSource.capture(memory("!fun(1); !~next(1);")).replay(plain);
        if (ContextQualification.inspect(plain, false).isValid()) {
            throw new AssertionError("the relational control detects its contradiction");
        }
        System.out.println("native arithmetic and relational conflict controls passed");
        int missing = 0;
        for (boolean reverse : new boolean[]{false, true}) {
            Mind target = memory(reverse ? FACTS : RULE);
            Mind source = memory(reverse ? RULE : FACTS);
            Mind overlay = Mind.ephemeralChild(target);
            try {
                PairQualification.PortableSource.capture(source).replay(overlay);
                boolean valid = ContextQualification.inspect(overlay, false).isValid();
                System.out.println("function overlay reverse=" + reverse + " valid=" + valid);
                if (valid) missing++;
            } finally {
                target.discardEphemeral(overlay);
            }
        }
        for (boolean reverse : new boolean[]{false, true}) {
            Mind flat = memory("");
            PairQualification.PortableSource.capture(memory(reverse ? FACTS : RULE)).replay(flat);
            PairQualification.PortableSource.capture(memory(reverse ? RULE : FACTS)).replay(flat);
            boolean valid = ContextQualification.inspect(flat, false).isValid();
            System.out.println("function flat reverse=" + reverse + " valid=" + valid);
            if (valid) missing++;
        }
        if (missing != 0) {
            System.err.println("CONTEXT_FUNCTION_PAIR_DIRECTION_FAIL missing=" + missing);
            System.exit(1);
        }
        System.out.println("CONTEXT_FUNCTION_PAIR_DIRECTION_PASS");
        System.exit(0);
    }

    private static Mind memory(String source) throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!source.isEmpty() && !mind.compile(source, null, false)) {
            throw new AssertionError("independently consistent fixture");
        }
        return mind;
    }
}
