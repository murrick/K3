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
 * Regression gate for TValue canonical identity across logical deletion and
 * resurrection.
 *
 * <p>Before factory pack physically removes a logically deleted TValue,
 * adding the same (TVariable, ITerm) pair must restore the existing canonical
 * instance. After physical removal, a later add is allowed to allocate a new
 * identity.</p>
 */
public final class KangerTValueResurrectionSafetyRunner {

    private KangerTValueResurrectionSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path testHome = Files.createTempDirectory("kanger-tvalue-resurrection-");
            System.setProperty("user.home", testHome.toAbsolutePath().toString());

            IUser user = UserFactory.createUser(
                    "tvalue-resurrection", "tvalue-resurrection");
            new UDF().init(user);
            new DB().init(user);
            Mind mind = new Mind(user);

            Rule owner = new Rule(mind);
            mind.getRules().register(owner);
            TVariable variable = mind.getTVars().createTVar(
                    owner, mind.getTerms().add("resurrection_variable"));
            ITerm value = mind.getTerms().add("resurrection_value");

            TValue original = mind.getTValues().add(variable, value);
            long originalId = original.getId();
            original.setDeleted(true, mind);
            require(original.isDeleted(mind),
                    "logical deletion mark was not visible");

            TValue restored = mind.getTValues().add(variable, value);
            require(restored == original,
                    "logical resurrection replaced the canonical TValue instance");
            require(restored.getId() == originalId,
                    "logical resurrection changed the canonical TValue id");
            require(!restored.isDeleted(mind),
                    "logical resurrection did not clear the deletion mark");
            require(mind.getTValues().find(variable, value) == restored,
                    "factory lookup did not return the restored canonical TValue");

            restored.setDeleted(true, mind);
            mind.getTValues().pack();
            require(mind.getTValues().find(variable, value) == null,
                    "pack did not physically remove the deleted TValue");

            TValue replacement = mind.getTValues().add(variable, value);
            require(replacement != restored,
                    "post-pack add reused a physically removed TValue instance");
            require(replacement.getId() != originalId,
                    "post-pack add reused a physically removed TValue id");
            require(!replacement.isDeleted(mind),
                    "post-pack replacement was created deleted");

            System.out.println("TVALUE_RESURRECTION_PASS logical-identity");
            System.out.println("TVALUE_RESURRECTION_PASS physical-boundary");
            System.out.println("TVALUE_RESURRECTION_OK");
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
