/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IUser;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.FValue;
import org.kanger.units.Function;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Focused red regression gate for FValueFactory action rollback.
 *
 * <p>A released speculative FValue must disappear from canonical lookup and
 * must not leave the Linker continuation signal raised after the corresponding
 * mutation has been rolled back.</p>
 */
public final class KangerFValueActionRollbackSafetyRunner {

    private KangerFValueActionRollbackSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path testHome = Files.createTempDirectory("kanger-fvalue-action-rollback-");
            System.setProperty("user.home", testHome.toAbsolutePath().toString());

            IUser user = UserFactory.createUser(
                    "fvalue-action-rollback", "fvalue-action-rollback");
            new UDF().init(user);
            new DB().init(user);
            Mind mind = new Mind(user);

            ArgumentsList arguments = new ArgumentsList();
            arguments.add(new Argument(mind.getTerms().add(1.0)));
            Function function = mind.getFunctions().add(
                    mind.getTerms().add("fvalue_action_rollback"), arguments);

            require(function.setParameter(
                            function.getRange(), mind.getTerms().add(2.0)),
                    "Unable to complete baseline Function");
            FValue baseline = mind.getFValues().add(function);
            require(baseline != null, "Baseline FValue was not created");

            mind.getFValues().dropAction();
            require(!mind.getFValues().isAction(),
                    "Baseline FValue action flag was not cleared");
            int baselineSize = mind.getFValues().size();

            function.clear();
            require(function.setParameter(
                            function.getRange(), mind.getTerms().add(3.0)),
                    "Unable to complete speculative Function");

            mind.getFValues().mark();
            FValue speculative = mind.getFValues().add(function);
            require(speculative != null, "Speculative FValue was not created");
            require(speculative.getId() != baseline.getId(),
                    "Speculative Function reused the baseline result identity");
            require(mind.getFValues().isAction(),
                    "Speculative FValue mutation did not raise action");

            mind.getFValues().release();
            require(mind.getFValues().size() == baselineSize,
                    "Released speculative FValue remained in canonical cache");
            require(mind.getFValues().find(function) == null,
                    "Released speculative FValue remained findable");
            require(!mind.getFValues().isAction(),
                    "Release retained a false FValue Linker continuation signal");

            System.out.println("FVALUE_ACTION_ROLLBACK_OK");
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
