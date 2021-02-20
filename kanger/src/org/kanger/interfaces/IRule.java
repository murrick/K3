package org.kanger.interfaces;

import org.kanger.primitives.ArgumentsList;

import java.util.Set;

public interface IRule {
    Set<ICause> getCauses();

    boolean isGenerated();

    boolean isStored();

    long getId();

    ITerm getOrigin() throws Exception;

    boolean isQuery();

    boolean isRestored(IMind mind);

    boolean isDeleted(IMind mind);

    boolean isSubstitutable();

    boolean isAbstractive();

    long getMindId();

    boolean isAntc() throws Exception;

    IPredicate getPredicate(IMind mind) throws Exception;

    long getPredicateId() throws Exception;

    ArgumentsList getArguments() throws Exception;

    IMind getMind();

    IComment getComment() throws Exception;

    void setComment(String term) throws Exception;
}
