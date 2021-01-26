package org.kanger.exception;

import org.kanger.units.SysOp;

/**
 * Created by Dmitry Kuznetsov on 30.12.2015.
 */
public class RuntimeErrorException extends Exception {
    String exceptionMessage = "Runtime error";
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