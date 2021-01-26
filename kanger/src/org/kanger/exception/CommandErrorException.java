package org.kanger.exception;

/**
 * Created by Dmitry Kuznetsov on 30.12.2015.
 */
public class CommandErrorException extends Exception {
    String exceptionMessage = "Command syntax error";
    String error = "";
    Object object = null;

    public CommandErrorException() {
    }

    public CommandErrorException(String msg) {
        exceptionMessage += ": " + msg;
    }

    @Override
    public String toString() {
        return exceptionMessage;
    }
}
//