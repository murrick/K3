package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.enums.*;
import org.kanger.interfaces.IArgument;
import org.kanger.primitives.*;
import org.kanger.units.TVariable;

/** Exact Argument/TVariable/Mind can still invoke a custom authoritative set. */
public final class ExposedSetDeletionWitness {
    private static int checks;
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("check " + checks);
        checks++;
    }
    private static final class ChangingIds extends HashSet<Long> {
        int calls;
        final RuntimeException failure;
        ChangingIds(long id, RuntimeException failure) { add(id); this.failure = failure; }
        @Override public boolean contains(Object value) {
            if (++calls == 1) return false;
            if (failure != null) throw failure;
            return super.contains(value);
        }
    }
    private static List<TVariable> singleCheck(ArgumentsList arguments, Mind mind) throws Exception {
        List<TVariable> result = new ArrayList<>();
        for (IArgument a : arguments) {
            if (a.getType() == ArgumentType.TVARIABLE && !a.isDeleted(mind)
                    && !result.contains(a.getObject(mind))) result.add((TVariable) a.getObject(mind));
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("exposed-set-").toString());
        Mind mind = new Mind(new User());
        TVariable variable = new TVariable(mind);
        Argument argument = new Argument(variable);
        ArgumentsList arguments = new ArgumentsList(); arguments.add(argument);
        check(mind.getClass() == Mind.class);
        check(variable.getClass() == TVariable.class);
        check(argument.getClass() == Argument.class);
        ChangingIds reference = new ChangingIds(variable.getId(), null);
        mind.getDeleted().put(UnitType.TVARIABLE, reference);
        check(arguments.getTVariables(mind).isEmpty());
        check(reference.calls == 2);
        ChangingIds shortcut = new ChangingIds(variable.getId(), null);
        mind.getDeleted().put(UnitType.TVARIABLE, shortcut);
        check(singleCheck(arguments, mind).equals(Collections.singletonList(variable)));
        check(shortcut.calls == 1);
        RuntimeException failure = new IllegalStateException("second set callback");
        ChangingIds throwing = new ChangingIds(variable.getId(), failure);
        mind.getDeleted().put(UnitType.TVARIABLE, throwing);
        try { arguments.getTVariables(mind); throw new AssertionError("missing failure"); }
        catch (RuntimeException actual) { check(actual == failure); }
        check(throwing.calls == 2);
        ChangingIds bypassed = new ChangingIds(variable.getId(), failure);
        mind.getDeleted().put(UnitType.TVARIABLE, bypassed);
        check(singleCheck(arguments, mind).equals(Collections.singletonList(variable)));
        check(bypassed.calls == 1);
        System.out.println("EXPOSED_SET_DELETION_WITNESS_OK scenarios=2 checks=" + checks);
    }
}
