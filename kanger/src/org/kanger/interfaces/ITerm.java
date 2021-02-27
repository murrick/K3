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

import org.kanger.enums.DataType;

/**
 * Описатель терма.
 */
public interface ITerm extends Comparable<Object> {

    /**
     * Получить тип терма.
     *
     * @return тип терма.
     */
    DataType getType();

    /**
     * Получить идентификатор терма.
     *
     * @return идентификатор терма.
     */
    long getId();

    /**
     * Получить значение терма.
     *
     * @return значение терма.
     */
    Object getValue();

    /**
     * Признак того что терм не имеет значения.
     *
     * @return true - терм не имеет значения.
     */
    boolean isEmpty();

    /**
     * Признак того что терм помечен для удаления на указанном
     * уровне транзакции.
     *
     * @param mind уровень транзакции.
     * @return true если терм помечен для удаления.
     */
    boolean isDeleted(IMind mind);

    /**
     * Признак того что терм является u-переменной.
     *
     * @return true - терм является u-переменной.
     */
    boolean isCVariable();

    boolean equalsTo(ITerm term);

}
