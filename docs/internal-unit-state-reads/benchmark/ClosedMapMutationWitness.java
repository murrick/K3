package org.kanger;

import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.kanger.primitives.Argument;
import org.kanger.units.TVariable;

/** Stages a legal interleaving between the two original deletion expressions.
 * No authoritative map getters, custom units/sets, or production hooks are used.
 * This is a boundary witness, not a claim that the benchmark has this race. */
public final class ClosedMapMutationWitness {
    private static int checks;
    private static void check(boolean value) {
        if (!value) throw new AssertionError("check " + checks);
        ++checks;
    }
    private static void pair(Mind mind, TVariable variable, Runnable mutation) throws Exception {
        Argument argument = new Argument(variable);
        check(mind.getClass() == Mind.class);
        check(variable.getClass() == TVariable.class);
        check(argument.getClass() == Argument.class);
        CountDownLatch firstCompleted = new CountDownLatch(1);
        CountDownLatch writeCompleted = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                if (!firstCompleted.await(10, TimeUnit.SECONDS)) throw new AssertionError("first timeout");
                mutation.run();
            } catch (Throwable e) { failure.set(e); }
            finally { writeCompleted.countDown(); }
        }, "closed-map-writer");
        writer.start();
        boolean first = argument.isDeleted(mind);
        firstCompleted.countDown();
        check(writeCompleted.await(10, TimeUnit.SECONDS));
        writer.join(10000);
        check(!writer.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
        boolean second = argument.isDeleted(mind);
        check(!first);
        check(second);
        // Original !first && !second rejects; a cached first miss accepts.
        check((!first && !second) != !first);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("closed-map-mutation-").toString());
        User user = new User();
        Mind deletion = new Mind(user);
        TVariable deleted = new TVariable(deletion); deleted.setId(10);
        pair(deletion, deleted, () -> deletion.setUnitDeleted(deleted, true));

        Mind identity = new Mind(user);
        TVariable moving = new TVariable(identity); moving.setId(10);
        TVariable blocked = new TVariable(identity); blocked.setId(20);
        identity.setUnitDeleted(blocked, true);
        pair(identity, moving, () -> moving.setId(20));

        Mind topology = new Mind(user);
        Mind ancestor = new Mind(user);
        TVariable linked = new TVariable(topology); linked.setId(30);
        ancestor.setUnitDeleted(linked, true);
        pair(topology, linked, () -> topology.setNext(ancestor));
        System.out.println("CLOSED_MAP_MUTATION_WITNESS_OK scenarios=3 checks=" + checks);
    }
}
