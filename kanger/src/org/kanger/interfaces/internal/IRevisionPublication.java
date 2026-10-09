/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.interfaces.internal;

import org.kanger.interfaces.IMind;

/** Optional immutable-revision publication contract; stable providers need not implement it. */
public interface IRevisionPublication {
    /** Validate an exact revision address before the current transaction stack is rebased. */
    default void validateStorageOpen(IMind source, String name) throws Exception { }
    /** Reject incompatible proposed live state before native parent settlement. */
    default void validateCommit(IMind proposed) throws Exception { }
    long getRevision();
    void setNextRevisionDescription(String description) throws Exception;
    IMind publishContext(IMind source, String description) throws Exception;
}
