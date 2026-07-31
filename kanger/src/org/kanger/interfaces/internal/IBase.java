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

    void add(IStep one) throws Exception;

    void update(IStep one) throws Exception;

    IStep get(long id) throws Exception;

    void clearCache();

    boolean isEmpty();

    void delete(long id) throws Exception;

    default void deleteAll(Collection<Long> ids) throws Exception {
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    delete(id);
                }
            }
        }
    }

    void clear() throws Exception;

    void reindex(IBase to, IMind mind) throws Exception;

    boolean containsKey(long id) throws Exception;

    IStep getRoot();

    IStep getTop();

    String getName();

    long getUsedCacheSize();

    long getMaxCacheSize();

    /**
     * Optional cache telemetry. Default methods keep historical/pluggable
     * storage engines source-compatible until they opt in.
     */
    default long getCacheHits() {
        return -1L;
    }

    default long getCacheMisses() {
        return -1L;
    }

    default long getCacheEvictions() {
        return -1L;
    }

    default long getCachedEntryCount() {
        return -1L;
    }

    default boolean isCacheEnabled() {
        return getMaxCacheSize() > 0L;
    }

    long lastId();

    long nextId();

    void flush() throws Exception;

    void close() throws Exception;

    Class getUdf();

}
