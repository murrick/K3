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
 */

package org.kanger.interfaces.internal;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * Внутренняя граница транзакционного cache/snapshot слоя KANGER.
 *
 * <p><strong>Архитектурная роль.</strong> {@code ICache} хранит локальную
 * проекцию {@link IUnit}: созданные, гидратированные, изменённые или удалённые
 * единицы текущего контекста. Cache не является persistent {@link IBase} и не
 * определяет глобальную canonical identity; он связывает semantic units с
 * transaction-local checkpoint lifecycle.</p>
 *
 * <p><strong>Checkpoint lifecycle.</strong> {@link #mark()} открывает локальную
 * границу изменений, {@link #commit()} принимает текущую границу в cache, а
 * {@link #release()} отменяет изменения после последней отметки согласно
 * реализации. Эти операции не публикуют physical storage generation и не
 * заменяют commit/release объекта Mind.</p>
 *
 * <p><strong>Индексация.</strong> {@link #find(int)} является lookup по
 * implementation hash и возвращает candidate IDs, а не доказательство
 * semantic equality. Вызывающая factory обязана дополнительно проверить
 * {@link IUnit#equalsTo(Object)} или эквивалентный canonical contract.</p>
 *
 * <p><strong>Linked projection.</strong> Root относится к локальному linked
 * snapshot representation. Его изменение не переписывает persistent chain до
 * явной materialization/update фазы.</p>
 *
 * <p><strong>Concurrency и ошибки.</strong> Интерфейс не обещает независимую
 * thread-safety. Владелец Mind/factory сериализует checkpoint lifecycle и не
 * должен трактовать exception как доказанное отсутствие semantic unit.</p>
 *
 * @see IUnit
 * @see IStep
 * @see IBase
 */
public interface ICache extends Iterable {

    /**
     * Добавляет единицу в локальную cache-проекцию.
     *
     * @param one semantic unit, принадлежащая совместимому владельцу
     * @throws Exception если регистрация или локальная индексация не удалась
     */
    void add(IUnit one) throws Exception;

//    void update(IUnit one) throws Exception;

    /**
     * Возвращает локально доступный объект по operational ID.
     *
     * @param id идентификатор внутри cache namespace
     * @return объект или {@code null}, если реализация не содержит запись
     * @throws Exception если lookup или hydration завершились ошибкой
     */
    Object get(long id) throws Exception;

    /**
     * Удаляет локальную cache-запись или регистрирует её удаление согласно
     * реализации. Операция сама по себе не гарантирует physical deletion.
     *
     * @param id operational ID
     * @throws Exception если изменение cache невозможно
     */
    void delete(long id) throws Exception;

    /**
     * Последовательно удаляет перечисленные ID.
     *
     * <p>Default-реализация не является атомарным batch: при исключении ранее
     * обработанные элементы могут остаться удалёнными.</p>
     *
     * @param ids идентификаторы; {@code null} и элементы {@code null}
     *            игнорируются
     * @throws Exception если одно из удалений завершилось ошибкой
     */
    default void deleteAll(Collection<Long> ids) throws Exception {
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    delete(id);
                }
            }
        }
    }

    /** @return число локально учитываемых cache entries */
    int size();

    /** @return {@code true}, если cache не содержит локальных entries */
    boolean isEmpty();

    /**
     * Возвращает candidate IDs по implementation hash.
     *
     * @param h hash semantic representation
     * @return множество кандидатов, возможно пустое
     * @throws Exception если индекс недоступен или повреждён
     */
    Set<Long> find(int h) throws Exception;

    /**
     * Очищает локальное cache-состояние. Это не эквивалентно очистке
     * persistent {@link IBase}.
     *
     * @throws Exception если cleanup не может быть завершён
     */
    void clear() throws Exception;

    /**
     * Открывает checkpoint текущего локального состояния.
     *
     * @return implementation checkpoint token или позиция
     */
    long mark();

    /**
     * Принимает изменения текущего checkpoint внутри cache lifecycle.
     *
     * @return implementation checkpoint token или позиция после commit
     */
    long commit();

    /**
     * Отменяет изменения после последней отметки согласно реализации.
     *
     * @return восстановленный checkpoint token или позиция
     * @throws Exception если rollback локального состояния не удался
     */
    long release() throws Exception;

    /**
     * Проверяет наличие ID в локальном namespace.
     *
     * @param id operational ID
     * @return {@code true}, если cache содержит соответствующую запись
     * @throws Exception если lookup завершился ошибкой
     */
    boolean containsKey(long id) throws Exception;

    /**
     * Создаёт итератор начиная с implementation-defined позиции ID.
     *
     * @param fromId нижняя граница или cursor согласно реализации
     * @return итератор локальной проекции
     */
    Iterator<Object> iterator(long fromId);

    /** @return root локального linked snapshot или {@code null} */
    IStep getRoot();

    /**
     * Устанавливает root локального linked snapshot.
     *
     * @param root новый root или {@code null}
     */
    void setRoot(IStep root);

    /** @return итератор всех локально доступных объектов */
    @Override
    Iterator<Object> iterator();

    /**
     * Материализует или синхронизирует накопленные cache changes согласно
     * реализации владельца.
     *
     * @return {@code true}, если update сообщил о фактическом изменении
     * @throws Exception если materialization/synchronization не удалась
     */
    boolean update() throws Exception;

}
