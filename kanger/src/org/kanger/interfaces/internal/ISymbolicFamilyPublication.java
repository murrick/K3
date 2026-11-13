/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.interfaces.internal;
import org.kanger.interfaces.IMind;

/** Optional explicit symbolic-family publication, called under the owning Mind's settled-root lock. */
public interface ISymbolicFamilyPublication {
    long saveCheckedSymbolicConnections(IMind source)throws Exception;
    boolean writeCheckedSymbolicCandidate(IMind source,String statement,boolean logging)throws Exception;
}
