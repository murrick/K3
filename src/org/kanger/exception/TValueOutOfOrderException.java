package org.kanger.exception;

/**
 * Created by Dmitry Kuznetsov on 30.12.2015.
 */
public class TValueOutOfOrderException extends Exception {
    String exceptionMessage = "ERROR";
    String error = "";
    Object object = null;

    public TValueOutOfOrderException() {
    }

    public TValueOutOfOrderException(String msg) {
        exceptionMessage += ": " + msg;
    }

    @Override
    public String toString() {
        return exceptionMessage;
    }
}
//