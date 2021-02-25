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

import org.kanger.enums.LibMode;

import java.util.List;

/**
 * Описатель вычислимой операции. Такой операцией может быть
 * системный предикат, такой как равенство, проверка на больше-меньше,
 * операция Принадлежность, и т.д., встроенная функция или функция
 * определяемая пользователем.
 */
public interface IOperation {
    /**
     * Получить идентификатор операции.
     *
     * @return идентификатор операции.
     */
    long getId();

    /**
     * Получить символьное имя операции.
     *
     * @return имя системного предиката, втроенной или определяемой
     * пользователем функции.
     */
    String getName();

    /**
     * Получить ранг операции.
     *
     * @return ранг операции - количество аргументов.
     */
    int getRange();

    /**
     * В случае функции определяемой пользователем метод возвращает
     * список исходных текстов на языке JavaScript последовательно для
     * вычисления результата функции и для обратных вычислений значений
     * параметров.
     *
     * @return список исходных текстов.
     */
    List<String> getScripts();

    /**
     * В случае функции определяемой пользователем метод возвращает
     * список имен параметров функции.
     *
     * @return список имен параметров функции.
     */
    List<String> getParams();

    /**
     * Признак пометки на удаление для указанного уровня транзакции.
     *
     * @param mind уровень транзакции.
     * @return true - операция помечена для удаления.
     */
    boolean isDeleted(IMind mind);

    /**
     * Возвращает тип операции - функция или предикат.
     *
     * @return тип операции.
     */
    LibMode getMode();

    /**
     * В случае функции определяемой пользователем метод возвращает
     * в виде текста описание функции в том виде, в котором она была
     * определена, включая все исходные текстоы на языке JavaScript.
     *
     * @return исходное описание функции.
     */
    String asString();

}
