package kanger.exception;

import kanger.compiler.SysOp;

/**
 * Created by Dmitry Kuznetsov on 30.12.2015.
 */
public class RuntimeErrorException extends Exception {
    String exceptionMessage = "ERROR";
    String error = "";
    Object object = null;

    public RuntimeErrorException() {
    }

    public RuntimeErrorException(String msg) {
        exceptionMessage += ": " + msg;
    }

    public RuntimeErrorException(Object object, String msg) {
        this.object = object;
        this.error = msg;
        if (object instanceof SysOp) {
            exceptionMessage += " in =" + object.toString() + ": " + msg;
        } else {
            exceptionMessage += ": " + msg;
        }
    }

    @Override
    public String toString() {
        return exceptionMessage;
    }
}
//