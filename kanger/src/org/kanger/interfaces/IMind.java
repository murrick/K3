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

    int incTransactionCounter();

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
