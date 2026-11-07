/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.Term;

/** Detached arithmetic dependency and bounded native evaluation regression. */
public final class ContextArithmeticExpressionRunner {
    private static int checks;

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("arithmetic-expression-").toString());
        verify("!@x fun(x)->next(x+1);", new double[]{1}, 2);
        verify("!@x fun(x)->next(x+x);", new double[]{3}, 6);
        verify("!@x @y fun(x,y)->next((x+1)*(y-2));", new double[]{1, 4}, 4);
        System.out.println("CONTEXT_ARITHMETIC_EXPRESSION_PASS checks=" + checks);
        System.exit(0);
    }

    private static void verify(String source, double[] inputs, double expected) throws Exception {
        User user = new User();
        Mind mind = new Mind(user);
        user.setCurrentMind(mind);
        require(mind.compile(source, null, false), "native expression fixture");
        Rule rule = null;
        IArgument argument = null;
        for (IRule candidate : mind.getRules()) if (!candidate.isGenerated()) {
            rule = (Rule) candidate;
            for (List<Domain> branch : rule.getTree()) for (Domain domain : branch)
                for (IArgument value : domain.getArguments()) if (value.getType() == ArgumentType.FUNCTION) argument = value;
        }
        ContextArithmeticExpression expression = ContextArithmeticExpression.capture(argument, rule, mind, () -> {});
        require(expression.variables.size() == inputs.length, "distinct ordered variable dependencies");
        Map<Integer, SemanticTermSnapshot> assignment = new LinkedHashMap<>();
        int index = 0;
        for (int variable : expression.variables) assignment.put(variable, number(inputs[index++]));
        require(expression.evaluate(assignment, () -> {}).semanticallyEquals(number(expected)), "native arithmetic result");
        int[] remaining = {1};
        boolean stopped = false;
        try {
            expression.evaluate(assignment, () -> {
                if (remaining[0]-- == 0) throw new IllegalStateException("expression allowance");
            });
        } catch (IllegalStateException exhausted) { stopped = true; }
        require(stopped, "evaluation propagates exhausted work");
        boolean missing = false;
        try { expression.evaluate(Collections.emptyMap(), () -> {}); }
        catch (IllegalArgumentException rejected) { missing = true; }
        require(missing, "missing variable assignment is refused");
        require(expression.evaluate(assignment, () -> {}).semanticallyEquals(number(expected)), "retry is detached from partial evaluation");
    }

    private static SemanticTermSnapshot number(double value) throws Exception {
        return SemanticTermSnapshot.capture(new Term(value, null));
    }

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
