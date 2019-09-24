package kanger.interfaces;

import kanger.exception.RuntimeErrorException;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.15.
 */
public interface Reactor {

    Object run(Object o) throws RuntimeErrorException;
}
