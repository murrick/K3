package org.kanger.exception;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public class OutOfBufferException extends Exception {

    String exceptionMessage = "Out of buffer";

    public OutOfBufferException() {
    }

    public OutOfBufferException(String msg) {
        exceptionMessage += ": " + msg;
    }

    @Override
    public String toString() {
        return exceptionMessage;
    }

}
