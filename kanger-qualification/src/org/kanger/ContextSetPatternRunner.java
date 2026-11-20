/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Function;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;

/** Verify shared variable identities and native set equality after substitution. */
public final class ContextSetPatternRunner {
    public static void main(String[] args) throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        if (!mind.compile("!@x @y a(x,y)->b([x,y]);")) throw new AssertionError("Fixture");
        for (IRule item : mind.getRules()) {
            if (item.isGenerated()) continue;
            check((Rule) item, mind);
            System.out.println("CONTEXT_SET_PATTERN_PASS");
            return;
        }
        throw new AssertionError("Missing native rule");
    }

    private static void check(Rule rule, Mind mind) throws Exception {
        ContextSetPattern pattern = null;
        int[] positions = new int[2];
        boolean body = false;
        for (List<Domain> branch : rule.getTree()) {
            for (Domain domain : branch) {
                if ("a".equals(domain.getPredicate(mind).getName(mind))) {
                    for (int i = 0; i < positions.length; i++) {
                        IArgument argument = domain.getArguments().get(i);
                        positions[i] = ((TVariable) argument.getObject(mind)).getIndex() - rule.getVarIndex();
                    }
                    body = true;
                }
                for (IArgument argument : domain.getArguments()) {
                    if (argument.getType() != ArgumentType.FUNCTION) continue;
                    Function function = (Function) argument.getObject(mind);
                    if (ContextSetPattern.isConstructor(function, mind))
                        pattern = ContextSetPattern.capture(function, rule, mind, () -> {});
                }
            }
        }
        if (pattern == null || !body || positions[0] == positions[1]) throw new AssertionError("Missing shared scope");
        Map<Integer, SemanticTermSnapshot> values = new HashMap<>();
        SemanticTermSnapshot john = SemanticTermSnapshot.capture(mind.getTerms().add("John"));
        SemanticTermSnapshot mary = SemanticTermSnapshot.capture(mind.getTerms().add("Mary"));
        values.put(positions[0], john);
        values.put(positions[1], mary);
        SemanticTermSnapshot first = pattern.instantiate(values, () -> {});
        SemanticTermSnapshot expected = SemanticTermSnapshot.capture(mind.getTerms().add(new Object[]{"John", "Mary"}));
        if (!first.semanticallyEquals(expected)) throw new AssertionError("Native set changed after substitution");
        values.put(positions[0], mary);
        values.put(positions[1], john);
        if (!first.semanticallyEquals(pattern.instantiate(values, () -> {})))
            throw new AssertionError("Permutation changed set equality");
        values.remove(positions[1]);
        try {
            pattern.instantiate(values, () -> {});
            throw new AssertionError("Missing variable was accepted");
        } catch (IllegalArgumentException expectedFailure) {
            // A set constructor cannot guess a missing assignment.
        }
    }
}
