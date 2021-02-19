package org.kanger.interfaces;

import org.kanger.enums.LibMode;

import java.util.List;

public interface IOperation {
    long getId();

    String getName();

    int getRange();

    List<String> getScripts();

    List<String> getParams();

    boolean isDeleted(IMind mind);

    LibMode getMode();

    String asString();

    long getMindId();

    String toString();

}
