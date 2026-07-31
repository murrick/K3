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
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Канонический реестр, транзакционный overlay и runtime-проекция значений
 * {@link TValue}, видимых на одном уровне {@link Mind}.
 *
 * <p><strong>Представление и роль.</strong> Фабрика объединяет несколько
 * самостоятельных lifecycle dimensions. {@link Escalera} хранит canonical
 * TValue identities и их transaction visibility; {@code current} хранит
 * transient active binding для каждой {@link TVariable}; layered ID-index
 * ускоряет обход values одной переменной; {@code action} фиксирует локальный
 * mutation/effect state; delta stacks обеспечивают nested checkpoint rollback.
 * Эти структуры не являются взаимозаменяемыми представлениями одного map.</p>
 *
 * <p><strong>Владение и публикация.</strong> Каждый {@code Mind} создаёт
 * отдельную {@code TValueFactory}. Child
 * {@code transaction(parentFactory)} строит cache overlay, связывает index с
 * parent layer, очищает active bindings, action и checkpoint journals и не
 * получает storage connection. Typed {@code commit(childFactory)} продвигает
 * child cache, переводит promoted units в parent {@code Mind}, объединяет
 * layered index и распространяет action state. Решение и sequencing всей
 * транзакции остаются ответственностью {@code Mind}.</p>
 *
 * <p><strong>Canonical identity и resurrection.</strong> Canonical key
 * задаётся парой {@code (TVariable identity, ITerm value identity)}.
 * {@link #add(TVariable, ITerm)} сначала вызывает
 * {@link #find(TVariable, ITerm)}. Lookup намеренно не исключает logically
 * deleted object: до физического {@link #pack()} повторное добавление той же
 * пары должно вернуть тот же {@code TValue} и снять deletion mark, а не создать
 * duplicate identity. Logical deletion является обратимым transaction state;
 * pack задаёт physical canonical removal boundary.</p>
 *
 * <p><strong>Active binding projection.</strong> {@code current} отвечает на
 * runtime-вопрос, какое значение сейчас выбрано для конкретной переменной.
 * {@link #get(TVariable)}, {@link #set(TVariable, TValue)} и
 * {@link #isEmpty(TVariable)} работают с этой transient projection, а не с
 * canonical registry или persistent records. Она очищается при новой
 * transaction/generation initialization и не определяется typed commit как
 * durable child-to-parent map. Pack удаляет stale bindings, чьи values больше
 * не существуют; {@link TVariableFactory#pack()} отдельно удаляет keys
 * физически удалённых variables.</p>
 *
 * <p><strong>Layered variable index.</strong> {@code parentIndex} и
 * {@code localByVariable} хранят только TValue IDs; semantic objects остаются
 * в cache/storage и materialize через {@link #get(long)}. Lazy root indexing
 * разворачивает newest-first iteration Escalera, чтобы сохранить исторический
 * oldest-first substitution order. Child collection обходит parent layers до
 * local IDs. Этот порядок наблюдаем и отличается как от canonical identity,
 * так и от full-range Comparable ordering самого {@code TValue}.</p>
 *
 * <p><strong>Checkpoint protocol.</strong> No-argument {@link #mark()},
 * {@link #commit()} и {@link #release()} журналируют не полный index snapshot,
 * а только values, добавленные после mark, плюс предыдущее action state. Nested
 * commit включает inner additions во внешний delta; release откатывает cache,
 * unindex-ит additions в обратном порядке и восстанавливает action. Это
 * category-specific auxiliary rollback, необходимый для согласованности cache
 * и acceleration metadata.</p>
 *
 * <p><strong>Persistence и очистка.</strong> Только root factory при открытом
 * storage заимствует schema-specific {@link IBase} у {@link User}; child
 * overlays остаются memory-only. {@link #pack()} физически удаляет values,
 * которые остаются logically deleted либо ссылаются на Term, не используемый
 * активным Rule, synchronously unindex-ит их, очищает stale current bindings и
 * переустанавливает generation-local chain anchor. Метод не меняет pre-pack
 * canonical resurrection contract.</p>
 *
 * <p><strong>Ordering и concurrency.</strong> {@code TValue.compareTo}
 * задаёт lexicographic order {@code (tVarId, TValue id)} по полному диапазону
 * {@code long}; factory per-variable traversal сохраняет иной — исторический
 * substitution order. {@code indexLock} и synchronized add защищают отдельные
 * metadata/creation paths, но не делают mutable values, reactors, iterator или
 * composite transaction protocol независимо thread-safe. Parent publication
 * и lock ordering остаются обязанностью {@code Mind}.</p>
 *
 * <p><strong>Обязательства вызывающего кода.</strong> Доступ должен идти через
 * фабрику актуального {@code Mind}. Вызывающая сторона не должна фильтровать
 * deleted candidates внутри canonical lookup, трактовать {@code current} как
 * persistent truth, изменять layered index напрямую, смешивать typed commit с
 * checkpoint completion либо предполагать произвольный порядок substitution
 * values.</p>
 *
 * @see TVariableFactory
 * @see TValue
 * @see TVariable
 */
public class TValueFactory implements IFactory<TValue> {

    public static final String SCHEMA = "tvalues";

    private ICache cache;
    private final Mind mind;
    private IStep top = null;
    private IBase connection = null;
    private final Map<TVariable, TValue> current = new HashMap<>();
    private boolean action = false;

    /**
     * Transaction-layered acceleration metadata. Buckets contain TValue IDs
     * only; values are hydrated through Escalera/IBase on demand.
     */
    private TValueFactory parentIndex = null;
    private final Map<Long, LinkedHashSet<Long>> localByVariable = new HashMap<>();
    private final Object indexLock = new Object();

    /**
     * Nested Linker marks are extremely frequent. Recording only values added
     * since a mark keeps rollback proportional to the delta instead of copying
     * the complete index for every candidate pair.
     */
    private final Stack<List<TValue>> additionsStack = new Stack<>();
    private final Stack<Boolean> actionStack = new Stack<>();
    private boolean indexInitialized = false;

    public TValueFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(TValueFactory base) throws Exception {
        top = null;
        connection = null;
        action = false;
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }

        current.clear();
        if (base != null) {
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
        }

        parentIndex = base;
        synchronized (indexLock) {
            localByVariable.clear();
            additionsStack.clear();
            actionStack.clear();
            indexInitialized = base != null;
        }
    }

    private void indexLocked(TValue value) {
        if (value == null) {
            return;
        }
        LinkedHashSet<Long> ids = localByVariable.get(value.getTVarId());
        if (ids == null) {
            ids = new LinkedHashSet<>();
            localByVariable.put(value.getTVarId(), ids);
        }
        ids.add(value.getId());
    }

    private void index(TValue value) {
        synchronized (indexLock) {
            indexLocked(value);
        }
    }

    private void unindex(TValue value) {
        synchronized (indexLock) {
            if (value == null || !indexInitialized) {
                return;
            }
            LinkedHashSet<Long> ids = localByVariable.get(value.getTVarId());
            if (ids != null) {
                ids.remove(value.getId());
                if (ids.isEmpty()) {
                    localByVariable.remove(value.getTVarId());
                }
            }
        }
    }

    private void ensureIndex() throws Exception {
        synchronized (indexLock) {
            if (indexInitialized) {
                return;
            }
            localByVariable.clear();

            // Escalera iterates newest first, while the historical forward()
            // traversal delivered oldest values first. Reversing here preserves
            // the observable substitution order.
            List<TValue> values = new ArrayList<>();
            for (Object value : cache) {
                values.add((TValue) value);
            }
            for (int i = values.size() - 1; i >= 0; --i) {
                indexLocked(values.get(i));
            }
            indexInitialized = true;
        }
    }

    private Map<Long, LinkedHashSet<Long>> snapshotIndex() throws Exception {
        ensureIndex();
        synchronized (indexLock) {
            Map<Long, LinkedHashSet<Long>> snapshot = new HashMap<>();
            for (Map.Entry<Long, LinkedHashSet<Long>> entry : localByVariable.entrySet()) {
                snapshot.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            }
            return snapshot;
        }
    }

    private void collectIds(long variableId, LinkedHashSet<Long> result) throws Exception {
        if (parentIndex != null) {
            parentIndex.collectIds(variableId, result);
        }
        ensureIndex();
        synchronized (indexLock) {
            LinkedHashSet<Long> local = localByVariable.get(variableId);
            if (local != null) {
                result.addAll(new LinkedHashSet<>(local));
            }
        }
    }

    private void mergeIndex(TValueFactory child) throws Exception {
        Map<Long, LinkedHashSet<Long>> childSnapshot = child.snapshotIndex();
        ensureIndex();
        synchronized (indexLock) {
            for (Map.Entry<Long, LinkedHashSet<Long>> entry : childSnapshot.entrySet()) {
                LinkedHashSet<Long> ids = localByVariable.get(entry.getKey());
                if (ids == null) {
                    ids = new LinkedHashSet<>();
                    localByVariable.put(entry.getKey(), ids);
                }
                ids.addAll(entry.getValue());
            }
        }
    }

    public void commit(TValueFactory base) throws Exception {
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
        mergeIndex(base);
        action = action || base.isAction();
    }

    public void update() throws Exception {
        if (cache.update()) {
        }
    }

    public synchronized TValue add(TVariable tv, ITerm o) throws Exception {
        TValue t = find(tv, o);
        if (t == null) {
            t = new TValue(tv, o, mind);
            t.setTVar(tv);
            t.setId(((User) mind.getUser()).nextId(SCHEMA));
            t.setMindId(mind.getId());
            cache.add(t);
            index(t);
            synchronized (indexLock) {
                if (!additionsStack.isEmpty()) {
                    additionsStack.peek().add(t);
                }
            }
            if (top == null) {
                top = cache.getRoot();
            }
            action = true;
            tv.incFloodControl(o);
        } else {
            t.setDeleted(false, mind);
        }
        return t;
    }

    public TValue get(TVariable tv) {
        if (isEmpty(tv)) {
            return null;
        }
        return current.get(tv);
    }

    public boolean isEmpty(TVariable tv) {
        return !current.containsKey(tv);
    }

    public TValue find(TVariable tv, ITerm v) throws Exception {
        TValue temp = new TValue(tv, v);
        for (long id : cache.find(temp.getHash())) {
            IUnit one = get(id);
            // Intentionally no deleted filter: canonical resurrection reuses the
            // existing TValue identity and clears its transaction deletion mark.
            if (one.equalsTo(temp)) {
                return (TValue) one;
            }
        }
        return null;
    }

    public TValue get(long id) throws Exception {
        TValue t = (TValue) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (TValue) s.getData(mind);
            }
        }
        return t;
    }

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted(mind)) {
                toDelete.add(o);
            } else {
                boolean found = mind.getRules().hasActiveRuleWithTerm(
                        ((TValue) o).getValue(mind).getId());
                if (!found) {
                    toDelete.add(o);
                }
            }
        }
        for (Object o : toDelete) {
            unindex((TValue) o);
            cache.delete(((IUnit) o).getId());
        }
        Iterator<Map.Entry<TVariable, TValue>> iterator = current.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!cache.containsKey(iterator.next().getValue().getId())) {
                iterator.remove();
            }
        }
        top = cache.getRoot();
    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction(((Mind) mind.getNext()).getTValues());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public long mark() throws Exception {
        synchronized (indexLock) {
            additionsStack.push(new ArrayList<TValue>());
            actionStack.push(action);
        }
        return cache.mark();
    }

    public long commit() throws Exception {
        long result = cache.commit();
        synchronized (indexLock) {
            if (!additionsStack.isEmpty()) {
                List<TValue> additions = additionsStack.pop();
                if (!additionsStack.isEmpty()) {
                    additionsStack.peek().addAll(additions);
                }
            }
            if (!actionStack.isEmpty()) {
                actionStack.pop();
            }
        }
        return result;
    }

    public long release() throws Exception {
        long result = cache.release();
        List<TValue> additions = null;
        synchronized (indexLock) {
            if (!additionsStack.isEmpty()) {
                additions = additionsStack.pop();
            }
            if (!actionStack.isEmpty()) {
                action = actionStack.pop();
            }
        }
        if (additions != null) {
            for (int i = additions.size() - 1; i >= 0; --i) {
                unindex(additions.get(i));
            }
        }
        return result;
    }

    public TValue set(TVariable tv, TValue v) {
        if (v == null) {
            current.remove(tv);
        } else {
            current.put(tv, v);
        }
        return v;
    }

    public int size() throws Exception {
        return cache.size();
    }

    public boolean isAction() {
        return action;
    }

    public void dropAction() {
        action = false;
    }

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

    public TValue getRoot(TVariable t) throws Exception {
        for (TValue v : this) {
            if (!v.isDeleted(mind) && v.getTVar(mind).getId() == t.getId()) {
                return v;
            }
        }
        return null;
    }

    public Map<TVariable, TValue> getCurrent() {
        return current;
    }

    public void forEach(TVariable t, IReactor reactor) throws Exception {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        collectIds(t.getId(), ids);
        for (long id : ids) {
            TValue value = get(id);
            if (value != null) {
                reactor.run(value);
            }
        }
    }

    public void scan(TVariable t, IReactor reactor) throws Exception {
        if (!cache.isEmpty()) {
            IStep root;
            IStep bottom = null;
            do {
                root = cache.getRoot();
                IStep saveRoot = root;
                for (; root != bottom; root = root.getNext()) {
                    if (((TValue) root.getData(mind)).getTVarId() == t.getId()) {
                        reactor.run(root.getData(mind));
                    }
                }
                bottom = saveRoot;
            } while (root != cache.getRoot());
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
