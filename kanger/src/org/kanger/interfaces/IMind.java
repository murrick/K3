package org.kanger.interfaces;

import org.kanger.enums.QueryPass;
import org.kanger.factory.*;
import org.kanger.interfaces.internal.IReactor;
import org.kanger.stores.HypothesisStore;
import org.kanger.stores.LogStore;
import org.kanger.stores.SolutionsStore;
import org.kanger.stores.ValuesStore;
import org.kanger.units.Rule;

import java.util.Collection;

public interface IMind {

    boolean commit(IMind m) throws Exception;

    void release(IMind m) throws Exception;

    QueryPass getQueryPass();

    IUser getUser();

    long getId();

    IMind getNext();

    int getDebugLevel();

    void setDebugLevel(int debugLevel);

    DictionaryFactory getTerms();

    PredicateFactory getPredicates();

    ValuesStore getValues();

    RuleFactory getRules();

    CommentFactory getComments();

    LibraryFactory getLibrary();

    HypothesisStore getHypothesis();

    LogStore getLog();

    SolutionsStore getSolutions();

    boolean compile(String src) throws Exception;

    String getSourceFileName();

    void setSourceFileName(String fname);

    Boolean query(String line) throws Exception;

    Boolean query(String line, Object[] ext) throws Exception;

    String getCompliedLine();

    String getVersion();

    String getQuerySource();

    Object getQueryResult();

    Rule getAcceptedRule();

    int getFloodControlLimit();

    void setFloodControlLimit(int floodControlLimit);

    int getTransactionLevel();

    boolean isEmpty();

    IMind getTop();

    IMind useStorage(String name) throws Exception;

    IMind closeStorage() throws Exception;

    IMind clearStorage() throws Exception;

    IMind reindexStorage(IReactor reactor) throws Exception;

    IMind removeStorage(String name) throws Exception;

    boolean isStorageUsed();

    String getStorageName();

    Collection<String> getStoragesList();

    String getSourceCode() throws Exception;
}
