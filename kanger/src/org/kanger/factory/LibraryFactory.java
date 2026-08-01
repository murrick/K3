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
import org.kanger.enums.LibMode;
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.IOperation;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.Operation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Copy-on-write реестр и транзакционный overlay операций {@link Operation},
 * видимых на одном уровне {@link Mind}.
 *
 * <p><strong>Представление и canonical signature.</strong> Operation хранит
 * schema ID, owning Mind ID, {@link LibMode}, имя, range, runtime reactor,
 * source scripts, parameter names и logical deletion state. Канонический title
 * задаётся строкой {@code name(range)}: её используют
 * {@link Operation#toString()}, hash и {@link #find(String)}. Поэтому
 * {@link #add(IOperation)} заменяет либо логически восстанавливает существующую
 * Operation с тем же title и ID, а не создаёт вторую активную identity до
 * физического {@link #pack()}. Mode, reactor, scripts и parameters являются
 * mutable implementation payload, а не дополнительными частями signature.</p>
 *
 * <p><strong>Владение и effective view.</strong> Каждый {@code Mind} создаёт
 * отдельную {@code LibraryFactory}. Child
 * {@code transaction(parentFactory)} строит {@link Escalera} overlay,
 * сохраняет ссылку на parent effective view, очищает local overrides,
 * dirty-update metadata и checkpoint journal, сбрасывает generation-local
 * anchor и не наследует storage connection. Чтение выполняется в порядке
 * local override, parent effective view, затем raw cache/root storage
 * hydration.</p>
 *
 * <p><strong>Child copy-on-write.</strong> Изменение унаследованной Operation
 * не мутирует parent object. Writable path создаёт child-owned copy с тем же
 * ID и копирует mode, name, range, reactor, scripts и parameters. Iterator
 * обходит raw cache identities, но разрешает каждый ID через effective view,
 * поэтому child наблюдает собственный payload, а parent до completion сохраняет
 * прежнее состояние.</p>
 *
 * <p><strong>Add и replacement.</strong> Для нового title фабрика связывает
 * incoming Operation с текущим Mind, выделяет library ID и добавляет её в
 * local cache. Для существующего title она снимает deletion mark и заменяет
 * mode, reactor, scripts и parameters, сохраняя Operation ID. Direct root
 * replacement существующего raw object отмечается в dirty updates. Если после
 * add scripts отсутствуют, effective Operation помечается logically deleted;
 * это реализованная deletion condition, а не утверждение об единственном
 * внешнем способе удаления.</p>
 *
 * <p><strong>Cross-factory invalidation.</strong> Replacement имеет
 * семантический side effect за пределами Library cache. Если существующая либо
 * incoming Operation имеет mode {@link LibMode#FUNCTION}, фабрика через
 * активный {@code Mind} просит {@link FValueFactory} invalidировать
 * materialized results соответствующего name/range. Unit-level deletion
 * Function Operation выполняет ту же делегацию. Binding-sensitive выбор
 * invalidated FValues принадлежит {@code FValueFactory} и здесь не
 * переопределяется.</p>
 *
 * <p><strong>Composite rollback boundary.</strong> Library replacement может
 * одновременно изменить Operation overlay и FValue invalidation metadata.
 * Локальные {@link #mark()}, {@link #commit()} и {@link #release()}
 * журналируют только Library cache, overrides и dirty IDs. Полный rollback
 * cross-factory side effect требует enclosing composite checkpoint
 * {@code Mind}, который отмечает и освобождает обе фабрики в согласованном
 * порядке. Library-local release сам по себе не является транзакцией всего
 * semantic effect.</p>
 *
 * <p><strong>Typed child completion.</strong> Typed
 * {@code commit(childFactory)} продвигает child cache chain, переводит новые
 * Operations в runtime-контекст parent Mind, копирует child overrides в
 * parent-owned objects и объединяет dirty IDs. Решение о commit/release и
 * sequencing с FValue completion принадлежат parent {@code Mind}; typed commit
 * не записывает existing-record replacements непосредственно в storage.</p>
 *
 * <p><strong>Nested Library checkpoints.</strong> Mark сохраняет Escalera
 * frame, глубокие копии active overrides и snapshot dirty IDs. No-argument
 * commit завершает cache frame и отбрасывает текущий snapshot; release
 * восстанавливает cache и обе auxiliary structures. Глубокая копия Operation
 * payload не позволяет последующей mutation writable object изменить saved
 * rollback state. Этот journal category-specific и не является общей
 * реализацией factory transactions.</p>
 *
 * <p><strong>Persistence.</strong> Только root factory при открытом storage
 * заимствует library {@link IBase} у {@link User}. Новые Operations проходят
 * обычный cache update. Existing root mutation сохраняет ID в dirty updates;
 * child-committed replacement сначала остаётся root override, затем root pack
 * materialize его в raw canonical Operation и также отмечает ID dirty.
 * {@link #update()} сохраняет cache changes, переписывает существующие steps и
 * очищает dirty metadata. Reactor reference и overlay metadata не являются
 * самостоятельными persistent records; persistent payload определяется
 * {@code Operation} serialization.</p>
 *
 * <p><strong>Physical cleanup.</strong> Root pack сначала применяет overrides
 * к raw canonical objects, затем физически удаляет Operations, остающиеся
 * logically deleted, и очищает соответствующие override/dirty entries. До
 * pack поиск по signature может найти deleted Operation, а add — восстановить
 * тот же canonical ID. Clear сбрасывает generation/checkpoint metadata и
 * заново создаёт child overlay либо root cache generation.</p>
 *
 * <p><strong>Concurrency boundary.</strong> Concurrent map/set и synchronized
 * add защищают отдельные metadata и replacement paths. Они не делают mutable
 * Operation payloads, reactors, effective iterator, FValue invalidation,
 * persistent update либо полный Mind transaction protocol независимо
 * thread-safe. Publication order, composite rollback и storage lifecycle
 * остаются обязанностью {@code Mind} и {@code User}.</p>
 *
 * <p><strong>Обязательства вызывающего кода.</strong> Доступ должен идти через
 * фабрику актуального {@code Mind}. Вызывающая сторона не должна мутировать
 * inherited Operation в обход copy-on-write path, считать runtime reactor
 * durable storage state, ожидать восстановления FValue invalidation от одного
 * Library-local release, смешивать typed child commit с checkpoint commit либо
 * обходить root pack/update при публикации existing-record replacement.</p>
 *
 * @see Operation
 * @see FValueFactory
 * @see LibMode
 */
public class LibraryFactory implements IFactory<IOperation> {
    public static final String SCHEMA = "library";

    private static final class OverlayState {
        private final Map<Long, Operation> overrides;
        private final Set<Long> dirtyUpdates;

        private OverlayState(Map<Long, Operation> overrides, Set<Long> dirtyUpdates) {
            this.overrides = overrides;
            this.dirtyUpdates = dirtyUpdates;
        }
    }

    private ICache cache;
    private IStep top = null;
    private IBase connection = null;

    private final Mind mind;
    private LibraryFactory parentView = null;
    private final Map<Long, Operation> overrides = new ConcurrentHashMap<>();
    private final Set<Long> dirtyUpdates = new CopyOnWriteArraySet<>();
    private final Stack<OverlayState> overlayStack = new Stack<>();

    public LibraryFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(LibraryFactory base) throws Exception {
        top = null;
        connection = null;
        parentView = base;
        overrides.clear();
        dirtyUpdates.clear();
        overlayStack.clear();
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }

        if (base != null) {
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
        }
    }

    private Operation copyOperation(Operation source, Mind owner) {
        Operation copy = new Operation(owner);
        copy.setId(source.getId());
        copy.setMindId(owner.getId());
        copy.setMode(source.getMode());
        copy.setName(source.getName());
        copy.setRange(source.getRange());
        copy.setProc(source.getProc());
        copy.getScripts().addAll(source.getScripts());
        copy.getParams().addAll(source.getParams());
        return copy;
    }

    private Map<Long, Operation> copyOverrides(Map<Long, Operation> source) {
        Map<Long, Operation> copy = new HashMap<>();
        for (Map.Entry<Long, Operation> entry : source.entrySet()) {
            copy.put(entry.getKey(), copyOperation(entry.getValue(), mind));
        }
        return copy;
    }

    private Operation rawGet(long id) throws Exception {
        Operation value = (Operation) cache.get(id);
        if (value == null && connection != null) {
            IStep step = connection.get(id);
            if (step != null) {
                value = (Operation) step.getData(mind);
            }
        }
        return value;
    }

    private Operation effective(long id) throws Exception {
        Operation value = overrides.get(id);
        if (value != null) {
            value.setMind(mind);
            return value;
        }
        if (parentView != null) {
            Operation inherited = parentView.effective(id);
            if (inherited != null) {
                return inherited;
            }
        }
        return rawGet(id);
    }

    private Operation writable(Operation source) {
        if (source.getMindId() == mind.getId() && source.getMind() == mind) {
            return source;
        }
        Operation copy = overrides.get(source.getId());
        if (copy == null) {
            Operation candidate = copyOperation(source, mind);
            Operation previous = ((ConcurrentHashMap<Long, Operation>) overrides)
                    .putIfAbsent(candidate.getId(), candidate);
            copy = previous == null ? candidate : previous;
        }
        return copy;
    }

    private void persistExisting(long id) throws Exception {
        if (connection == null) {
            return;
        }
        Operation value = rawGet(id);
        IStep step = connection.get(id);
        if (value != null && step != null) {
            step.setData(value);
            step.update();
        }
    }

    public void commit(LibraryFactory base) throws Exception {
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
        Map<Long, Operation> childOverrides = base.copyOverrides(base.overrides);
        for (Map.Entry<Long, Operation> entry : childOverrides.entrySet()) {
            overrides.put(entry.getKey(), copyOperation(entry.getValue(), mind));
        }
        dirtyUpdates.addAll(new HashSet<>(base.dirtyUpdates));
    }

    public void update() throws Exception {
        cache.update();
        if (connection != null) {
            for (long id : new HashSet<>(dirtyUpdates)) {
                persistExisting(id);
            }
        }
        dirtyUpdates.clear();
    }

    public synchronized IOperation add(IOperation s) throws Exception {
        Operation incoming = (Operation) s;
        Operation existing = find(s.toString());
        Operation target;
        if (existing != null) {
            if (existing.getMode() == LibMode.FUNCTION || s.getMode() == LibMode.FUNCTION) {
                mind.getFValues().invalidateUdf(existing.getName(), existing.getRange());
            }
            target = writable(existing);
            target.setDeleted(false, mind);
            target.setMode(s.getMode());
            target.setProc(incoming.getProc());
            target.getScripts().clear();
            target.getScripts().addAll(s.getScripts());
            target.getParams().clear();
            target.getParams().addAll(s.getParams());
            if (mind.getNext() == null && target == existing) {
                dirtyUpdates.add(target.getId());
            }
        } else {
            incoming.setMind(mind);
            incoming.setId(((User) mind.getUser()).nextId(SCHEMA));
            incoming.setMindId(mind.getId());
            cache.add(incoming);
            if (top == null) {
                top = cache.getRoot();
            }
            target = incoming;
        }
        if (target.getScripts().isEmpty()) {
            target.setDeleted(true, mind);
        }
        return target;
    }

    public Operation find(String title) throws Exception {
        for (long id : cache.find(title.hashCode())) {
            Operation one = get(id);
            if (one != null && one.toString().equals(title)) {
                return one;
            }
        }
        return null;
    }

    public Operation get(long id) throws Exception {
        return effective(id);
    }

    private void applyOverrides() throws Exception {
        if (mind.getNext() != null || overrides.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, Operation> entry : new HashMap<>(overrides).entrySet()) {
            Operation raw = rawGet(entry.getKey());
            Operation source = entry.getValue();
            if (raw != null) {
                raw.setMind(mind);
                raw.setMindId(mind.getId());
                raw.setMode(source.getMode());
                raw.setName(source.getName());
                raw.setRange(source.getRange());
                raw.setProc(source.getProc());
                raw.getScripts().clear();
                raw.getScripts().addAll(source.getScripts());
                raw.getParams().clear();
                raw.getParams().addAll(source.getParams());
                dirtyUpdates.add(raw.getId());
            }
        }
        overrides.clear();
    }

    public void pack() throws Exception {
        applyOverrides();
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted(mind)) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            overrides.remove(((IUnit) o).getId());
            dirtyUpdates.remove(((IUnit) o).getId());
            cache.delete(((IUnit) o).getId());
        }
    }

    public void clear() throws Exception {
        overrides.clear();
        dirtyUpdates.clear();
        overlayStack.clear();
        if (mind.getNext() != null) {
            transaction((LibraryFactory) mind.getNext().getLibrary());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public int size() {
        return cache.size();
    }

    @Override
    public Iterator iterator() {
        final Iterator raw = cache.iterator();
        return new Iterator() {
            @Override
            public boolean hasNext() {
                return raw.hasNext();
            }

            @Override
            public Object next() {
                Object value = raw.next();
                try {
                    return get(((IUnit) value).getId());
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                }
            }

            @Override
            public void remove() {
                raw.remove();
            }
        };
    }

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    public boolean isEmpty() {
        return cache == null || cache.isEmpty();
    }

    public void mark() throws Exception {
        cache.mark();
        overlayStack.push(new OverlayState(copyOverrides(overrides), new HashSet<>(dirtyUpdates)));
    }

    public void commit() throws Exception {
        cache.commit();
        if (!overlayStack.isEmpty()) {
            overlayStack.pop();
        }
    }

    public void release() throws Exception {
        cache.release();
        if (!overlayStack.isEmpty()) {
            OverlayState state = overlayStack.pop();
            overrides.clear();
            overrides.putAll(state.overrides);
            dirtyUpdates.clear();
            dirtyUpdates.addAll(state.dirtyUpdates);
        }
    }
}
