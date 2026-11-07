/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.Collections;
import java.util.UUID;

/** Constant arithmetic binds as semantic values without admitting variable-bearing calls. */
public final class ContextClosedArithmeticBindingRunner {
    private static int checks;

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("closed-arithmetic-binding-").toString());
        for (String expression : new String[]{"1+1", "7-5", "2*3", "8/2", "(1+1)*3"}) {
            verify("!next(" + expression + ");", true);
            verify("!@x fun(x)->next(" + expression + ");", true);
        }
        verify("!@x fun(x)->next(x+1);", false);
        System.out.println("CONTEXT_CLOSED_ARITHMETIC_BINDING_PASS checks=" + checks);
        System.exit(0);
    }

    private static void verify(String source, boolean expected) throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile(source, null, false)) throw new AssertionError("fixture " + source);
        UUID context = UUID.randomUUID();
        ContextProductionInventory inventory = ContextProductionInventory.inspect(
                mind, context, 0, true, Collections.emptyList(), 1000);
        ContextProductionNativeBinding.Result result =
                ContextProductionNativeBinding.bind(mind, inventory, 1000000, 100000);
        checks++;
        if (result.bound != expected) throw new AssertionError(source + " bound=" + result.bound + " " + result.reason);
        checks++;
        if (!inventory.fingerprint.equals(ContextProductionInventory.inspect(
                mind, context, 0, true, Collections.emptyList(), 1000).fingerprint)) {
            throw new AssertionError("binding changed source");
        }
        if (expected) {
            checks++;
            if (result.snapshot.primary.size() != 1 || !result.snapshot.primary.get(0).source.context.equals(context)) {
                throw new AssertionError("binding preserves whole source identity");
            }
        }
    }
}
