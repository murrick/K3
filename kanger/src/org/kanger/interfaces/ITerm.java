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
 * Канонический терм KANGER: типизированное элементарное значение либо
 * внутренний u-свидетель. Обычные значения интернируются и сравниваются по
 * канонической семантике; u-переменная имеет собственную identity и не должна
 * смешиваться с внешней проекцией конкретных значений.
 */
public interface ITerm extends Comparable<Object> {

    /**
     * Возвращает логический тип значения. Тип является частью канонической
     * семантики терма и определяет правила сравнения и представления.
     *
     * @return тип данных терма
     */
    DataType getType();

    /**
     * Возвращает канонический идентификатор терма в пространстве объектов
     * KANGER. Идентификатор сохраняется при транзакционном наследовании и
     * commit; он не является физическим адресом storage. Для u-переменной ID
     * идентифицирует именно свидетель, а не его возможную подстановку.
     *
     * @return идентификатор терма
     */
    long getId();

    /**
     * Возвращает неизменяемое логическое значение обычного терма. Для
     * служебного u-свидетеля значение может отсутствовать; вычисляемые значения
     * t-переменных и функций принадлежат соответствующим argument/value узлам,
     * а не изменяют этот объект.
     *
     * @return значение терма либо {@code null}, если терм значения не содержит
     */
    Object getValue();

    /**
     * Проверяет отсутствие собственного значения у терма. В частности, это
     * состояние допустимо для u-переменной и не означает удаление объекта или
     * отсутствие его канонической identity.
     *
     * @return {@code true}, если собственное значение отсутствует
     */
    boolean isEmpty();

    /**
     * Проверяет транзакционную видимость удаления терма. Пометка относится к
     * указанному Mind; объект и его ID могут сохраняться для ссылочной
     * целостности, rollback и ранее скомпилированных структур.
     *
     * @param mind Mind, в котором проверяется состояние
     * @return {@code true}, если терм помечен для удаления
     */
    boolean isDeleted(IMind mind);

    /**
     * Проверяет, представляет ли терм внутреннюю u-переменную. Такой объект
     * является транзакционно принадлежащим свидетелем и не включается во
     * внешнюю проекцию Values как конкретная пользовательская подстановка.
     *
     * @return {@code true}, если терм является u-переменной
     */
    boolean isCVariable();

    /**
     * Сравнивает логические значения двух термов без требования совпадения их
     * объектных ссылок. Для канонических обычных термов результат согласован с
     * типом и значением; identity различных u-свидетелей не должна случайно
     * схлопываться только из-за отсутствующего значения.
     *
     * @param term сравниваемый терм
     * @return {@code true}, если термы эквивалентны по контракту значения
     */
    boolean equalsTo(ITerm term);

}
