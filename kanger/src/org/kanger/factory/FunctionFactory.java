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

package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.FunctionBinding;
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.storage.Escalera;
import org.kanger.units.Function;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Реестр и транзакционный overlay скомпилированных {@link Function},
 * принадлежащих одному уровню {@link Mind}.
 *
 * <p><strong>Представление и роль.</strong> Фабрика создаёт отдельное Function
 * occurrence, связывающее schema ID, owning Mind, name Term, range,
 * {@link FunctionBinding}, argument structure и зарезервированный result slot
 * с индексом {@code range}. {@link #add(ITerm, ArgumentsList,
 * FunctionBinding)} всегда выделяет новую Function identity; structural
 * comparison реализуется на unit level и не является factory-level
 * canonicalization или дедупликацией.</p>
 *
 * <p><strong>Binding contract.</strong> Binding mode сохраняется вместе с
 * Function и участвует в hashing/structural comparison. {@code LEGACY_AUTO}
 * поддерживает исторический infrastructure-first, UDF-second resolution;
 * {@code INFRASTRUCTURE} ограничивает dispatch инфраструктурным registry;
 * {@code UDF_DYNAMIC} разрешает актуальную пользовательскую operation по
 * signature при каждом вызове. Перегрузка без binding использует
 * {@code LEGACY_AUTO}; старые records без сохранённого binding также hydrate в
 * этот compatibility mode.</p>
 *
 * <p><strong>Владение и публикация.</strong> Каждый {@code Mind} создаёт
 * отдельную {@code FunctionFactory}. Child
 * {@code transaction(parentFactory)} строит {@link Escalera} overlay над
 * parent cache, сбрасывает generation-local chain anchor и не наследует
 * storage connection. До typed parent commit созданные Functions принадлежат
 * child Mind. Typed {@code commit(childFactory)} продвигает cache chain и
 * переводит promoted units в runtime-контекст родителя.</p>
 *
 * <p><strong>Add-time side effect.</strong> Добавление не является только
 * инертной регистрацией. После публикации новой Function в local cache фабрика
 * вызывает Calculator, когда {@link Function#isCalculable()} возвращает
 * {@code false}. Документация сохраняет историческое значение этого predicate
 * и не переименовывает его: в текущей реализации оно выводится из наличия
 * TVariables в argument structure.</p>
 *
 * <p><strong>Checkpoint protocol.</strong> No-argument {@link #mark()},
 * {@link #commit()} и {@link #release()} относятся к вложенным
 * cache/composite checkpoints и делегируют их {@code Escalera}. Фабрика не
 * владеет отдельным auxiliary journal. Эти операции не следует смешивать с
 * typed child commit, решение и sequencing которого принадлежат parent
 * {@code Mind}.</p>
 *
 * <p><strong>Persistence.</strong> Только root factory при открытом storage
 * заимствует schema-specific {@link IBase} у {@link User}. Root cache miss
 * может materialize Function через эту базу, а update передаёт cache changes
 * storage-модулю. Child overlays остаются memory-only и не владеют connection,
 * storage generation или close authority.</p>
 *
 * <p><strong>Очистка.</strong> Child {@link #clear()} заново строит overlay над
 * текущей parent factory; root clear сбрасывает текущую generation.
 * {@link #pack()} физически удаляет Functions, остающиеся logically deleted.
 * Unit-level deletion Function может также пометить найденный {@link
 * FValueFactory} result deleted через активный Mind, но этот cascade не
 * превращает pack фабрики в общий механизм invalidation результатов.</p>
 *
 * <p><strong>Инварианты и concurrency.</strong> Function schema identity,
 * binding metadata, structural identity и materialized {@code FValue} являются
 * различными lifecycle dimensions. Локальная синхронизация add защищает
 * creation path, но не делает argument objects, Calculator execution,
 * iterators, FValue state или полный transaction protocol независимо
 * thread-safe.</p>
 *
 * <p><strong>Обязательства вызывающего кода.</strong> Function следует
 * создавать и получать через фабрику актуального {@code Mind}. Вызывающая
 * сторона должна учитывать add-time calculation, не ожидать несуществующей
 * factory deduplication, не публиковать child Function до parent completion и
 * не трактовать binding как transient delivery preference.</p>
 *
 * @see FValueFactory
 * @see Function
 * @see FunctionBinding
 */
public class FunctionFactory implements IFactory<Function> {

    public static final String SCHEMA = "functions";

    private ICache cache;
    private IStep top = null;
    private IBase connection = null;

    private final Mind mind;

    public FunctionFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(FunctionFactory base) throws Exception {
        top = null;
        connection = null;
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }
        if (base != null) {
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
        }
    }

    public void commit(FunctionFactory base) throws Exception {
        if (top == null) {
            top = base.top;
        } else if (base.top != null) {
            base.top.setNext(cache.getRoot());
        }
        cache.setRoot(base.cache.getRoot());
        for (Object s : cache) {
            if (((IUnit) s).getMindId() == base.mind.getId()) {
                ((IUnit) s).setMind(mind);
                ((IUnit) s).setMindId(mind.getId());
            }
        }
    }

    public void update() throws Exception {
        cache.update();
    }

    public synchronized Function add(ITerm name, ArgumentsList arguments) throws Exception {
        return add(name, arguments, FunctionBinding.LEGACY_AUTO);
    }

    public synchronized Function add(ITerm name, ArgumentsList arguments, FunctionBinding binding) throws Exception {
        Function f = new Function(mind);
        f.setName(name);
        f.setRange(arguments.size());
        f.setBinding(binding);
        f.getArguments().clear();
        f.getArguments().addAll(arguments);
        f.getArguments().add(new Argument());
        f.setId(((User) mind.getUser()).nextId(SCHEMA));
        f.setMindId(mind.getId());
        cache.add(f);
        if (top == null) {
            top = cache.getRoot();
        }
        if (!f.isCalculable()) {
            mind.getCalculator().calculate(f, false);
        }
        return f;
    }

    public Function get(long id) throws Exception {
        Function t = (Function) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (Function) s.getData(mind);
            }
        }
        return t;
    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction(((Mind) mind.getNext()).getFunctions());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public void mark() throws Exception {
        cache.mark();
    }

    public void commit() throws Exception {
        cache.commit();
    }

    public void release() throws Exception {
        cache.release();
    }

    public int size() throws Exception {
        return cache.size();
    }

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted(mind)) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
        }
    }

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    public boolean isEmpty() {
        return cache == null || cache.isEmpty();
    }
}
