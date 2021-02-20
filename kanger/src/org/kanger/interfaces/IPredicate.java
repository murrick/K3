package org.kanger.interfaces;

import java.util.Set;

public interface IPredicate {

    ITerm getName() throws Exception;

    int getRange();

    long getId();

    boolean isDeleted(IMind mind);

    boolean isEmpty() throws Exception;

    long getMindId();

    Set<IRule> getSolves() throws Exception;

}
