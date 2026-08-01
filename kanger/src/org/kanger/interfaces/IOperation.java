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
 * Каноническое определение вычислимой операции библиотеки KANGER.
 * Операцией может быть системный предикат, встроенная функция либо функция,
 * определённая пользователем. Объект описывает само зарегистрированное
 * определение; результаты конкретных вычислений принадлежат узлам текущего
 * Mind и не изменяют identity операции.
 */
public interface IOperation {
    /**
     * Возвращает канонический идентификатор зарегистрированного определения.
     * Идентификатор используется ссылками скомпилированных функций и
     * предикатов и сохраняется при транзакционном наследовании объекта.
     * Он не является номером вычисления или физическим адресом storage.
     *
     * @return идентификатор определения операции
     */
    long getId();

    /**
     * Возвращает зарегистрированное символьное имя операции. Имя участвует в
     * разрешении исходного текста библиотекой, тогда как каноническая ссылка
     * скомпилированной модели использует {@link #getId()}.
     *
     * @return имя системного предиката, встроенной или пользовательской функции
     */
    String getName();

    /**
     * Возвращает arity операции — число её логических аргументов. Значение
     * относится к определению и не зависит от направления конкретного
     * вычисления или от заполненности параметров в текущем Mind.
     *
     * @return количество аргументов операции
     */
    int getRange();

    /**
     * Возвращает исходные JavaScript-сценарии пользовательской функции:
     * сначала сценарий прямого результата, затем доступные обратные сценарии
     * восстановления параметров. Для системных и встроенных операций список
     * может быть пустым. Возвращаемый список является описанием определения,
     * а не журналом выполненных вычислений.
     *
     * @return упорядоченный список исходных текстов сценариев
     */
    List<String> getScripts();

    /**
     * Возвращает объявленные имена параметров пользовательской функции в
     * порядке, соответствующем её arity и сценариям. Для операций без
     * пользовательского объявления список может быть пустым.
     *
     * @return упорядоченный список имён параметров определения
     */
    List<String> getParams();

    /**
     * Проверяет транзакционную видимость удаления определения. Пометка может
     * принадлежать указанному Mind либо быть унаследована из видимого нижнего
     * уровня; сам объект при этом сохраняет identity для существующих ссылок.
     *
     * @param mind Mind, в видимости которого проверяется состояние
     * @return {@code true}, если операция помечена для удаления в этом контексте
     */
    boolean isDeleted(IMind mind);

    /**
     * Возвращает семантическую категорию определения — функцию или предикат.
     * Категория является частью зарегистрированного контракта и не меняется
     * между вычислениями и транзакционными уровнями.
     *
     * @return категория операции
     */
    LibMode getMode();

    /**
     * Возвращает исходное декларативное представление пользовательского
     * определения, включая его сценарии. Метод предназначен для диагностики и
     * повторного представления определения; формат не является каноническим
     * persistence-протоколом и не должен использоваться как identity.
     *
     * @return текст определения операции
     */
    String asString();

}
