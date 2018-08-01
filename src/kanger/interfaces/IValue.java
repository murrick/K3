package kanger.interfaces;


import kanger.primitives.Term;

/**
 * Created by murray on 02.12.16.
 */
public interface IValue {

    Term getValue();

    void setValue(Term term);

    boolean isEmpty();

    void clear();
}
