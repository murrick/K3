/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/** Shared whole-rule/source-cut validation before structural shapes become evidence. */
final class ContextWitnessDeclarationCut {
    static List<Input> capture(Packet input,Cut cut,List<Declaration> declarations,Runnable work,
            int maximumWork,int maximumEvents) throws Exception {
        return capture(input,cut,declarations,work,maximumWork,maximumEvents,null);
    }
    static List<Input> capture(Packet input,Cut cut,List<Declaration> declarations,Runnable work,
            int maximumWork,int maximumEvents,ContextWitnessEmptyTarget emptyTarget) throws Exception {
        if (emptyTarget != null) emptyTarget.validate(cut,work);
        // Validate the entire supplied declaration snapshot before using shapes.
        ContextWitnessResultTransport.family(input,Collections.singletonList(cut),maximumWork);
        Cut parent = null;
        for (Item item : input.items) for (Support proof : item.proofs) for (Source source : proof.sources) {
            work.run(); if (source.localX) parent = Cut.of(source);
        }
        if (declarations.isEmpty() && emptyTarget == null || declarations.size() > 64) throw new IllegalStateException("Bounded nonempty declaration cut required");
        List<Declaration> snapshot = new ArrayList<>(declarations); Set<Source> seen = new HashSet<>();
        boolean target = emptyTarget != null;
        User user = new User(); Mind mind = new Mind(user); user.setCurrentMind(mind);
        List<Input> inputs;
        try (ContextProductionCapture capture = ContextProductionCapture.begin(mind,maximumEvents)) {
            for (Declaration declaration : snapshot) {
                work.run(); Source source = declaration.source; Cut actual = Cut.of(source);
                if (emptyTarget != null && (cut.localX || !source.localX))
                    throw new IllegalStateException("Declaration contradicts empty target proof");
                if (!seen.add(source)) throw new IllegalStateException("Duplicate declaration source occurrence");
                if (!(source.localX ? actual.equals(parent) : !cut.localX && actual.equals(cut)))
                    throw new IllegalStateException("Declaration producer outside expected cut");
                target |= actual.equals(cut); capture.replay(mind,source,declaration.statement);
            }
            Snapshot captured = capture.snapshot();
            if (!target || captured.inputs.size() != snapshot.size() || !captured.gaps.isEmpty())
                throw new IllegalStateException("Incomplete declaration capture");
            inputs = captured.inputs;
            for (int i=0;i<inputs.size();++i) {
                work.run(); if (!inputs.get(i).source.equals(snapshot.get(i).source))
                    throw new IllegalStateException("Declaration source binding mismatch");
            }
            // Shape metadata is evidence only after checking the entire original sourced Rule.
            // TERM witnesses/functions cannot be promoted to universal variables.
            for (Input declaration : inputs) {
                work.run(); Rule rule = (Rule)mind.getRules().get(declaration.nativeRule);
                if (rule.isQuery() || rule.isGenerated() || declaration.branches < 1 || declaration.branches > 8
                        || declaration.shapes.size() != declaration.branches || declaration.variableCount > 2)
                    throw new IllegalStateException("Outside universal declaration clause scope");
                for (List<Domain> branch : rule.getTree()) for (Domain domain : branch) for (IArgument argument : domain.getArguments()) {
                    work.run();
                    if (argument.getType() != ArgumentType.TVARIABLE && (argument.getType() != ArgumentType.TERM
                            || !SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind))))
                        throw new IllegalStateException("Non-universal declaration argument");
                }
                for (Clause clause : declaration.shapes) {
                    work.run(); if (clause.literals.size() > 8) throw new IllegalStateException("Declaration clause size limit");
                }
            }
        }
        return inputs;
    }
}
