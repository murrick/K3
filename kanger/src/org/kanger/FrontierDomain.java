/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Domain;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Immutable operation-local description of one unresolved ordinary query
 * Domain that may be offered to a connected Context.
 *
 * <p>Fixed arguments are detached semantic values. Unbound TVariables preserve
 * query-local names/indexes only; no Context-local variable or Term ID is used
 * as cross-Context identity.</p>
 */
public final class FrontierDomain {

    private final String predicateName;
    private final boolean negated;
    private final String querySource;
    private final String diagnosticSource;
    private final List<ArgumentState> arguments;
    private final List<VariableState> variables;
    private final boolean ground;

    private FrontierDomain(String predicateName,
                           boolean negated,
                           String querySource,
                           String diagnosticSource,
                           List<ArgumentState> arguments,
                           List<VariableState> variables,
                           boolean ground) {
        this.predicateName = predicateName;
        this.negated = negated;
        this.querySource = querySource;
        this.diagnosticSource = diagnosticSource;
        this.arguments = Collections.unmodifiableList(
                new ArrayList<ArgumentState>(arguments));
        this.variables = Collections.unmodifiableList(
                new ArrayList<VariableState>(variables));
        this.ground = ground;
    }

    static FrontierDomain capture(Domain domain, Mind mind)
            throws Exception {
        String predicateName =
                domain.getPredicate(mind).getName(mind);

        List<ArgumentState> arguments =
                new ArrayList<ArgumentState>();
        List<VariableState> variables =
                new ArrayList<VariableState>();
        Set<String> declared =
                new LinkedHashSet<String>();
        boolean ground = true;

        for (IArgument argument : domain.getArguments()) {
            if (argument.getType() == ArgumentType.TVARIABLE) {
                TVariable variable =
                        (TVariable) argument.getObject(mind);
                String name = String.valueOf(
                        variable.getName(mind).getValue());
                TValue current = variable.getCurrent();
                if (current == null
                        || current.getValue(mind) == null) {
                    ground = false;
                    arguments.add(
                            ArgumentState.variable(
                                    name, variable.getIndex()));
                    if (declared.add(name)) {
                        variables.add(
                                new VariableState(
                                        name,
                                        variable.getIndex(),
                                        null));
                    }
                } else if (current.getValue(mind).isCVariable()) {
                    /*
                     * A C-variable is an operation-local existential
                     * descriptor, not a concrete cross-Context value.
                     * Keep this Domain local until ordinary evidence binds it.
                     */
                    return null;
                } else {
                    SemanticTermSnapshot fixed =
                            SemanticTermSnapshot.capture(
                                    current.getValue(mind));
                    arguments.add(
                            ArgumentState.fixed(fixed));
                    if (declared.add(name)) {
                        variables.add(
                                new VariableState(
                                        name,
                                        variable.getIndex(),
                                        current.getValue(mind)
                                                .toString()));
                    }
                }
                continue;
            }

            ITerm value = argument.getValue(mind);
            if (value == null || value.isCVariable()) {
                return null;
            }
            arguments.add(
                    ArgumentState.fixed(
                            SemanticTermSnapshot.capture(value)));
        }

        String querySource =
                renderQuery(
                        predicateName,
                        domain.isAntc(),
                        arguments,
                        variables);
        return new FrontierDomain(
                predicateName,
                domain.isAntc(),
                querySource,
                domain.toString(mind),
                arguments,
                variables,
                ground);
    }

    private static String renderQuery(
            String predicateName,
            boolean negated,
            List<ArgumentState> arguments,
            List<VariableState> variables) {
        StringBuilder source = new StringBuilder();
        source.append('?');
        for (VariableState variable : variables) {
            if (!variable.isBound()) {
                source.append('$')
                        .append(variable.getName())
                        .append(' ');
            }
        }
        if (negated) {
            source.append('~');
        }
        source.append(predicateName).append('(');
        for (int i = 0; i < arguments.size(); ++i) {
            ArgumentState argument = arguments.get(i);
            source.append(argument.isVariable()
                    ? argument.getVariableName()
                    : "?");
            if (i + 1 < arguments.size()) {
                source.append(',');
            }
        }
        source.append(");");
        return source.toString();
    }

    public String getPredicateName() {
        return predicateName;
    }

    public boolean isNegated() {
        return negated;
    }

    /**
     * Query compilation carrier. Fixed values are supplied separately as
     * canonical semantic Terms and therefore never round-trip through text.
     */
    public String getQuerySource() {
        return querySource;
    }

    public String getDiagnosticSource() {
        return diagnosticSource;
    }

    public List<VariableState> getVariables() {
        return variables;
    }

    public boolean isGround() {
        return ground;
    }

    /**
     * Query-local semantic equivalence used only to collapse multiple internal
     * KANGER representations of the same unresolved Domain (for example a
     * generated demand and its originating branch premise).
     */
    public boolean semanticallyEquivalent(
            FrontierDomain other) {
        if (other == null
                || negated != other.negated
                || !predicateName.equals(other.predicateName)
                || !querySource.equals(other.querySource)
                || arguments.size() != other.arguments.size()) {
            return false;
        }
        for (int i = 0; i < arguments.size(); ++i) {
            ArgumentState left = arguments.get(i);
            ArgumentState right = other.arguments.get(i);
            if (left.isVariable() != right.isVariable()) {
                return false;
            }
            if (left.isVariable()) {
                if (!left.getVariableName().equals(
                        right.getVariableName())) {
                    return false;
                }
            } else if (!left.getFixedValue()
                    .semanticallyEquals(
                            right.getFixedValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares this detached frontier with one live Domain in the supplied
     * Mind without using Context-local Rule/Domain/Term IDs as identity.
     */
    public boolean semanticallyMatches(
            Domain domain, Mind mind) throws Exception {
        FrontierDomain other =
                capture(domain, mind);
        return other != null
                && semanticallyEquivalent(other);
    }

    /**
     * Projects fixed X arguments into the target Context in query-template
     * placeholder order.
     */
    public Queue<ITerm> projectFixedArguments(
            Mind target) throws Exception {
        Queue<ITerm> result = new LinkedList<ITerm>();
        for (ArgumentState argument : arguments) {
            if (!argument.isVariable()) {
                result.add(
                        target.getTerms().projectSemantic(
                                argument.getFixedValue()
                                        .materialize()));
            }
        }
        return result;
    }

    /**
     * Builds a fully grounded ephemeral assertion template. Every argument is
     * carried as a canonical external Term rather than rendered text.
     */
    public String getEvidenceSource(boolean truth) {
        boolean evidenceNegated =
                truth ? negated : !negated;
        StringBuilder source = new StringBuilder();
        source.append('!');
        if (evidenceNegated) {
            source.append('~');
        }
        source.append(predicateName).append('(');
        for (int i = 0; i < arguments.size(); ++i) {
            source.append('?');
            if (i + 1 < arguments.size()) {
                source.append(',');
            }
        }
        source.append(");");
        return source.toString();
    }

    /**
     * Resolves one lifted tuple into assertion-argument order in X.
     */
    public Queue<ITerm> evidenceArguments(
            Mind target,
            List<String> variableOrder,
            List<ITerm> tuple) throws Exception {
        if (variableOrder.size() != tuple.size()) {
            throw new IllegalArgumentException(
                    "Frontier tuple arity does not match variable order");
        }

        java.util.Map<String, ITerm> values =
                new java.util.LinkedHashMap<String, ITerm>();
        for (int i = 0; i < variableOrder.size(); ++i) {
            values.put(variableOrder.get(i), tuple.get(i));
        }

        Queue<ITerm> result = new LinkedList<ITerm>();
        for (ArgumentState argument : arguments) {
            if (argument.isVariable()) {
                ITerm value = values.get(
                        argument.getVariableName());
                if (value == null) {
                    throw new IllegalArgumentException(
                            "No lifted value for frontier variable "
                                    + argument.getVariableName());
                }
                result.add(value);
            } else {
                result.add(
                        target.getTerms().projectSemantic(
                                argument.getFixedValue()
                                        .materialize()));
            }
        }
        return result;
    }

    private static final class ArgumentState {

        private final String variableName;
        private final int variableIndex;
        private final SemanticTermSnapshot fixedValue;

        private ArgumentState(String variableName,
                              int variableIndex,
                              SemanticTermSnapshot fixedValue) {
            this.variableName = variableName;
            this.variableIndex = variableIndex;
            this.fixedValue = fixedValue;
        }

        static ArgumentState variable(
                String name, int index) {
            return new ArgumentState(
                    name, index, null);
        }

        static ArgumentState fixed(
                SemanticTermSnapshot value) {
            return new ArgumentState(
                    null, -1, value);
        }

        boolean isVariable() {
            return variableName != null;
        }

        String getVariableName() {
            return variableName;
        }

        int getVariableIndex() {
            return variableIndex;
        }

        SemanticTermSnapshot getFixedValue() {
            return fixedValue;
        }
    }

    public static final class VariableState {

        private final String name;
        private final int index;
        private final String boundValue;

        private VariableState(String name,
                              int index,
                              String boundValue) {
            this.name = name;
            this.index = index;
            this.boundValue = boundValue;
        }

        public String getName() {
            return name;
        }

        public int getIndex() {
            return index;
        }

        public boolean isBound() {
            return boundValue != null;
        }

        public String getBoundValue() {
            return boundValue;
        }
    }
}
