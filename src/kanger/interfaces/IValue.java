package kanger.interfaces;


import kanger.primitives.Term;
import kanger.primitives.*;

/**
 * Created by murray on 02.12.16.
 */
public interface IValue<T> {

    Term getValue();

    T setValue(Term term);

    boolean isEmpty();

    void clear()
   
    boolean isTSet();
   
    boolean isFSet();
   
    boolean isVSet();
   
    boolean isTerm();
   
    boolean isCVar();
   
    TVariable getT();
   
    Function getF();
   
    TValue getV();
}
