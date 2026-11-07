/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.DataType;
import org.kanger.enums.FunctionBinding;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.ITerm;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.Function;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;

/** Detached numeric expression; variable indexes are relative to the owning rule. */
final class ContextArithmeticExpression {
    private static final Set<String> OPERATIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("_add", "_sub", "_mul", "_div", "_rem")));

    private static final class Node {
        final SemanticTermSnapshot value;
        final int variable;
        final String operation;
        final int left, right;

        Node(SemanticTermSnapshot value, int variable, String operation, int left, int right) {
            this.value = value;
            this.variable = variable;
            this.operation = operation;
            this.left = left;
            this.right = right;
        }
    }

    private static final class Frame {
        final IArgument argument;
        Function function;
        String operation;
        int left = -1, right = -1, next;

        Frame(IArgument argument) { this.argument = argument; }
    }

    final Set<Integer> variables;
    private final List<Node> nodes;

    private ContextArithmeticExpression(List<Node> nodes, Set<Integer> variables) {
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.variables = Collections.unmodifiableSet(new LinkedHashSet<>(variables));
    }

    static ContextArithmeticExpression capture(IArgument root, Rule owner, Mind mind, Runnable work) throws Exception {
        List<Node> nodes = new ArrayList<>();
        Set<Integer> variables = new LinkedHashSet<>();
        Deque<Frame> pending = new ArrayDeque<>();
        pending.push(new Frame(root));
        while (!pending.isEmpty()) {
            work.run();
            Frame frame = pending.peek();
            Node node;
            if (frame.argument.getType() == ArgumentType.FUNCTION) {
                if (frame.function == null) {
                    frame.function = (Function) frame.argument.getObject(mind);
                    frame.operation = frame.function.getName(mind).getValue().toString();
                    if (frame.function.getBinding() != FunctionBinding.INFRASTRUCTURE
                            || frame.function.getRange() != 2 || !OPERATIONS.contains(frame.operation)) {
                        throw new IllegalStateException("Binary numeric infrastructure expression required");
                    }
                }
                if (frame.next < 2) {
                    pending.push(new Frame(frame.function.getArguments().get(frame.next++)));
                    continue;
                }
                node = new Node(null, -1, frame.operation, frame.left, frame.right);
            } else if (frame.argument.getType() == ArgumentType.TVARIABLE) {
                TVariable variable = (TVariable) frame.argument.getObject(mind);
                if (variable.getRuleId() != owner.getId()) throw new IllegalStateException("Expression variable belongs to another rule");
                int index = variable.getIndex() - owner.getVarIndex();
                if (index < 0) throw new IllegalStateException("Invalid rule-relative expression variable");
                variables.add(index);
                node = new Node(null, index, null, -1, -1);
            } else {
                ITerm value = frame.argument.getValue(mind);
                if (frame.argument.getType() != ArgumentType.TERM || value == null || value.getType() != DataType.NUMERIC) {
                    throw new IllegalStateException("Numeric expression input required");
                }
                node = new Node(SemanticTermSnapshot.capture(value), -1, null, -1, -1);
            }
            int index = nodes.size();
            nodes.add(node);
            pending.pop();
            if (!pending.isEmpty()) {
                Frame parent = pending.peek();
                if (parent.next == 1) parent.left = index;
                else parent.right = index;
            }
        }
        return new ContextArithmeticExpression(nodes, variables);
    }

    SemanticTermSnapshot evaluate(Map<Integer, SemanticTermSnapshot> assignment, Runnable work) throws Exception {
        if (!variables.equals(assignment.keySet())) throw new IllegalArgumentException("Exact expression assignment required");
        User user = new User();
        Mind scratch = new Mind(user);
        user.setCurrentMind(scratch);
        List<ITerm> values = new ArrayList<>();
        for (Node node : nodes) {
            work.run();
            if (node.operation == null) {
                SemanticTermSnapshot snapshot = node.variable < 0 ? node.value : assignment.get(node.variable);
                ITerm value = scratch.getTerms().projectSemantic(snapshot.materialize());
                if (value.getType() != DataType.NUMERIC) throw new IllegalArgumentException("Numeric expression assignment required");
                values.add(value);
            } else {
                ArgumentsList arguments = new ArgumentsList();
                arguments.add(new Argument(values.get(node.left)));
                arguments.add(new Argument(values.get(node.right)));
                Function function = scratch.getFunctions().add(scratch.getTerms().add(node.operation),
                        arguments, FunctionBinding.INFRASTRUCTURE);
                scratch.getCalculator().calculate(function, false);
                ITerm value = function.getValue(scratch);
                if (value == null || value.getType() != DataType.NUMERIC) throw new IllegalStateException("Arithmetic result unavailable");
                values.add(value);
            }
        }
        return SemanticTermSnapshot.capture(values.get(values.size() - 1));
    }
}
