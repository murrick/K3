package org.kanger.exception;

/**
 * @author Dmitry Kuznetsov
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
