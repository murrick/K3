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

/**
 * Узел дерева вывода. Множество таких узлов связывается в поцессе вывода
 * с производным утверждением и позволяет в последствии рекурсивно восстановить
 * последовательность фаз его получения. Включает правило, которое
 * явилось основанием, и утверждение, которое явилось донором-источником,
 * для полученного результата.
 * <pre>
 *
 * Пример структуры дерева вывода:
 *
 * Утверждение производное: !child(Tom, John);
 *       Правило-основание: !@x @y father(x, y) -> child(y, x);
 *       Утверждение-донор: !father(John, Tom);
 * </pre>
 */
public interface ICause {

    /**
     * Получить утверждение, явившееся донором-источником для
     * фазы вывода, описываемой узлом.
     *
     * @param mind Текущий уровень транзакции
     * @return Объект IRule описывающий утверждение-донор.
     * @throws Exception
     */
    IRule getDonor(IMind mind) throws Exception;

    /**
     * Получить правило, явившееся основанием для
     * фазы вывода, описываемой узлом.
     *
     * @param mind Текущий уровень транзакции
     * @return Объект IRule описывающий правило-основание.
     * @throws Exception
     */
    IRule getRule(IMind mind) throws Exception;

}
