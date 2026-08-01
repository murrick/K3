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

package org.kanger.interfaces.internal;

import org.kanger.Mind;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.storage.ByteBuffer;

/**
 * Persistent chain node, связывающий сериализованное представление storage с
 * гидратированной semantic unit.
 *
 * <p><strong>Архитектурная роль.</strong> {@code IStep} является operational
 * node физической linked representation. Он несёт persistent ID, hash, размер,
 * ссылку {@code next} и optional hydrated data. Step не является canonical
 * semantic identity: одна и та же сущность может существовать как unit до
 * materialization и как hydrated data после чтения.</p>
 *
 * <p><strong>Serialization.</strong> {@link #pack()} создаёт физический packet,
 * а {@link #apply(ByteBuffer)} восстанавливает состояние step из packet.
 * Формат, base-code packing и recovery checks принадлежат storage-реализации.</p>
 *
 * <p><strong>Hydration.</strong> {@link #getData(Mind)} может использовать Mind
 * для разрешения ссылок и canonicalization. Безаргументный {@link #getData()}
 * возвращает уже прикреплённое представление и не обязан выполнять hydration.</p>
 *
 * <p><strong>Persistence lifecycle.</strong> {@link #append()} публикует новый
 * node, {@link #update()} изменяет существующее physical representation.
 * Вызывающий обязан обеспечить корректный owner/base, ID и chain state.</p>
 *
 * <p><strong>Инварианты.</strong> Persistent ID не задаёт порядок обхода;
 * порядок определяется {@link #getNext()}. Hash является индексным признаком,
 * но не доказательством semantic equality. Size описывает physical footprint,
 * а не логическую сложность unit.</p>
 *
 * @see IUnit
 * @see IBase
 */
public interface IStep {

    /**
     * Сериализует текущее physical состояние step.
     *
     * @return packet, пригодный для storage layer
     */
    ByteBuffer pack();

    /**
     * Восстанавливает step из packet.
     *
     * @param packet источник serialized state
     * @return применённый step, обычно текущий экземпляр
     * @throws OutOfBufferException если packet неполон
     * @throws RuntimeErrorException если serialized state нарушает runtime contract
     * @throws Exception при иных ошибках decoding или validation
     */
    IStep apply(ByteBuffer packet) throws OutOfBufferException, RuntimeErrorException, Exception;

    /**
     * Возвращает или гидратирует semantic data в контексте Mind.
     *
     * @param mind контекст canonicalization и разрешения ссылок
     * @return hydrated semantic object или {@code null} согласно реализации
     * @throws Exception если hydration не удалась
     */
    Object getData(Mind mind) throws Exception;

    /**
     * Возвращает уже прикреплённые данные без гарантии hydration.
     *
     * @return attached data или {@code null}
     */
    Object getData();

    /**
     * Прикрепляет hydrated/materialized data к step.
     *
     * @param data semantic object или {@code null}
     */
    void setData(Object data);

    /** @return следующий node persistent chain или {@code null} */
    IStep getNext();

    /**
     * Устанавливает следующий node linked representation.
     *
     * @param next следующий step или {@code null}
     */
    void setNext(IStep next);

    /** @return operational persistent ID */
    long getId();

    /**
     * Устанавливает operational persistent ID.
     *
     * @param id ID внутри schema-local allocation domain
     */
    void setId(long id);

    /** @return stored/index hash semantic representation */
    int getHash();

    /**
     * Устанавливает индексный hash; значение не заменяет semantic equality.
     *
     * @param hash hash representation
     */
    void setHash(int hash);

    /**
     * Обновляет существующее physical representation step.
     *
     * @throws Exception если persistence update не удался
     */
    void update() throws Exception;

    /**
     * Добавляет новый step в persistent representation.
     *
     * @throws Exception если append или chain publication не удались
     */
    void append() throws Exception;

//    IBase getBase();
//
//    void setBase(IBase base);

//    void delete() throws IOException;

    /** @return physical serialized size или implementation estimate */
    long getSize();

    /**
     * Устанавливает physical size metadata.
     *
     * @param sz размер в единицах storage implementation
     */
    void setSize(long sz);

}
