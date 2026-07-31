/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.factory.DomainFactory;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * Focused regression gate for DomainFactory auxiliary waiter metadata across a
 * released composite checkpoint.
 *
 * <p>Escalera rollback and factory-owned inference metadata must describe the
 * same visible transaction state. A child waiter promoted under a mark must
 * disappear when that mark is released.</p>
 */
public final class KangerDomainWaiterRollbackSafetyRunner {

    private KangerDomainWaiterRollbackSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            Path testHome = Files.createTempDirectory("kanger-domain-waiter-rollback-");
            System.setProperty("user.home", testHome.toAbsolutePath().toString());

            IUser user = UserFactory.createUser(
                    "domain-waiter-rollback", "domain-waiter-rollback");
            new UDF().init(user);
            new DB().init(user);

            Mind parent = new Mind(user);
            Mind child = new Mind(parent);

            DomainFactory parentDomains = parent.getDomains();
            DomainFactory childDomains = child.getDomains();
            Set<Domain> baseline = new HashSet<>(parentDomains.getWaiters());

            Object compiled = child.compileLine(
                    "!@x domain_waiter_rollback(x);",
                    false,
                    new LinkedList<ITerm>());
            require(compiled instanceof Rule,
                    "single-domain variable rule was not compiled");

            Set<Domain> introduced = new HashSet<>(childDomains.getWaiters());
            introduced.removeAll(baseline);
            require(!introduced.isEmpty(),
                    "RuleFactory.expand did not publish a child waiter");

            parentDomains.mark();
            parentDomains.commit(childDomains);
            require(parentDomains.getWaiters().containsAll(introduced),
                    "typed DomainFactory commit did not merge child waiters");

            parentDomains.release();
            require(parentDomains.getWaiters().equals(baseline),
                    "released DomainFactory checkpoint retained child waiters");

            parent.release(child);

            System.out.println("DOMAIN_WAITER_ROLLBACK_OK");
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
