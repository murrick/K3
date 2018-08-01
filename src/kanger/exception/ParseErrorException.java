package kanger.exception;

import kanger.enums.ParseError;

/**
 * Created by Dmitry Kuznetsov on 30.12.2015.
 */
public class ParseErrorException extends Exception {
    String exceptionMessage = "Parse error";
    ParseError code = ParseError.SUCCESS;

    public ParseErrorException() {
    }

    public ParseErrorException(String msg) {
        exceptionMessage += ": " + msg;
    }

    public ParseErrorException(int pos, ParseError error) {
        code = error;
        if (pos >= 0) {
            exceptionMessage = "" + pos + "@";
        } else {
            exceptionMessage = "";
        }
        switch (error) {
            case SUCCESS:
                exceptionMessage += "Success";
                break;
            case BRACKET:
                exceptionMessage += "Right brackets mismatch";
                break;
            case SUCC:
                exceptionMessage += "Must be ! or ? symbol";
                break;
            case QUOTESL:
                exceptionMessage += "Left quotes mismatch";
                break;
            case QUOTESR:
                exceptionMessage += "Right quotes mismatch";
                break;
            case RBRACES:
                exceptionMessage += "Right braces mismatch";
                break;
            case LBRACES:
                exceptionMessage += "Left braces mismatch";
                break;
            case EOLN:
                exceptionMessage += "Semicolon required";
                break;
            case ANOT:
                exceptionMessage += "Misplaced ~ symbol";
                break;
            case LBRACK:
                exceptionMessage += "Misplaced left bracket";
                break;
            case QUANTOR:
                exceptionMessage += "Misplaced quantor symbol";
                break;
            case INFIX:
                exceptionMessage += "Misplaced infix symbol";
                break;
            case EMPTY:
                exceptionMessage += "Empty term";
                break;
            case AVAR:
                exceptionMessage += "Quantor variable mismatch";
                break;
            case INPRO:
                exceptionMessage += "Symbol inside predicate";
                break;
            case COMMA:
                exceptionMessage += "Misplaced comma";
                break;
            case ATERM:
                exceptionMessage += "Misplaced term";
                break;
            case IPNAME:
                exceptionMessage += "Ivalid predicat name";
                break;
            case FUNC:
                exceptionMessage += "Unexpected using of function";
                break;
            case RANGE:
                exceptionMessage += "Unexpected parametes count";
                break;
            default:
                exceptionMessage += "Unknown error";
        }
    }

    public ParseError getCode() {
        return code;
    }

    @Override
    public String toString() {
        return exceptionMessage;
    }
}
//