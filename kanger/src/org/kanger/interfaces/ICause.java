package org.kanger.interfaces;

public interface ICause {

    ISolve getDonor();

    IRule getDonor(IMind mind) throws Exception;

    IRule getRule(IMind mind) throws Exception;

    long getRuleId();

}
