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

import org.kanger.Mind;
import org.kanger.enums.ArgumentType;

/**
 * Элемент строки аргументов - параметров предиката, функции или гипотезы.
 * Представляет собой объект типа терм, t-переменная, u-переменная,
 * функция, вариант подстановки t-переменной или вариант результата функции.
 * Базовый объект может иметь текущее значение типа терм если он является функцией
 * или t-переменной.
 */
public interface IArgument {

    /**
     * Типа базового объекта аргумента.
     *
     * @return тип базового объекта
     */
    ArgumentType getType();

    /**
     * Получить идентификатор базового объекта.
     *
     * @return идентификатор базового объекта.
     */
    long getId();

    /**
     * Получить базовый объект для указанного уровня транзакции.
     *
     * @param mind уровень транзакции.
     * @return базовый объект.
     * @throws Exception
     */
    Object getObject(IMind mind) throws Exception;

    /**
     * Получить текущее значение аргумента для указанного уровня транзакции.
     *
     * @param mind уровень транзакции.
     * @return текущее знаение типа терм.
     * @throws Exception
     */
    ITerm getValue(IMind mind) throws Exception;


    /**
     * Получить строковое представление аргумента для указанного уровня
     * транзакции.
     *
     * @param mind уровень транзакции.
     * @return строковое представление аргумента.
     */
    String toString(IMind mind) throws Exception;

    /**
     * Признак того что текущее значение аргумента для указанного уровня
     * транзакции неопределено.
     *
     * @param mind уровень транзакции.
     * @return true если текущее значение не определено.
     */
    boolean isEmpty(Mind mind);

    /**
     * Признак того что базовый объект на указанном уровне транзакции помечен
     * для удаления.
     *
     * @param mind уровень транзакци.
     * @return true - объект помечен для удаления
     */
    boolean isDeleted(IMind mind);

//    IMind getMind();
}
