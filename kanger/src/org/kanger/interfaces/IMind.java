package org.kanger.interfaces;

import org.kanger.stores.HypothesisStore;
import org.kanger.stores.LogStore;
import org.kanger.stores.SolutionsStore;
import org.kanger.stores.ValuesStore;

import java.util.Collection;

public interface IMind {

    IUser getUser();

    long getId();

    IMind getNext();

    IMind getTop();


    Boolean query(String line) throws Exception;

    Boolean query(String line, Object[] ext) throws Exception;

    boolean compile(String src) throws Exception;

    boolean commit(IMind m) throws Exception;

    void release(IMind m) throws Exception;


    IFactory<ITerm> getTerms();

    IFactory<IPredicate> getPredicates();

    IFactory<IRule> getRules();

    IFactory<IComment> getComments();

    IFactory<IOperation> getLibrary();

    HypothesisStore getHypothesis();

    ValuesStore getValues();

    SolutionsStore getSolutions();

    LogStore getLog();


    String getSourceFileName();

    void setSourceFileName(String fname);

    String getSourceCode() throws Exception;

    String getCompliedString();

    String getQueryString();

    Object getQueryResult();

    IRule getAcceptedRule();


    String getVersion();

    int getDebugLevel();

    void setDebugLevel(int debugLevel);

    int getFloodControlLimit();

    void setFloodControlLimit(int floodControlLimit);


    int getTransactionLevel();

    boolean isEmptyLevel();


    boolean isStorageUsed();

    String getStorageName();

    Collection<String> getStoragesList();

    IMind useStorage(String name) throws Exception;

    IMind closeStorage() throws Exception;

    IMind clearStorage() throws Exception;

    IMind reindexStorage(String name) throws Exception;

    IMind reindexStorage(String name, IReactor reactor) throws Exception;

    IMind removeStorage(String name) throws Exception;


}
