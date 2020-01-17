package org.kanger.exception;

/**
 * Created by Dmitry Kuznetsov on 30.12.2015.
 */
public class LogicalConflictException extends Exception {
    String exceptionMessage = "Logical conflict";
    String error = "";
    Object object = null;

    public LogicalConflictException() {
    }

    public LogicalConflictException(String msg) {
        exceptionMessage += ": " + msg;
    }

    @Override
    public String toString() {
        return exceptionMessage;
    }
}
//