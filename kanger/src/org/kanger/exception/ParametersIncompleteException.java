package org.kanger.exception;

/**
 * Created by Dmitry Kuznetsov on 30.12.2015.
 */
public class ParametersIncompleteException extends Exception {
    String exceptionMessage = "Parameters incomplete";
    String error = "";
    Object object = null;

    public ParametersIncompleteException() {
    }

    public ParametersIncompleteException(String msg) {
        exceptionMessage += ": " + msg;
    }

    @Override
    public String toString() {
        return exceptionMessage;
    }
}
//