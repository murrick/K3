/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.ArgumentType;
import org.kanger.enums.Enums;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.ITerm;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.Domain;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable operation-local description of one unresolved ordinary query
 * Domain that may be offered to a connected Context.
 *
 * <p>This is an orchestration surface, not cross-Context identity. The
 * diagnostic source preserves the currently rendered Domain while variable
 * states preserve the query-local positional order and bindings observed at
 * the Analyzer boundary.</p>
 */
public final class FrontierDomain {

    private final String predicateName;
    private final String querySource;
    private final String diagnosticSource;
    private final List<VariableState> variables;
    private final boolean ground;

    private FrontierDomain(String predicateName,
                           String querySource,
                           String diagnosticSource,
                           List<VariableState> variables,
                           boolean ground) {
        this.predicateName = predicateName;
        this.querySource = querySource;
        this.diagnosticSource = diagnosticSource;
        this.variables = Collections.unmodifiableList(
                new ArrayList<VariableState>(variables));
        this.ground = ground;
    }

    static FrontierDomain capture(Domain domain, Mind mind)
            throws Exception {
        String predicateName =
                domain.getPredicate(mind)
                        .getName(mind);

        List<VariableState> variables =
                new ArrayList<VariableState>();
        boolean ground = true;
        for (TVariable variable
                : domain.getArguments().getTVariables(mind)) {
            TValue current = variable.getCurrent();
            String boundValue = null;
            if (current == null) {
                ground = false;
            } else if (current.getValue(mind) != null) {
                boundValue =
                        current.getValue(mind).toString();
            }
            variables.add(new VariableState(
                    String.valueOf(
                            variable.getName(mind).getValue()),
                    variable.getIndex(),
                    boundValue));
        }

        return new FrontierDomain(
                predicateName,
                renderQuerySource(domain, mind),
                domain.toString(mind),
                variables,
                ground);
    }

    public String getPredicateName() {
        return predicateName;
    }

    /**
     * Minimal operation-local query text accepted by a foreign Context.
     *
     * <p>This is a compilation carrier only. It is not Context identity,
     * persistence data or a semantic deduplication key.</p>
     */
    public String getQuerySource() {
        return querySource;
    }

    /**
     * Current Context diagnostic rendering. This is deliberately not a
     * persistence format or cross-Context identity.
     */
    public String getDiagnosticSource() {
        return diagnosticSource;
    }

    /**
     * Unique query variables in their Domain traversal order.
     */
    public List<VariableState> getVariables() {
        return variables;
    }

    /**
     * True when the frontier has no currently unbound query variables. A
     * predicate such as {@code ?male(Tom)} is therefore a valid ground frontier.
     */
    public boolean isGround() {
        return ground;
    }

    private static String renderQuerySource(
            Domain domain, Mind mind) throws Exception {
        int previousDebug = mind.getDebugLevel();
        try {
            /*
             * Source rendering must not inherit diagnostic TValue/status
             * decorations from the active Mind.
             */
            mind.setDebugLevel(Enums.DEBUG_LEVEL_QUIET);

            ArgumentsList rendered = new ArgumentsList();
            Set<String> declarations = new LinkedHashSet<String>();

            for (IArgument argument : domain.getArguments()) {
                if (argument.getType() == ArgumentType.TVARIABLE) {
                    TVariable variable =
                            (TVariable) argument.getObject(mind);
                    TValue current = variable.getCurrent();
                    if (current != null
                            && current.getValue(mind) != null) {
                        rendered.add(new Argument(
                                current.getValue(mind)));
                    } else {
                        rendered.add(argument);
                    }
                } else if (argument.getType() == ArgumentType.TVALUE) {
                    TValue value =
                            (TValue) argument.getObject(mind);
                    ITerm term = value.getValue(mind);
                    rendered.add(new Argument(term));
                } else {
                    rendered.add(argument);
                }
            }

            for (TVariable variable
                    : domain.getArguments().getTVariables(mind)) {
                if (variable.getCurrent() == null) {
                    declarations.add(String.valueOf(
                            variable.getName(mind).getValue()));
                }
            }

            String body =
                    domain.toString(mind, rendered, false);
            if (body == null || body.isEmpty()
                    || declarations.isEmpty()) {
                return body;
            }

            StringBuilder source = new StringBuilder();
            source.append(body.charAt(0));
            for (String variable : declarations) {
                source.append('

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
).append(variable).append(' ');
            }
            source.append(body.substring(1));
            return source.toString();
        } finally {
            mind.setDebugLevel(previousDebug);
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
