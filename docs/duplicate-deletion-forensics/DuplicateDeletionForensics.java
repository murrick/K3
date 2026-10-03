import java.nio.file.Files;
import java.util.*;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IMind;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.Function;
import org.kanger.units.TVariable;

/** Diagnostic counterexamples only: production getTVariables remains untouched. */
public final class DuplicateDeletionForensics {
    static int checks;
    static void check(boolean condition) {
        if (!condition) throw new AssertionError("check " + checks);
        checks++;
    }
    // Exact reference body with only the adjacent second isDeleted call removed.
    static List<TVariable> singleCheck(ArgumentsList arguments, IMind mind) throws Exception {
        List<TVariable> list = new ArrayList<>();
        for (IArgument a : arguments) {
            if (a.getType() == ArgumentType.TVARIABLE
                    && !a.isDeleted(mind)
                    && !list.contains(a.getObject(mind))) {
                list.add((TVariable) a.getObject(mind));
            } else if (a.getType() == ArgumentType.FUNCTION) {
                List<TVariable> temp = singleCheck(((Function) a.getObject(mind)).getArguments(), mind);
                for (TVariable t : temp) if (!list.contains(t)) list.add(t);
            }
        }
        return list;
    }
    static final class StatefulArgument extends Argument {
        int calls;
        final RuntimeException failure;
        StatefulArgument(TVariable variable, RuntimeException failure) { super(variable); this.failure = failure; }
        @Override public boolean isDeleted(IMind mind) {
            calls++;
            if (calls == 2 && failure != null) throw failure;
            return calls == 2;
        }
    }
    static final class StatefulSet extends HashSet<Long> {
        int calls;
        final RuntimeException failure;
        StatefulSet(RuntimeException failure) { this.failure = failure; }
        @Override public boolean contains(Object value) {
            calls++;
            if (calls == 2 && failure != null) throw failure;
            boolean present = super.contains(value);
            if (calls == 1 && value instanceof Long) super.add((Long) value);
            return present;
        }
    }
    static final class MutatingParent extends Mind {
        Mind child;
        int calls;
        MutatingParent(User user) throws Exception { super(user); }
        @Override public IMind getNext() {
            if (child == null) return null; // Mind constructor initializes factories through getNext.
            calls++;
            // Child was already inspected in the first traversal. The second
            // deletion check must observe the newly installed child mark.
            child.getDeleted().computeIfAbsent(UnitType.TVARIABLE, key -> new HashSet<Long>()).add(77L);
            return null;
        }
    }
    static final class Fixture {
        final Mind mind;
        final TVariable variable;
        final ArgumentsList arguments = new ArgumentsList();
        final RuntimeException failure = new IllegalStateException("second deletion check");
        StatefulArgument customArgument;
        StatefulSet customSet;
        MutatingParent parent;
        Fixture(int scenario) throws Exception {
            User user = new User();
            mind = new Mind(user);
            variable = new TVariable(mind);
            variable.setId(77L);
            if (scenario == 1 || scenario == 2) {
                customArgument = new StatefulArgument(variable, scenario == 2 ? failure : null);
                arguments.add(customArgument);
            } else arguments.add(new Argument(variable));
            if (scenario == 3) {
                parent = new MutatingParent(user);
                parent.child = mind;
                mind.setNext(parent);
            }
            if (scenario == 4 || scenario == 5) {
                customSet = new StatefulSet(scenario == 5 ? failure : null);
                // Public API accepts any Set<Long>; exact outer classes do not
                // imply callback-free deletion overlays.
                mind.getDeleted().put(UnitType.TVARIABLE, customSet);
            }
        }
        int callbacks() {
            return customArgument != null ? customArgument.calls : customSet != null ? customSet.calls : parent != null ? parent.calls : 0;
        }
    }
    static int run(Fixture fixture, boolean single) throws Exception {
        try {
            List<TVariable> result = single ? singleCheck(fixture.arguments, fixture.mind) : fixture.arguments.getTVariables(fixture.mind);
            for (TVariable variable : result) check(variable == fixture.variable);
            return result.size();
        } catch (RuntimeException actual) {
            check(actual == fixture.failure);
            return -1;
        }
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("duplicate-deletion-").toString());
        String[] names = {"stable-builtins", "argument-second-true", "argument-second-throw", "parent-marks-child", "exact-builtins-set-second-true", "exact-builtins-set-second-throw"};
        int divergent = 0;
        for (int scenario = 0; scenario < names.length; scenario++) {
            Fixture old = new Fixture(scenario), single = new Fixture(scenario);
            int oldResult = run(old, false), singleResult = run(single, true);
            check(singleResult == 1);
            check(oldResult == (scenario == 0 ? 1 : scenario == 2 || scenario == 5 ? -1 : 0));
            if (scenario != 0) {
                divergent++;
                check(old.callbacks() == (scenario == 3 ? 1 : 2));
                check(single.callbacks() == 1);
            }
            if (scenario >= 3) {
                check(old.arguments.get(0).getClass() == Argument.class);
                check(old.variable.getClass() == TVariable.class);
                check(old.mind.getClass() == Mind.class);
            }
            System.out.println("CASE " + names[scenario] + " reference=" + oldResult + " single=" + singleResult
                    + " reference_callbacks=" + old.callbacks() + " single_callbacks=" + single.callbacks());
        }
        System.out.println("DUPLICATE_DELETION_FORENSICS_OK scenarios=6 divergent=" + divergent + " checks=" + checks);
    }
}
