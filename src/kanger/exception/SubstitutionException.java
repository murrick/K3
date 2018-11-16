package kanger.exception;

/**
 * Created by Dmitry Kuznetsov on 30.12.2015.
 */
public class SubstitutionException extends Exception {
    String exceptionMessage = "ERROR";
    String error = "";
    Object object = null;

    public SubstitutionException() {
    }

    public SubstitutionException(String msg) {
        exceptionMessage += ": " + msg;
    }

    @Override
    public String toString() {
        return exceptionMessage;
    }
}
//