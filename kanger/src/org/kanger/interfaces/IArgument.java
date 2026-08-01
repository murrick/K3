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
 * Контекстно разрешаемая позиция аргумента правила, предиката, функции или
 * гипотезы KANGER.
 *
 * <p><strong>Архитектурная роль.</strong> Аргумент не является отдельным
 * значением и не владеет вычислением. Он сохраняет ссылку на базовый
 * семантический объект — терм, t-переменную, u-свидетель, функцию либо вариант
 * их runtime-проекции — и позволяет разрешить этот объект и его текущее
 * значение в видимости конкретного {@link IMind}.</p>
 *
 * <p><strong>Identity и projection.</strong> {@link #getId()} идентифицирует
 * базовый объект аргумента, тогда как {@link #getValue(IMind)} возвращает
 * контекстную value-проекцию. Для терма эти уровни обычно совпадают; для
 * t-переменной или функции текущее значение может отсутствовать либо меняться
 * между Mind, не изменяя identity самого аргумента.</p>
 *
 * <p><strong>Lifecycle.</strong> Аргумент принадлежит скомпилированной
 * структуре правила или вычисления. Runtime-значения принадлежат текущему Mind
 * и его фабрикам. Удаление базового объекта влияет на видимость аргумента, но
 * не превращает сохранённую ссылку в новое значение и не меняет её ID.</p>
 *
 * <p><strong>Инварианты.</strong> Базовый объект не равен его текущему
 * значению; неопределённое значение не означает отсутствия аргумента;
 * диагностическое представление не является identity или persistence-форматом.</p>
 */
public interface IArgument {

    /**
     * Возвращает семантическую разновидность базового объекта аргумента.
     *
     * <p>Тип описывает форму ссылки и не зависит от текущего значения
     * t-переменной или функции в конкретном Mind.</p>
     *
     * @return тип базового объекта аргумента
     */
    ArgumentType getType();

    /**
     * Возвращает устойчивый идентификатор базового объекта аргумента.
     *
     * <p>Это не идентификатор текущей подстановки, результата функции или
     * физический адрес storage.</p>
     *
     * @return идентификатор базового объекта
     */
    long getId();

    /**
     * Разрешает базовый семантический объект в видимости указанного Mind.
     *
     * <p>Фактический тип результата определяется {@link #getType()}. Метод
     * может выполнять ленивую hydration или разрешение transactional overlay,
     * но не создаёт новую identity только из-за чтения.</p>
     *
     * @param mind Mind, относительно которого разрешается объект
     * @return видимый базовый объект аргумента
     * @throws Exception если ссылка повреждена либо объект нельзя загрузить или
     *                   разрешить в указанном контексте
     */
    Object getObject(IMind mind) throws Exception;

    /**
     * Возвращает текущее терм-значение аргумента в указанном Mind.
     *
     * <p>Для обычного терма возвращается сам терм; для t-переменной или функции
     * — их текущая вычисленная проекция. Значение может быть {@code null}, если
     * аргумент ещё не определён. Внутренний u-свидетель не должен ошибочно
     * публиковаться как конкретная пользовательская подстановка.</p>
     *
     * @param mind Mind, определяющий runtime-значение
     * @return текущий терм либо {@code null}, если значение не определено
     * @throws Exception если базовый объект или его value-проекцию нельзя
     *                   разрешить
     */
    ITerm getValue(IMind mind) throws Exception;

    /**
     * Формирует диагностическое строковое представление аргумента в контексте
     * указанного Mind.
     *
     * <p>Представление может включать разрешённый базовый объект или текущее
     * значение и не является стабильным сериализационным форматом.</p>
     *
     * @param mind Mind, относительно которого разрешаются объект и значение
     * @return человекочитаемое представление аргумента
     * @throws Exception если аргумент нельзя разрешить в указанном Mind
     */
    String toString(IMind mind) throws Exception;

    /**
     * Проверяет, отсутствует ли текущее значение аргумента в указанном Mind.
     *
     * <p>Пустота относится к value-проекции и не означает отсутствия базового
     * объекта или его identity.</p>
     *
     * @param mind Mind, в котором проверяется значение
     * @return {@code true}, если текущее значение не определено
     */
    boolean isEmpty(Mind mind);

    /**
     * Проверяет транзакционную видимость удаления базового объекта аргумента.
     *
     * <p>Пометка удаления не уничтожает сохранённую ссылку и не эквивалентна
     * неопределённому текущему значению.</p>
     *
     * @param mind Mind, в котором проверяется состояние
     * @return {@code true}, если базовый объект помечен для удаления
     */
    boolean isDeleted(IMind mind);

//    IMind getMind();
}
