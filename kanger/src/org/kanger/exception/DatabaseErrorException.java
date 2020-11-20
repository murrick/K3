package org.kanger.exception;

/**
 * Created by Dmitry Kuznetsov on 30.12.2015.
 */
public class DatabaseErrorException extends Exception {
    String exceptionMessage = "Database error";
    String error = "";
    Object object = null;

    public DatabaseErrorException() {
    }

    public DatabaseErrorException(String msg) {
        exceptionMessage = msg;
    }

    @Override
    public String toString() {
        return exceptionMessage;
    }
}
//