/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.test.*;
import java.nio.file.Files;

/** Reuses the five existing core corpora; mode is selected by the JVM property. */
public final class LatentSubstitutionCorpusRunner {
    private static Mind mind(String name) throws Exception {
        User user = (User) UserFactory.createUser(name, name);
        new UDF().init(user);
        new DB().init(user);
        return new Mind(user);
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("latent-corpus-").toString());
        boolean ok = KangerCompletedTest.test(mind("completed"), "set_");
        ok &= KangerStabilizationTest.test(mind("stabilization"), "set_");
        ok &= KangerC1PromotionTest.test(mind("promotion"), "set_");
        ok &= KangerC4IntervalTest.test(mind("interval"), "set_");
        ok &= KangerC3BindingTest.test(mind("binding"), "set_");
        if (!ok) throw new AssertionError("Latent experiment corpus failed");
        System.out.println("LATENT_CORPUS_PASS");
    }
}
