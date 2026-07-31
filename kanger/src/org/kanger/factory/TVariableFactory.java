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
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Реестр и транзакционный overlay логических {@link TVariable}, принадлежащих
 * одному уровню {@link Mind}.
 *
 * <p><strong>Представление и роль.</strong> Фабрика создаёт variable units,
 * связывающие schema identity, owning Rule, name Term и отдельный variable
 * index, выделяемый dictionary/term subsystem. {@link #createTVar(Rule, ITerm)}
 * всегда создаёт новую логическую переменную; класс не выполняет
 * semantic-deduplication по имени или Rule и не должен описываться как lookup
 * canonicalizer аналогично {@link TValueFactory}.</p>
 *
 * <p><strong>Владение и публикация.</strong> Каждый {@code Mind} создаёт
 * собственную {@code TVariableFactory}. Child
 * {@code transaction(parentFactory)} строит {@link Escalera} overlay над
 * parent cache, сбрасывает generation-local chain anchor и не наследует
 * storage connection. До typed parent commit child-created variables
 * принадлежат дочернему уровню.</p>
 *
 * <p><strong>Завершение транзакции.</strong> Typed
 * {@code commit(childFactory)} продвигает child cache chain и переводит
 * созданные на дочернем уровне variables в runtime-контекст родительского
 * {@code Mind}. Решение о принятии или отклонении всего child уровня, порядок
 * factory completion и transaction reservation принадлежат {@code Mind}, а не
 * этой фабрике.</p>
 *
 * <p><strong>Checkpoint protocol.</strong> No-argument {@link #mark()},
 * {@link #commit()} и {@link #release()} относятся к вложенным cache/composite
 * checkpoints и делегируют их {@code Escalera}. У фабрики нет отдельного
 * auxiliary checkpoint journal; это не означает, что typed child commit и
 * no-argument checkpoint commit являются одной операцией.</p>
 *
 * <p><strong>Persistence.</strong> Только root factory при открытом storage
 * заимствует schema-specific {@link IBase} у {@link User}. Cache miss может
 * materialize переменную через эту базу, а root update передаёт cache changes
 * storage-модулю. Child overlay получает видимость через parent cache и не
 * владеет connection, storage generation или close authority.</p>
 *
 * <p><strong>Очистка.</strong> Child {@link #clear()} заново создаёт overlay
 * над текущей parent factory; root clear сбрасывает текущую generation.
 * {@link #pack()} физически удаляет variables, остающиеся logically deleted, и
 * одновременно удаляет их как keys из transient binding projection
 * {@link TValueFactory#getCurrent()}. Эта cross-factory очистка не позволяет
 * runtime binding ссылаться на уже удалённую logical variable.</p>
 *
 * <p><strong>Инварианты и concurrency.</strong> Schema ID, variable index,
 * owning Rule, name и owning Mind ID являются различными частями identity и
 * lifecycle; variable index нельзя трактовать как persistent schema ID.
 * Синхронизация {@code createTVar} защищает локальный creation path, но не
 * делает mutable variables, iterator, связанную value factory или полный
 * transaction protocol независимо thread-safe.</p>
 *
 * <p><strong>Обязательства вызывающего кода.</strong> Переменные следует
 * создавать и получать через фабрику актуального {@code Mind}. Вызывающая
 * сторона не должна ожидать несуществующей дедупликации по имени, публиковать
 * child variable до parent completion либо сохранять active TValue binding
 * после физического удаления соответствующей переменной.</p>
 *
 * @see TValueFactory
 * @see TVariable
 * @see Rule
 */
public class TVariableFactory implements IFactory<TVariable> {

    public static final String SCHEMA = "tvariables";

    private ICache cache;
    private IStep top = null;
    private IBase connection = null;

    private final Mind mind;

    public TVariableFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(TVariableFactory base) throws Exception {
        // A factory instance is reused across close/clear/use transitions.
        // Anchors from the previous cache must never retain or address it.
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

    public void commit(TVariableFactory base) throws Exception {
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
        if (cache.update()) {
        }
    }

    public synchronized TVariable createTVar(Rule r, ITerm name) throws Exception {
        TVariable p = new TVariable(mind);
        p.setId(((User) mind.getUser()).nextId(SCHEMA));
        p.setMindId(mind.getId());
        p.setIndex(mind.getTerms().nextVarIndex());
        p.setRule(r);
        p.setName(name);
        cache.add(p);
        if (top == null) {
            top = cache.getRoot();
        }
        return p;
    }

    public TVariable get(long id) throws Exception {
        TVariable t = (TVariable) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (TVariable) s.getData(mind);
            }
        }
        return t;
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
            mind.getTValues().getCurrent().remove(o);
        }
    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction(((Mind) mind.getNext()).getTVars());
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
        return cache.iterator(-1);
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
