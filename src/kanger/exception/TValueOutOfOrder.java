package kanger.exception;

/**
 * Created by Dmitry Kuznetsov on 30.12.2015.
 */
public class TValueOutOfOrder extends Exception {
    String exceptionMessage = "ERROR";
    String error = "";
    Object object = null;

    public TValueOutOfOrder() {
    }

    public TValueOutOfOrder(String msg) {
        exceptionMessage += ": " + msg;
    }

    @Override
    public String toString() {
        return exceptionMessage;
    }
}
//