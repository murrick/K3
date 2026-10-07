/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.interfaces.internal;

import org.kanger.interfaces.IMind;

/** Optional immutable-revision publication contract; stable providers need not implement it. */
public interface IRevisionPublication {
    long getRevision();
    void setNextRevisionDescription(String description) throws Exception;
    IMind publishContext(IMind source, String description) throws Exception;
}
