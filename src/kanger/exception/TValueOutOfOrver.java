package kanger.exception;

import kanger.compiler.SysOp;

/**
 * Created by Dmitry Kuznetsov on 30.12.2015.
 */
public class TValueOutOfOrver extends Exception {
    String exceptionMessage = "ERROR";
    String error = "";
    Object object = null;

    public TValueOutOfOrver() {
    }

    public TValueOutOfOrver(String msg) {
        exceptionMessage += ": " + msg;
    }

    @Override
    public String toString() {
        return exceptionMessage;
    }
}
//