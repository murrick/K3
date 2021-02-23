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

import java.util.Set;

public interface IRule {
    Set<ICause> getCauses();

    boolean isGenerated();

    boolean isStored();

    long getId();

    ITerm getOrigin() throws Exception;

    boolean isQuery();

    boolean isRestored(IMind mind);

    boolean isDeleted(IMind mind);

    boolean isSubstitutable();

    boolean isAbstractive();

    long getMindId();

    boolean isAntc() throws Exception;

    IPredicate getPredicate(IMind mind) throws Exception;

    long getPredicateId() throws Exception;

    IList getArguments() throws Exception;

    IMind getMind();

    IComment getComment() throws Exception;

    void setComment(String term) throws Exception;
}
