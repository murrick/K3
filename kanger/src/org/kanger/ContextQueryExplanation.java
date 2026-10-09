package org.kanger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IContextFederation;
import org.kanger.units.Rule;

/** Detaches the completed query's truth, bindings and solutions for Context explain. */
final class ContextQueryExplanation {
    private ContextQueryExplanation() {
    }

    static IContextFederation.ExplainResult capture(Mind mind,
            IContextFederation federation, Boolean answer,
            List<IContextFederation.ExplainPass> passes) throws Exception {
        IContextFederation.FrontierTruth finalTruth =
                explainTruth(answer, passes);
        IContextFederation.FrontierTruth localTruth =
                passes.isEmpty()
                        ? finalTruth
                        : IContextFederation.FrontierTruth.UNKNOWN;

        ArrayList<IContextFederation.ValueRow> values =
                new ArrayList<IContextFederation.ValueRow>();
        for (Map<String, ITerm> row : mind.getValues()) {
            LinkedHashMap<String, String> bindings =
                    new LinkedHashMap<String, String>();
            for (Map.Entry<String, ITerm> binding
                    : row.entrySet()) {
                ITerm value = binding.getValue();
                bindings.put(
                        binding.getKey(),
                        value == null
                                ? ""
                                : value.toString());
            }
            values.add(
                    new IContextFederation.ValueRow(
                            bindings));
        }

        ArrayList<String> solutions =
                new ArrayList<String>();
        for (IRule solution : mind.getSolutions()) {
            solutions.add(
                    ((Rule) solution).toString(mind));
        }

        return new IContextFederation.ExplainResult(
                federation.federationSnapshot(),
                localTruth,
                finalTruth,
                passes,
                values,
                solutions);
    }

    private static IContextFederation.FrontierTruth explainTruth(
            Boolean answer,
            List<IContextFederation.ExplainPass> passes) {
        if (answer != null) {
            return answer.booleanValue()
                    ? IContextFederation.FrontierTruth.TRUE
                    : IContextFederation.FrontierTruth.FALSE;
        }
        for (IContextFederation.ExplainPass pass : passes) {
            for (IContextFederation.FrontierObservation observation
                    : pass.getContinuation().getObservations()) {
                if (observation.getTruth()
                        == IContextFederation.FrontierTruth.CONFLICT) {
                    return IContextFederation.FrontierTruth.CONFLICT;
                }
            }
        }
        return IContextFederation.FrontierTruth.UNKNOWN;
    }

}
