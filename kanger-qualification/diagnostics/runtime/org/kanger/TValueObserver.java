package org.kanger;
import org.kanger.units.TValue;
import org.kanger.factory.TValueFactory;
/** Experimental callback contract; no diagnostic implementation dependency. */
public interface TValueObserver {
    void constructed(Mind m);
    void reset(Mind m);
    void mark(Mind m);
    void complete(Mind m);
    void touch(Mind m,TValue v,String reason);
    void promoted(Mind m,TValueFactory f);
    void metadata(TValue v,long id,long variable,long term,String reason);
    void beginSettlement(Mind m);
    void endSettlement(Mind m);
    void retire(Mind m);
    Object beforeClear(Mind m,TValueFactory f);
    void afterClear(Mind m,Object token);
}
