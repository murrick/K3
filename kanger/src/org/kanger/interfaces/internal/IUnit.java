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
import org.kanger.enums.UnitType;
import org.kanger.interfaces.IMind;
import org.kanger.storage.ByteBuffer;

import java.util.Map;

/**
 * Внутренний общий контракт канонической semantic unit KANGER.
 *
 * <p><strong>Architectural essence.</strong> Unit имеет logical type,
 * operational ID и canonical equality/hash contract. Persistent ID, owning
 * Mind и serialized representation являются разными координатами одной
 * сущности и не должны смешиваться.</p>
 *
 * <p><strong>Mind projection.</strong> {@link #getMindId()} и
 * {@link #setMind(Mind)} связывают unit с контекстом владения/materialization.
 * Удаление определяется относительно переданного {@link IMind}; оно является
 * transaction-visible состоянием, а не уничтожением logical identity.</p>
 *
 * <p><strong>Canonicalization.</strong> {@link #getHash()} служит candidate
 * lookup, а окончательная проверка выполняется {@link #equalsTo(Object)}.
 * Совпадение hash не доказывает identity.</p>
 *
 * <p><strong>Serialization и внешняя проекция.</strong> ByteBuffer и Map — два
 * представления unit для storage и внешнего обмена. Ни одно из них само по себе
 * не определяет canonical identity. Реализация обязана восстанавливать
 * совместимый logical type и ссылки.</p>
 *
 * <p><strong>Lifecycle.</strong> {@link #isLoaded()} сообщает о состоянии
 * materialization/hydration, а не о существовании знания. Caller обязан
 * использовать factory/Mind lifecycle для публикации, rollback и persistence.</p>
 *
 * @param <T> конкретный semantic type, возвращаемый fluent/apply операциями
 * @see ICache
 * @see IStep
 */
public interface IUnit<T> {

    /** @return operational ID unit в её allocation domain */
    long getId();

    /**
     * Устанавливает operational ID; метод не выполняет регистрацию в factory.
     *
     * @param id новый ID
     */
    void setId(long id);

    /** @return ID Mind, с которым связана текущая проекция unit */
    long getMindId();

    /**
     * Устанавливает сохранённый Mind ID без полного lifecycle перехода.
     *
     * @param id Mind ID
     */
    void setMindId(long id);

    /**
     * Вычисляет canonical candidate hash.
     *
     * @return hash logical representation
     * @throws Exception если зависимые значения не могут быть разрешены
     */
    int getHash() throws Exception;

    /**
     * Проверяет semantic equality с объектом того же logical kind.
     *
     * @param to сравниваемая unit
     * @return {@code true}, если canonical content эквивалентен
     * @throws Exception если сравнение требует недоступной hydration
     */
    boolean equalsTo(T to) throws Exception;

    /** @return owning/materialization Mind или {@code null} */
    Mind getMind();

    /**
     * Связывает unit с Mind и выполняет implementation-specific relocation или
     * canonicalization.
     *
     * @param mind новый контекст
     * @return unit, соответствующая этому контексту
     * @throws Exception если перенос или разрешение ссылок не удались
     */
    T setMind(Mind mind) throws Exception;

    /**
     * Проверяет transaction-visible deletion относительно Mind.
     *
     * @param mind наблюдающий контекст
     * @return {@code true}, если unit скрыта как deleted в этом контексте
     */
    boolean isDeleted(IMind mind);

    /**
     * Изменяет deletion projection в указанном Mind.
     *
     * <p>Операция не уничтожает canonical identity и не гарантирует немедленное
     * physical deletion.</p>
     *
     * @param on требуемое состояние deletion marker
     * @param mind контекст изменения
     * @throws Exception если overlay/lifecycle update не удался
     */
    void setDeleted(boolean on, Mind mind) throws Exception;

    /**
     * Сериализует unit в storage representation.
     *
     * @return packet текущей physical projection
     */
    ByteBuffer pack();

    /**
     * Восстанавливает unit из storage packet.
     *
     * @param packet serialized representation
     * @return восстановленная unit, обычно текущий экземпляр
     * @throws Exception если decoding, validation или link restoration не удались
     */
    T apply(ByteBuffer packet) throws Exception;

    /** @return stable discriminator logical unit kind */
    UnitType getUnitType();

    /**
     * Сообщает, материализовано ли необходимое содержимое unit.
     *
     * @return {@code true}, если implementation считает unit загруженной
     */
    boolean isLoaded();

    /**
     * Создаёт внешнюю map-проекцию в контексте Mind.
     *
     * @param mind контекст видимости и представления
     * @return mutable или immutable map согласно реализации
     * @throws Exception если проекция не может быть построена
     */
    Map<String, Object> createMap(IMind mind) throws Exception;

    /**
     * Восстанавливает внешнюю map-проекцию в semantic unit.
     *
     * @param map входное представление
     * @return применённая/восстановленная unit
     * @throws Exception если поля неполны, несовместимы или не разрешаются
     */
    T applyMap(Map<String, Object> map) throws Exception;

}
