package org.kanger.exception;

/**
 * Created by Dmitry Kuznetsov on 30.12.2015.
 */
public class AuthenticationErrorException extends Exception {
    String exceptionMessage = "Authentication error";

    public AuthenticationErrorException() {
    }

    public AuthenticationErrorException(String msg) {
        exceptionMessage += ": " + msg;
    }

    @Override
    public String toString() {
        return exceptionMessage;
    }
}
//