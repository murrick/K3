/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger.exception;

import org.kanger.enums.ParseError;

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
            case COMMENT:
                exceptionMessage += "Unclosed comments";
                break;
            case EPARAM:
                exceptionMessage += "External parameter expected";
                break;
            case ENEG:
                exceptionMessage += "Misplaced unary minus";
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