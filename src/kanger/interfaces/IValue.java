package kanger.interfaces;


import kanger.units.*;

/**
 * Created by murray on 02.12.16.
 */
public interface IValue<T> {

    Term getValue();

    T setValue(Term term);

    boolean isEmpty();

    void clear();

    boolean isTVariable();

    boolean isFunction();

    boolean isTValue();
   
    boolean isTerm();

    boolean isFValue();

    boolean isCVariable();

    boolean isDefined();

//    boolean isCalculated();

    TVariable getTVariable();

    Function getFunction();

    TValue getTValue();

    FValue getFValue();
}
