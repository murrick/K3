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
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Focused regression gate for TValue's lexicographic natural ordering by
 * (TVariable id, TValue id) over the complete long domain.
 */
public final class KangerTValueComparableSafetyRunner {

    private KangerTValueComparableSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path testHome = Files.createTempDirectory("kanger-tvalue-ordering-");
            System.setProperty("user.home", testHome.toAbsolutePath().toString());

            IUser user = UserFactory.createUser("tvalue-ordering", "tvalue-ordering");
            new UDF().init(user);
            new DB().init(user);
            Mind mind = new Mind(user);
            ITerm value = mind.getTerms().add("ordering_value");

            // Same variable: ordering is determined by TValue id.
            assertOrder(mind, value, 7L, 0L, 7L, 1L);
            assertOrder(mind, value, 7L, 0L, 7L, 1L << 32);
            assertOrder(mind, value, 7L, Long.MIN_VALUE, 7L, Long.MAX_VALUE);

            // Different variables: ordering is determined by TVariable id,
            // independently of TValue id.
            assertOrder(mind, value, 0L, Long.MAX_VALUE, 1L, Long.MIN_VALUE);
            assertOrder(mind, value, 0L, 0L, 1L << 32, 0L);
            assertOrder(mind, value, Long.MIN_VALUE, 100L, Long.MAX_VALUE, -100L);

            TValue equalLeft = value(mind, value, 42L, 99L);
            TValue equalRight = value(mind, value, 42L, 99L);
            require(equalLeft.compareTo(equalRight) == 0,
                    "equal ordering keys must compare as zero");

            System.out.println("TVALUE_COMPARABLE_PASS same-variable");
            System.out.println("TVALUE_COMPARABLE_PASS variable-order");
            System.out.println("TVALUE_COMPARABLE_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static void assertOrder(Mind mind, ITerm value,
                                    long lowerVariableId, long lowerValueId,
                                    long higherVariableId, long higherValueId) {
        TValue lower = value(mind, value, lowerVariableId, lowerValueId);
        TValue higher = value(mind, value, higherVariableId, higherValueId);
        int forward = lower.compareTo(higher);
        int reverse = higher.compareTo(lower);

        require(forward < 0,
                "expected lower key to compare first, got " + forward);
        require(reverse > 0,
                "expected higher key to compare last, got " + reverse);
        require(Integer.signum(forward) == -Integer.signum(reverse),
                "comparison must be antisymmetric");
    }

    private static TValue value(Mind mind, ITerm term,
                                long variableId, long valueId) {
        TVariable variable = new TVariable(mind);
        variable.setId(variableId);
        TValue value = new TValue(variable, term, mind);
        value.setId(valueId);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
