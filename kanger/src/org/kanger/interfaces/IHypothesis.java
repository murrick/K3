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
 * Описатель гипотезы. В общем случае гипотеза представляет собой утверждение,
 * для которого определен предикат, строка параметров и признак отношения к антецеденту
 * или сукцеденту.
 */
public interface IHypothesis /*extends Comparable<IHypothesis>*/ {

    /**
     * Получить описатель предиката на котором построено гипотетическое
     * утверждение.
     *
     * @return описатель предиката.
     * @throws Exception
     */
    IPredicate getPredicate() throws Exception;

    /**
     * Массив аргументов. Прдетставляет собой список реализующий интерфейс IList
     * содержащий элементы типа IArgument.
     *
     * @return Массив аргументов
     */
    IList getArguments();

    /**
     * Получить признак отношения гипотетического утверждения к антецеденту
     * или к сукцеденту.
     *
     * @return true если утверждение относится к антецеденту, false - к сукцеденту.
     */
    boolean isAntc();

}
