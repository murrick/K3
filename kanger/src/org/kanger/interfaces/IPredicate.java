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

/**
 * Описатель предиката.
 */
public interface IPredicate {

    /**
     * Получить имя предиката.
     *
     * @return имя предиката в виде строки.
     * @throws Exception
     */
    String getName() throws Exception;

    /**
     * Получить ранг предиката.
     *
     * @return ранг предиката - количество аргументов.
     */
    int getRange();

    /**
     * Получить идентификатор предиката.
     *
     * @return идентификатор предиката.
     */
    long getId();

    /**
     * Признак пометки на удаление для указанного уровня транзакции.
     *
     * @param mind уровень транзакции.
     * @return true - предикат помечен для удаления
     */
    boolean isDeleted(IMind mind);

    /**
     * Признак того что программа не содержит утверждений с
     * этим предикатом на указанном уровне транзакции.
     *
     * @param mind уровень транзакции.
     * @return true если в программе нет утверждений с предикатом.
     * @throws Exception
     */
    boolean isEmpty(IMind mind) throws Exception;

    /**
     * Получить список утверждений с этим предикатом на
     * указанном уровне транзакции.
     *
     * @param mind уровень транзакции.
     * @return список утверждений.
     * @throws Exception
     */
    Set<IRule> getSolves(IMind mind) throws Exception;

}
