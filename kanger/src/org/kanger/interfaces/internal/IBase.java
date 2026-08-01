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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package org.kanger.interfaces.internal;

import org.kanger.interfaces.IMind;

import java.util.Collection;

/**
 * Внутренняя граница одной логической persistent-схемы KANGER внутри выбранного
 * storage generation.
 *
 * <p><strong>Представление и владение.</strong> {@code IBase} представляет не
 * транзакцию и не factory cache, а schema-specific persistent address space:
 * записи {@link IStep}, allocation domain идентификаторов, восстановленные
 * chain endpoints, физическую durability и implementation-specific cache.
 * Экземпляр создаётся и удерживается {@link IData}; {@code User} публикует
 * полный набор таких баз корневым фабрикам, которые только заимствуют ссылки.</p>
 *
 * <p><strong>Chain authority.</strong> {@link #getRoot()} и {@link #getTop()}
 * описывают концы persistent linked representation. Порядок цепочки задаётся
 * ссылками {@code IStep.next}, а не числовым порядком ID или позицией записи в
 * физическом индексе. Реализация обязана восстанавливать endpoints из
 * сохранённых связей и сигнализировать повреждение согласно своему recovery
 * contract.</p>
 *
 * <p><strong>Hydration и materialization.</strong> {@link #get(long)}
 * гидратирует persistent step по canonical ID. {@link #add(IStep)} и
 * {@link #update(IStep)} публикуют или изменяют физическое представление уже
 * подготовленного chain node; orchestration перехода memory-only Step в
 * persistent Sapato остаётся у snapshot/cache layer. {@link #containsKey(long)}
 * проверяет physical namespace и не заменяет semantic lookup фабрики.</p>
 *
 * <p><strong>Удаление и очистка.</strong> {@link #delete(long)} и
 * {@link #deleteAll(Collection)} удаляют выбранные physical records;
 * {@link #clear()} очищает всю логическую базу. Эти операции отличаются от
 * {@link #clearCache()}, который освобождает только implementation-specific
 * hydration/cache state, и от {@link #close()}, завершающего ресурс базы.
 * Владельцы транзакционных overlay не должны вызывать destructive operations
 * как обычный rollback.</p>
 *
 * <p><strong>Persistence lifecycle.</strong> {@link #flush()} задаёт
 * durability boundary накопленных изменений. {@link #reindex(IBase, IMind)}
 * переносит логическую схему в другую базу с использованием переданного
 * контекста гидратации; storage-wide sequencing и атомарность выбора generation
 * принадлежат {@link IData} и {@code User}, а не отдельной базе.</p>
 *
 * <p><strong>Идентификаторы.</strong> {@link #lastId()} и {@link #nextId()}
 * принадлежат schema-local persistent allocation domain. ID является
 * operational identity внутри поддерживаемого generation и не определяет
 * linked traversal order.</p>
 *
 * <p><strong>Cache telemetry.</strong> Метрики hits, misses, evictions и entry
 * count относятся к физическому storage cache реализации. Они не описывают
 * canonical semantic ownership, transaction visibility или Escalera indexes.
 * Значение {@code -1} означает, что совместимая реализация не публикует данную
 * метрику.</p>
 *
 * <p><strong>Concurrency и ошибки.</strong> Интерфейс не обещает независимую
 * thread-safety каждой операции. Вызывающий код соблюдает synchronization и
 * lifecycle владельца storage. Возвращаемый {@code null}, checked exception и
 * recovery failure имеют implementation-specific смысл; вызывающая сторона не
 * должна превращать ошибку hydration в доказанное отсутствие semantic unit.</p>
 *
 * <p><strong>Совместимость.</strong> Конкретные DUMB file layout, WAL,
 * base-code packing, cache policy и recovery algorithm являются доказательной
 * реализацией этого контракта, но не универсальной частью интерфейса.</p>
 *
 * @see IData
 * @see ICache
 * @see IStep
 */
public interface IBase {

    /**
     * Публикует новый persistent step в schema-local address space.
     *
     * <p>Переданный step должен уже иметь согласованные identity, hash и chain
     * linkage. Метод не создаёт semantic unit и не выполняет transaction commit.</p>
     *
     * @param one подготовленный persistent step
     * @throws Exception если запись, индексирование или durability preparation
     *                   не могут быть завершены
     */
    void add(IStep one) throws Exception;

    /**
     * Обновляет физическое представление существующего persistent step.
     *
     * @param one step с существующей schema-local identity
     * @throws Exception если запись отсутствует, повреждена или не может быть
     *                   обновлена атомарно согласно реализации
     */
    void update(IStep one) throws Exception;

    /**
     * Гидратирует persistent step по schema-local canonical ID.
     *
     * <p>Отсутствие записи и failure гидратации являются разными состояниями;
     * вызывающая сторона обязана сохранять это различие.</p>
     *
     * @param id schema-local persistent ID
     * @return гидратированный step либо implementation-defined {@code null},
     *         если контракт реализации допускает отсутствие
     * @throws Exception при ошибке чтения, проверки или восстановления записи
     */
    IStep get(long id) throws Exception;

    /**
     * Освобождает implementation-specific hydration cache без удаления
     * persistent records и без закрытия базы.
     */
    void clearCache();

    /**
     * Проверяет отсутствие persistent records в логической базе.
     *
     * @return {@code true}, если physical namespace базы пуст
     */
    boolean isEmpty();

    /**
     * Удаляет persistent record с указанным schema-local ID.
     *
     * <p>Метод является destructive storage operation и не эквивалентен
     * transaction rollback или semantic deletion overlay.</p>
     *
     * @param id удаляемый persistent ID
     * @throws Exception если удаление или обновление индексов не завершено
     */
    void delete(long id) throws Exception;

    /**
     * Последовательно удаляет набор persistent records, пропуская
     * {@code null}-коллекцию и {@code null}-элементы.
     *
     * <p>Default implementation не обещает group atomicity: при исключении
     * часть предшествующих удалений уже может быть выполнена.</p>
     *
     * @param ids удаляемые schema-local IDs
     * @throws Exception при первом неуспешном удалении
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

    /**
     * Полностью очищает physical namespace логической базы.
     *
     * @throws Exception если destructive reset не может быть завершён
     */
    void clear() throws Exception;

    /**
     * Переносит содержимое этой логической базы в destination base.
     *
     * <p>{@code mind} используется как контекст гидратации/materialization.
     * Выбор поколения, swap и rollback migration принадлежат storage owner.</p>
     *
     * @param to destination base другого migration generation
     * @param mind контекст гидратации persistent units
     * @throws Exception при чтении, преобразовании или публикации destination
     */
    void reindex(IBase to, IMind mind) throws Exception;

    /**
     * Проверяет наличие physical record в schema-local namespace.
     *
     * @param id persistent ID
     * @return {@code true}, если physical record существует
     * @throws Exception если индекс нельзя прочитать или проверить
     */
    boolean containsKey(long id) throws Exception;

    /**
     * Возвращает первый endpoint восстановленной persistent chain.
     *
     * @return root step либо {@code null} для пустой базы
     */
    IStep getRoot();

    /**
     * Возвращает последний endpoint восстановленной persistent chain.
     *
     * @return top step либо {@code null} для пустой базы
     */
    IStep getTop();

    /**
     * Возвращает schema name этой логической базы.
     *
     * @return stable generation-local имя схемы
     */
    String getName();

    /**
     * Возвращает текущий объём implementation-specific cache.
     *
     * @return используемый объём в единицах реализации
     */
    long getUsedCacheSize();

    /**
     * Возвращает настроенный предел implementation-specific cache.
     *
     * @return предел cache; неположительное значение обычно означает disabled
     */
    long getMaxCacheSize();

    /**
     * Возвращает число успешных обращений к physical storage cache.
     *
     * @return hits либо {@code -1}, если telemetry не поддерживается
     */
    default long getCacheHits() {
        return -1L;
    }

    /**
     * Возвращает число промахов physical storage cache.
     *
     * @return misses либо {@code -1}, если telemetry не поддерживается
     */
    default long getCacheMisses() {
        return -1L;
    }

    /**
     * Возвращает число вытеснений из physical storage cache.
     *
     * @return evictions либо {@code -1}, если telemetry не поддерживается
     */
    default long getCacheEvictions() {
        return -1L;
    }

    /**
     * Возвращает количество записей в physical storage cache.
     *
     * @return entry count либо {@code -1}, если telemetry не поддерживается
     */
    default long getCachedEntryCount() {
        return -1L;
    }

    /**
     * Проверяет, включён ли implementation-specific cache.
     *
     * @return {@code true}, если объявленный предел cache положителен
     */
    default boolean isCacheEnabled() {
        return getMaxCacheSize() > 0L;
    }

    /**
     * Возвращает последний выделенный schema-local persistent ID.
     *
     * @return последний выделенный ID; значение не задаёт chain order
     */
    long lastId();

    /**
     * Выделяет следующий schema-local persistent ID.
     *
     * <p>Метод изменяет allocation state и не обязан создавать запись.</p>
     *
     * @return новый уникальный ID в текущем generation
     */
    long nextId();

    /**
     * Доводит накопленные physical changes до durability boundary реализации,
     * не закрывая базу.
     *
     * @throws Exception если flush не может быть завершён
     */
    void flush() throws Exception;

    /**
     * Завершает lifecycle этой базы и освобождает принадлежащие ей ресурсы.
     *
     * <p>После вызова ранее заимствованные ссылки должны считаться недействительными.</p>
     *
     * @throws Exception если закрытие одного или нескольких ресурсов не завершено
     */
    void close() throws Exception;

    /**
     * Возвращает implementation-specific UDF class, связанный с базой.
     *
     * @return UDF class либо {@code null}, если база его не публикует
     */
    Class getUdf();

}
