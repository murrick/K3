package kanger.interfaces;

import kanger.exception.RuntimeErrorException;
import kanger.primitives.Argument;

import java.util.List;

/**
 * Created by murray on 27.05.15.
 */
public interface IRunnable {
    Object run(Object o) throws RuntimeErrorException;
}
