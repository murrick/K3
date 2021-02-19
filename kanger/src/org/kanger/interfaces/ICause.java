package org.kanger.interfaces;

public interface ICause {
    ISolve getDonor();

    IRule getRule(IMind mind) throws Exception;

    long getRuleId();

    String toString();

}
