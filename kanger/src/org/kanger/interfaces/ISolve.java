package org.kanger.interfaces;

import org.kanger.primitives.ArgumentsList;

public interface ISolve {
    IPredicate getPredicate(IMind mind) throws Exception;

    ArgumentsList getArguments();

    boolean isAntc();

    int getRange();

    String toString(IMind mind);

    String toString();

}
