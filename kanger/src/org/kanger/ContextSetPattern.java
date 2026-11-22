/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.kanger.enums.ArgumentType;
import org.kanger.enums.DataType;
import org.kanger.enums.FunctionBinding;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Function;
import org.kanger.units.Rule;
import org.kanger.units.Term;
import org.kanger.units.TVariable;

/** A flat native set template with variable positions relative to the owning rule. */
final class ContextSetPattern {
    private static final class Member {
        final int variable;
        final SemanticTermSnapshot constant;

        Member(int variable, SemanticTermSnapshot constant) {
            this.variable = variable;
            this.constant = constant;
        }
    }

    private final List<Member> members;

    private ContextSetPattern(List<Member> members) {
        this.members = Collections.unmodifiableList(members);
    }

    static boolean isConstructor(Function function, Mind mind) throws Exception {
        return function.getBinding() == FunctionBinding.INFRASTRUCTURE
                && "_set".equals(function.getName(mind).getValue());
    }

    static ContextSetPattern capture(Function function, Rule owner, Mind mind, Runnable work)
            throws Exception {
        if (!isConstructor(function, mind)) throw new IllegalArgumentException("Native set constructor required");
        int arity = function.getRange();
        if (arity < 1 || function.getArguments().size() != arity + 1)
            throw new IllegalStateException("Invalid native set constructor arguments");
        List<Member> members = new ArrayList<>();
        // The final function argument is its result slot, outside the set members.
        for (int i = 0; i < arity; i++) {
            work.run();
            IArgument argument = function.getArguments().get(i);
            if (argument.getType() == ArgumentType.TVARIABLE) {
                members.add(new Member(variablePosition(argument, owner, mind), null));
            } else if (argument.getType() == ArgumentType.TERM
                    && SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind))) {
                members.add(new Member(-1, SemanticTermSnapshot.capture(argument.getValue(mind))));
            } else {
                throw new IllegalStateException("Native unordered-set patterns require structured term qualification");
            }
        }
        return new ContextSetPattern(members);
    }

    void collectVariables(java.util.Set<Integer> variables, Runnable work) {
        for (Member member : members) {
            work.run();
            if (member.variable >= 0) variables.add(member.variable);
        }
    }

    static int variablePosition(IArgument argument, Rule owner, Mind mind) throws Exception {
        TVariable variable = (TVariable) argument.getObject(mind);
        int index = variable.getIndex() - owner.getVarIndex();
        if (variable.getRuleId() != owner.getId() || index < 0)
            throw new IllegalStateException("Variable does not belong to the rule");
        return index;
    }

    static SemanticTermSnapshot requireBinding(int index, Map<Integer, SemanticTermSnapshot> assignment) {
        SemanticTermSnapshot value = assignment.get(index);
        if (value == null) throw new IllegalArgumentException("Missing binding for rule variable " + index);
        return value;
    }

    SemanticTermSnapshot instantiate(Map<Integer, SemanticTermSnapshot> assignment, Runnable work)
            throws Exception {
        List<ITerm> values = new ArrayList<>();
        for (Member member : members) {
            work.run();
            SemanticTermSnapshot value = member.variable < 0
                    ? member.constant : requireBinding(member.variable, assignment);
            value.appendFlatSetMembers(values, work);
        }
        Term set = new Term();
        set.setPersistentState(DataType.SET, values, 0, 0, -1L, -1L, false);
        return SemanticTermSnapshot.capture(set);
    }
}
