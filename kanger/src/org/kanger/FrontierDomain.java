/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.units.Domain;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final String diagnosticSource;
    private final List<VariableState> variables;
    private final boolean ground;

    private FrontierDomain(String predicateName,
                           String diagnosticSource,
                           List<VariableState> variables,
                           boolean ground) {
        this.predicateName = predicateName;
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
                domain.toString(mind),
                variables,
                ground);
    }

    public String getPredicateName() {
        return predicateName;
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
