package org.kanger.interfaces.internal;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.15.
 */
public interface IReactor<T> {

    Object run(T o) throws Exception;
}
