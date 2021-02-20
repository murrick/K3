package org.kanger.interfaces;

import org.kanger.primitives.ArgumentsList;
import org.kanger.primitives.Hypothesis;

public interface IHypothesis extends Comparable<Hypothesis> {
    IPredicate getPredicate() throws Exception;

    ArgumentsList getArguments();

    boolean isAntc();

    boolean isQuery();

}
