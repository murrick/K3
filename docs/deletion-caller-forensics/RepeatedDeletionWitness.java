package org.kanger;

import java.util.*;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.*;
import org.kanger.primitives.*;
import org.kanger.units.TVariable;

/** A hypothetical one-check collector differs from the reference public API. */
public final class RepeatedDeletionWitness {
    private static int checks;
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("check " + checks);
        checks++;
    }
    private static final class ChangingVariable extends TVariable {
        int calls;
        final RuntimeException failure;
        ChangingVariable(RuntimeException failure) { this.failure = failure; }
        @Override public boolean isDeleted(IMind mind) {
            if (++calls == 2) {
                if (failure != null) throw failure;
                return true;
            }
            return false;
        }
    }
    private static ArgumentsList arguments(ChangingVariable variable) {
        ArgumentsList result = new ArgumentsList();
        result.add(new Argument(variable)); // Exact built-in Argument, custom variable.
        return result;
    }
    // Deliberately hypothetical, for this single-variable witness only.
    private static List<TVariable> singleCheck(ArgumentsList arguments) throws Exception {
        List<TVariable> result = new ArrayList<>();
        for (IArgument a : arguments) {
            if (a.getType() == ArgumentType.TVARIABLE && !a.isDeleted(null)
                    && !result.contains(a.getObject(null))) {
                result.add((TVariable) a.getObject(null));
            }
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        ChangingVariable reference = new ChangingVariable(null);
        check(arguments(reference).getTVariables(null).isEmpty());
        check(reference.calls == 2);
        ChangingVariable shortcut = new ChangingVariable(null);
        check(singleCheck(arguments(shortcut)).equals(Collections.singletonList(shortcut)));
        check(shortcut.calls == 1);
        RuntimeException failure = new IllegalStateException("second deletion callback");
        ChangingVariable throwing = new ChangingVariable(failure);
        try { arguments(throwing).getTVariables(null); throw new AssertionError("missing failure"); }
        catch (RuntimeException actual) { check(actual == failure); }
        check(throwing.calls == 2);
        ChangingVariable bypassed = new ChangingVariable(failure);
        check(singleCheck(arguments(bypassed)).equals(Collections.singletonList(bypassed)));
        check(bypassed.calls == 1);
        System.out.println("REPEATED_DELETION_WITNESS_OK scenarios=2 checks=" + checks);
    }
}
