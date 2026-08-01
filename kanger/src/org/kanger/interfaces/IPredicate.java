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
 * Канонический описатель предиката KANGER. Предикат задаёт имя и arity,
 * используемые правилами, а его identity сохраняется независимо от множества
 * утверждений, видимых в конкретном транзакционном Mind.
 */
public interface IPredicate {

    /**
     * Возвращает имя предиката, разрешённое в видимости указанного Mind.
     * Контекст необходим из-за ленивой загрузки и transactional overlay;
     * получение имени не создаёт нового предиката и не меняет его identity.
     *
     * @param mind Mind, используемый для разрешения видимого определения
     * @return зарегистрированное имя предиката
     * @throws Exception если определение нельзя разрешить или загрузить
     */
    String getName(IMind mind) throws Exception;

    /**
     * Возвращает arity предиката — число аргументов каждого использующего его
     * утверждения. Значение является частью канонического определения и не
     * зависит от текущих подстановок или наличия утверждений.
     *
     * @return количество аргументов предиката
     */
    int getRange();

    /**
     * Возвращает канонический идентификатор предиката. Правила используют его
     * как устойчивую логическую ссылку; это не порядковый номер утверждения и
     * не физический адрес persistent storage.
     *
     * @return идентификатор предиката
     */
    long getId();

    /**
     * Проверяет, помечено ли определение для удаления в транзакционной
     * видимости указанного Mind. Пометка не уничтожает identity объекта,
     * необходимую уже существующим правилам и rollback.
     *
     * @param mind Mind, в котором проверяется видимость удаления
     * @return {@code true}, если предикат помечен для удаления
     */
    boolean isDeleted(IMind mind);

    /**
     * Проверяет отсутствие видимых утверждений, использующих этот предикат.
     * Результат относится к текущей транзакционной проекции и не означает,
     * что каноническое определение предиката отсутствует.
     *
     * @param mind Mind, определяющий видимость правил
     * @return {@code true}, если видимых утверждений с предикатом нет
     * @throws Exception если набор правил нельзя разрешить или загрузить
     */
    boolean isEmpty(IMind mind) throws Exception;

    /**
     * Возвращает множество видимых утверждений, использующих этот предикат.
     * Набор формируется для указанного Mind и учитывает transactional overlay,
     * удаления и восстановление правил. Его нельзя использовать как глобальный
     * неизменяемый индекс всех исторических утверждений.
     *
     * @param mind Mind, определяющий транзакционную видимость
     * @return множество видимых утверждений с данным предикатом
     * @throws Exception если правила нельзя разрешить или загрузить
     */
    Set<IRule> getSolves(IMind mind) throws Exception;

}
