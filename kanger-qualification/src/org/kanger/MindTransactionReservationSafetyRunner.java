/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.lang.reflect.Field;

/**
 * Focused regression gate for the direct-child reservation invariant maintained
 * by Mind.transactionCounter.
 */
public final class MindTransactionReservationSafetyRunner {

    private MindTransactionReservationSafetyRunner() {
    }

    public static void main(String[] args) {
        int exitCode = 1;
        try {
            testDirectChildAccounting();
            System.out.println("MIND_RESERVATION_PASS direct-child-accounting");

            testUnderflowGuard();
            System.out.println("MIND_RESERVATION_PASS underflow-guard");

            System.out.println("MIND_RESERVATION_OK");
            exitCode = 0;
        } catch (Throwable error) {
            error.printStackTrace(System.err);
        }
        System.exit(exitCode);
    }

    private static Mind newMind(String name) throws Exception {
        IUser user = UserFactory.createUser(name, name);
        new UDF().init(user);
        new DB().init(user);
        return new Mind(user);
    }

    private static void testDirectChildAccounting() throws Exception {
        Mind root = newMind("mind-reservation-direct");
        require(counter(root) == 0, "fresh root counter must be zero");

        Mind child = new Mind(root);
        require(counter(root) == 1,
                "root must reserve exactly one direct child");
        require(counter(child) == 0,
                "new child must not inherit the parent's reservation count");

        Mind grandchild = new Mind(child);
        require(counter(root) == 1,
                "grandchild must not increment the root direct-child count");
        require(counter(child) == 1,
                "child must reserve its own direct grandchild");

        child.release(grandchild);
        require(counter(child) == 0,
                "child reservation must close on grandchild release");
        require(counter(root) == 1,
                "closing a grandchild must not close the root child reservation");

        root.release(child);
        require(counter(root) == 0,
                "root reservation must return to zero after child release");
    }

    private static void testUnderflowGuard() throws Exception {
        Mind root = newMind("mind-reservation-underflow");
        Mind child = new Mind(root);
        root.release(child);
        require(counter(root) == 0,
                "first release must close the reservation");

        boolean rejected = false;
        try {
            root.release(child);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        require(rejected,
                "duplicate release must be rejected by the underflow guard");
        require(counter(root) == 0,
                "underflow rejection must not make the counter negative");
    }

    private static int counter(Mind mind) throws Exception {
        Field field = Mind.class.getDeclaredField("transactionCounter");
        field.setAccessible(true);
        return field.getInt(mind);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
