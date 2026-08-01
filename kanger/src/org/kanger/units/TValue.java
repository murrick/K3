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

package org.kanger.units;

import org.kanger.Mind;
import org.kanger.enums.Enums;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.ByteBuffer;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Каноническая актуализация подстановки t-переменной в конкретном Mind.
 *
 * <p><strong>Архитектурная роль.</strong> {@code TValue} связывает одну
 * {@link TVariable} с одним конкретным {@link ITerm}. Объект представляет не
 * саму переменную и не терм-донор, а каноническую пару
 * {@code (TVariable, Term)}, принадлежащую транзакционной проекции Mind.</p>
 *
 * <p><strong>Inside.</strong> Собственная семантическая identity подстановки
 * определяется идентификаторами переменной и значения. Operational ID
 * {@link #getId()} идентифицирует зарегистрированный unit, но не заменяет
 * равенство пары, проверяемое {@link #equalsTo(TValue)}.</p>
 *
 * <p><strong>Outside.</strong> Текущая видимость, удаление, query projection,
 * hydration переменной и терма, а также отображение имени переменной зависят от
 * переданного или owning {@link Mind}. Эти отношения не изменяют identity
 * самой пары.</p>
 *
 * <p><strong>Canonicalization и lifecycle.</strong> Создание и повторное
 * получение подстановок принадлежат TValueFactory. Для одной видимой пары
 * {@code (TVariable, Term)} должен использоваться один канонический TValue;
 * удалённый объект может быть восстановлен и переиспользован, а не заменён
 * дубликатом. Пометка удаления не означает физического уничтожения.</p>
 *
 * <p><strong>Query semantics.</strong> {@link #setQuery(Mind)} публикует
 * существующую подстановку в query-local result projection. Операция не создаёт
 * новую логическую подстановку и не выполняет commit.</p>
 *
 * <p><strong>Persistence.</strong> {@link #pack()} и
 * {@link #apply(ByteBuffer)} сохраняют operational representation через ID
 * переменной и терма. Hydration объектов выполняется лениво через Mind;
 * serialized representation не является canonical identity.</p>
 *
 * <p><strong>Инварианты.</strong> TValue не является значением переменной без
 * контекста; {@code deleted != nonexistent}; query membership не является
 * identity; порядок {@link #compareTo(TValue)} не определяет семантическое
 * равенство; hash collision не создаёт эквивалентность.</p>
 */
public class TValue implements Comparable<TValue>, IUnit<TValue> {

    private static final long serialVersionUID = 196402070009L;

    private long id = -1;
    private long mindId = -1;
    private ITerm value = null;
    private TVariable tVar = null;

    private long valueId = -1;
    private long tVarId = -1;
    private Mind mind = null;

    public TValue() {
    }

    public TValue(TVariable var, ITerm val) {
        tVar = var;
        value = val;
        tVarId = tVar.getId();
        valueId = value.getId();
    }

    public TValue(Mind mind) {
        this.mind = mind;
    }

    public TValue(TVariable tv, ITerm t, IMind mind) {
        this.mind = (Mind) mind;
        this.tVar = tv;
        this.value = t;
        tVarId = tVar.getId();
        valueId = value.getId();
    }

    /**
     * Сериализует operational representation подстановки.
     *
     * @return маркированный пакет с unit ID, Mind ID, deletion flag и ссылками
     *         на терм и t-переменную
     */
    @Override
    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(mind) ? 1 : 0)
                .putLong(valueId)
                .putLong(tVarId);
        return packet.createMarked();
    }

    /**
     * Восстанавливает operational fields из serialized packet.
     * Связанные объекты остаются негидратированными до первого обращения.
     *
     * @param packet пакет persistent representation
     * @return этот объект
     * @throws OutOfBufferException если пакет неполон
     */
    @Override
    public TValue apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        valueId = packet.getLong();
        tVarId = packet.getLong();
        return this;
    }

    /**
     * Возвращает терм-донор, при необходимости гидратируя его через Mind.
     *
     * @param mind контекст hydration
     * @return терм, подставленный в переменную, либо {@code null}, если ссылка
     *         не задана
     * @throws Exception если терм нельзя разрешить
     */
    public ITerm getValue(Mind mind) throws Exception {
        if (value == null && valueId != -1) {
            value = mind.getTerms().get(valueId);
        }
        return value;
    }

    /**
     * Устанавливает уже канонический терм-донор и синхронизирует его ID.
     * Метод не регистрирует TValue в factory.
     *
     * @param value терм-донор
     */
    public void setValue(Term value) {
        this.value = value;
        valueId = value.getId();
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Возвращает переменную-акцептор, при необходимости гидратируя её через
     * Mind.
     *
     * @param mind контекст hydration
     * @return t-переменная подстановки
     * @throws Exception если переменную нельзя разрешить
     */
    public TVariable getTVar(Mind mind) throws Exception {
        if (tVar == null && tVarId != -1) {
            tVar = mind.getTVars().get(tVarId);
        }
        return tVar;
    }

    /**
     * Устанавливает уже каноническую переменную-акцептор и синхронизирует ID.
     *
     * @param tVar t-переменная
     */
    public void setTVar(TVariable tVar) {
        this.tVar = tVar;
        this.tVarId = tVar.getId();
    }

    /**
     * Формирует диагностическое представление подстановки в указанном Mind.
     * Формат зависит от debug options и не является persistence-протоколом.
     *
     * @param mind контекст разрешения имени и значения
     * @return диагностическое представление либо пустая строка при ошибке
     */
    public String toString(IMind mind) {
        try {
            return ((mind.getDebugLevel() & Enums.DEBUG_OPTION_VALUES) != 0
                    ? getTVar((Mind) mind).getVarName((Mind) mind) + "="
                    : "") + getValue((Mind) mind).toString();
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return "";
        }
    }

    /**
     * Добавляет эту подстановку в query-local projection текущего Mind.
     * Повторная публикация той же identity не создаёт дубликат в Set.
     *
     * @param mind Mind, владеющий результатом query
     * @throws Exception если переменную нельзя разрешить
     */
    public void setQuery(Mind mind) throws Exception {
        if (!mind.getQueryValues().containsKey(getTVar(mind))) {
            mind.getQueryValues().put(getTVar(mind), new HashSet<>());
        }
        mind.getQueryValues().get(getTVar(mind)).add(this);
    }

    @Override
    public int getHash() {
        int hash = 3;
        hash = 47 * hash + (int) (valueId ^ (valueId >>> 32));
        hash = 47 * hash + (int) (tVarId ^ (tVarId >>> 32));
        return hash;
    }

    /**
     * Проверяет семантическое равенство canonicalization key.
     *
     * @param to сравниваемая подстановка
     * @return {@code true}, если совпадают ID переменной и терма
     */
    @Override
    public boolean equalsTo(TValue to) {
        return to.getTVarId() == tVarId && to.getValueId() == valueId;
    }

    @Override
    public Mind getMind() {
        return mind;
    }

    @Override
    public TValue setMind(Mind mind) {
        this.mind = mind;
        return this;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (int) (id ^ (id >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof TValue && ((TValue) obj).getId() == id;
    }

    /**
     * Задаёт стабильный operational order: сначала по ID переменной, затем по
     * unit ID TValue. Порядок не является семантическим равенством пары.
     *
     * @param o сравниваемый TValue
     * @return результат сравнения
     */
    @Override
    public int compareTo(TValue o) {
        int variableOrder = Long.compare(tVarId, o.getTVarId());
        return variableOrder != 0 ? variableOrder : Long.compare(id, o.getId());
    }

    public long getValueId() {
        return valueId;
    }

    public long getTVarId() {
        return tVarId;
    }

    @Override
    public boolean isDeleted(IMind mind) {
        return ((Mind) mind).isUnitDeleted(this);
    }

    @Override
    public void setDeleted(boolean on, Mind mind) {
        mind.setUnitDeleted(this, on);
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.TVALUE;
    }

    @Override
    public long getMindId() {
        return mindId;
    }

    @Override
    public void setMindId(long mindId) {
        this.mindId = mindId;
    }

    /**
     * Проверяет, гидратирован ли терм-донор и соответствует ли он сохранённому
     * ID. Состояние {@code false} не означает отсутствие persistent unit.
     *
     * @return {@code true}, если терм уже разрешён
     */
    @Override
    public boolean isLoaded() {
        return value != null && valueId == value.getId();
    }

    @Override
    public Map<String, Object> createMap(IMind mind) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("mind_id", mindId);
        map.put("deleted", isDeleted(mind));
        map.put("value_id", valueId);
        map.put("tvar_id", tVarId);

        map.put("value", getValue((Mind) mind).getValue());
        map.put("tvar", getTVar((Mind) mind).createMap(mind));

        return map;
    }

    @Override
    public TValue applyMap(Map<String, Object> map) throws Exception {
        id = Long.parseLong(map.get("id") + "");
        mindId = Long.parseLong(map.get("mind_id") + "");
        boolean deleted = Boolean.parseBoolean(map.get("deleted") + "");
        if (deleted) {
            setDeleted(true, mind);
        }
        valueId = Long.parseLong(map.get("value_id") + "");
        tVarId = Long.parseLong(map.get("tvar_id") + "");
        value = null;
        tVar = null;
        return this;
    }
}
