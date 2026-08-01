/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Focused red regression gate for factory-local action flags across a released
 * speculative mark.
 *
 * <p>A released mutation must restore both canonical contents and the control
 * signal consumed by Linker pass continuation. Leaving action=true after the
 * mutation has been rolled back reports a state change that no longer exists.</p>
 */
public final class KangerFactoryActionRollbackSafetyRunner {

    private KangerFactoryActionRollbackSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path testHome = Files.createTempDirectory("kanger-action-rollback-");
            System.setProperty("user.home", testHome.toAbsolutePath().toString());

            IUser user = UserFactory.createUser("action-rollback", "action-rollback");
            new UDF().init(user);
            new DB().init(user);
            Mind mind = new Mind(user);

            Rule owner = new Rule(mind);
            mind.getRules().register(owner);
            TVariable variable = mind.getTVars().createTVar(
                    owner, mind.getTerms().add("action_rollback_variable"));
            ITerm baselineTerm = mind.getTerms().add("action_rollback_baseline");
            ITerm speculativeTerm = mind.getTerms().add("action_rollback_speculative");

            TValue baseline = mind.getTValues().add(variable, baselineTerm);
            require(baseline != null, "baseline TValue was not created");
            mind.getTValues().dropAction();
            require(!mind.getTValues().isAction(),
                    "baseline action flag was not cleared");

            int baselineSize = mind.getTValues().size();
            mind.getTValues().mark();
            TValue speculative = mind.getTValues().add(variable, speculativeTerm);
            require(speculative != null, "speculative TValue was not created");
            require(mind.getTValues().isAction(),
                    "speculative mutation did not raise action");

            mind.getTValues().release();
            require(mind.getTValues().size() == baselineSize,
                    "released speculative TValue remained in canonical cache");
            require(mind.getTValues().find(variable, speculativeTerm) == null,
                    "released speculative TValue remained findable");
            require(!mind.getTValues().isAction(),
                    "release retained a false Linker continuation signal");

            System.out.println("FACTORY_ACTION_ROLLBACK_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
