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
 * Общая caller-visible граница канонического реестра объектов KANGER.
 * Реализация принадлежит конкретному {@link IMind} и предоставляет чтение
 * объектов, видимых на его транзакционном уровне, включая унаследованное
 * состояние и локальный overlay.
 *
 * <p>Интерфейс не предоставляет операции создания или удаления: такие
 * изменения выполняются специализированными фабриками, которые одновременно
 * поддерживают каноническую identity и транзакционные инварианты.</p>
 *
 * @param <T> тип объектов, принадлежащих реестру
 */
public interface IFactory<T> extends Iterable<T> {

    /**
     * Возвращает объект с указанным устойчивым идентификатором в текущей
     * транзакционной видимости фабрики.
     *
     * @param id идентификатор объекта KANGER
     * @return объект с указанным идентификатором
     * @throws Exception если идентификатор недоступен, состояние фабрики
     *                   повреждено или underlying storage не может выполнить
     *                   чтение
     */
    T get(long id) throws Exception;

    /**
     * Возвращает физическое число зарегистрированных элементов в видимом
     * реестре. Значение включает объекты, логически помеченные для удаления,
     * поэтому не является числом активных семантических объектов.
     *
     * @return число зарегистрированных элементов, включая deleted entries
     * @throws Exception если размер нельзя получить из текущего состояния или
     *                   underlying storage
     */
    int size() throws Exception;

    /**
     * Проверяет, содержит ли видимый реестр хотя бы один зарегистрированный
     * элемент. Как и {@link #size()}, проверка относится к физическому составу
     * реестра и не фильтрует элементы, помеченные для удаления.
     *
     * @return {@code true}, если зарегистрированных элементов нет;
     *         {@code false} в противном случае
     */
    boolean isEmpty();

}
