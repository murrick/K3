/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
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

package org.kanger.storage;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;

import java.util.*;

/**
 * Транзакционная linked-snapshot проекция canonical units одной schema,
 * видимая конкретному {@link Mind}.
 *
 * <p><strong>Представление.</strong> Escalera хранит собственный указатель
 * {@code root} на цепочку {@link IStep}. Memory-only {@link Step} содержит ещё
 * не материализованный unit; {@link Sapato} представляет persistent link,
 * гидратируемый через заимствованный {@link IBase}. Объект не является
 * физическим storage container и не владеет закрытием базы.</p>
 *
 * <p><strong>Root и child snapshots.</strong> Корневая Escalera при открытом
 * storage начинает с persistent root соответствующей schema. Child Escalera
 * получает root родительского {@link ICache}, создавая собственную mutable
 * snapshot boundary поверх той же видимой цепочки. Публикация нового root между
 * factory layers выполняется explicit transaction protocol владельца Mind.</p>
 *
 * <p><strong>Chain authority.</strong> Наблюдаемый порядок задаётся только
 * связями {@code next}: новые Step добавляются в голову, iterator идёт от root
 * к tail. ID, hash buckets и physical index positions являются descriptors и
 * acceleration metadata, но не источником порядка. Persistent endpoints
 * восстанавливаются {@code IBase} из links.</p>
 *
 * <p><strong>Checkpoint protocol.</strong> {@link #mark()} всегда открывает
 * реальный LIFO frame и сохраняет root, включая пустой root. {@link #commit()}
 * потребляет ровно один frame, сохраняя текущую цепочку; {@link #release()}
 * восстанавливает root frame и инвалидирует производные indexes. Completion
 * без открытого frame является lifecycle error. Checkpoint не копирует graph и
 * не выполняет physical storage commit.</p>
 *
 * <p><strong>Acceleration metadata.</strong> {@code memoryById},
 * {@code persistentIds}, {@code idsByHash} и {@code predecessorById} являются
 * производными структурами для lookup, find и splice. Они перестраиваются из
 * текущей linked chain, не владеют canonical units и не переживают изменение
 * root как semantic authority. Persistent entries удерживаются ID-only и
 * гидратируются через IBase только при фактическом доступе.</p>
 *
 * <p><strong>Mutation и deletion.</strong> {@link #add(IUnit)} создаёт новый
 * memory Step в голове snapshot. Delete перестраивает links и metadata; если
 * изменяется persistent predecessor, обновляет его physical link, после чего
 * удаляет record из IBase. Batch deletion сохраняет ту же linked semantics и
 * не допускает, чтобы ID-order заменил predecessor relations.</p>
 *
 * <p><strong>Materialization.</strong> {@link #update()} собирает ведущий
 * memory-only segment, публикует его от старейшего Step к новейшему и заменяет
 * его persistent Sapato nodes. Такой порядок сохраняет существующую цепочку и
 * делает новый persistent root последним. После materialization открытые local
 * checkpoints очищаются: persisted boundary уже не является откатываемым
 * transient frame этой Escalera.</p>
 *
 * <p><strong>Clear boundary.</strong> {@link #clear()} сбрасывает snapshot и
 * derived metadata. На root factory при открытом storage он также очищает
 * заимствованный IBase; child factory lifecycle обязан вместо destructive
 * clear перестроить overlay через parent transaction. Без storage очищается
 * schema-local in-memory ID counter пользователя.</p>
 *
 * <p><strong>Hydration и ошибки.</strong> Persistent lookup выполняется через
 * schema-specific IBase текущего User generation. Escalera не должна
 * интерпретировать storage/recovery failure как semantic absence и не должна
 * самостоятельно отображать такие ошибки. Iterator сохраняет исходный failure
 * object и передаёт его внешнему lifecycle/application boundary; unchecked
 * bridge нужен только потому, что контракт {@link Iterator} не объявляет
 * checked exceptions.</p>
 *
 * <p><strong>Владение storage.</strong> Escalera заимствует IBase у User и
 * никогда не выбирает generation, не приобретает полный schema set и не
 * закрывает IData/IBase. Exception-atomic acquisition и публикация storage
 * generation выполняются User/IData до инициализации root factories.</p>
 *
 * <p><strong>Concurrency.</strong> Отдельные persistent операции синхронизуют
 * доступ к IBase, но mutable root, checkpoint stack и indexes не делают
 * Escalera independently thread-safe. Factory/Mind owner сериализует composite
 * transaction и не использует один snapshot конкурентно без внешнего протокола.</p>
 *
 * <p><strong>Обязательства вызывающего кода.</strong> Не следует менять root,
 * links или indexes в обход ICache protocol; считать hash/ID index canonical
 * truth; закрывать borrowed storage; смешивать checkpoint commit с physical
 * update; либо вызывать destructive clear на child overlay.</p>
 *
 * @see ICache
 * @see IBase
 * @see Step
 * @see Sapato
 */
public class Escalera implements ICache {

    private IStep root = null;

    private String schema = "";
    private Stack<Checkpoint> stack = new Stack<>();

    private Mind mind = null;

    private static final class Checkpoint {
        private final IStep root;

        private Checkpoint(IStep root) {
            this.root = root;
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException propagate(Throwable failure) throws E {
        throw (E) failure;
    }

    /**
     * Lightweight acceleration metadata. Persistent semantic objects are not
     * retained here: persistent entries are represented by IDs and are loaded
     * through the configured IBase only when get(id) is requested.
     */
    private final Map<Long, IStep> memoryById = new HashMap<>();
    private final Set<Long> persistentIds = new HashSet<>();
    private final Map<Integer, Set<Long>> idsByHash = new HashMap<>();
    private final Map<Long, Long> predecessorById = new HashMap<>();
    private boolean indexValid = false;
    private IStep indexedRoot = null;

    public Escalera(Mind mind, String schema, ICache parent) {
        this.mind = mind;
        this.schema = schema;

        if (parent == null && mind.isStorageUsed()) {
            synchronized (((User) mind.getUser()).getStorage(schema)) {
                root = ((User) mind.getUser()).getStorage(schema).getRoot();
            }
        } else if (parent != null) {
            root = parent.getRoot();
        }
    }

    private void invalidateIndex() {
        indexValid = false;
        indexedRoot = null;
    }

    private void clearIndex() {
        memoryById.clear();
        persistentIds.clear();
        idsByHash.clear();
        predecessorById.clear();
    }

    private void indexStep(IStep step) {
        if (step == null) {
            return;
        }
        long id = step.getId();
        if (step instanceof Sapato) {
            persistentIds.add(id);
            memoryById.remove(id);
        } else {
            memoryById.put(id, step);
            persistentIds.remove(id);
        }
        Set<Long> ids = idsByHash.get(step.getHash());
        if (ids == null) {
            ids = new HashSet<>();
            idsByHash.put(step.getHash(), ids);
        }
        ids.add(id);
    }

    private void removeIndexedStep(long id, IStep step) {
        memoryById.remove(id);
        persistentIds.remove(id);
        if (step != null) {
            Set<Long> ids = idsByHash.get(step.getHash());
            if (ids != null) {
                ids.remove(id);
                if (ids.isEmpty()) {
                    idsByHash.remove(step.getHash());
                }
            }
        } else {
            Iterator<Map.Entry<Integer, Set<Long>>> iterator = idsByHash.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, Set<Long>> entry = iterator.next();
                entry.getValue().remove(id);
                if (entry.getValue().isEmpty()) {
                    iterator.remove();
                }
            }
        }
    }

    private void ensureIndex() {
        if (indexValid && indexedRoot == root) {
            return;
        }
        clearIndex();
        IStep predecessor = null;
        for (IStep step = root; step != null; step = step.getNext()) {
            indexStep(step);
            if (predecessor != null) {
                predecessorById.put(step.getId(), predecessor.getId());
            }
            predecessor = step;
        }
        indexedRoot = root;
        indexValid = true;
    }

    private IStep indexedStep(long id) throws Exception {
        IStep step = memoryById.get(id);
        if (step == null && persistentIds.contains(id) && mind.isStorageUsed()) {
            synchronized (((User) mind.getUser()).getStorage(schema)) {
                step = ((User) mind.getUser()).getStorage(schema).get(id);
            }
        }
        return step;
    }

    @Override
    public void add(IUnit one) throws Exception {
        ensureIndex();
        Step s = new Step();
        s.setData(one);
        s.setId(one.getId());
        s.setHash(one.getHash());
        IStep previousRoot = root;
        s.setNext(previousRoot);
        root = s;
        indexStep(s);
        predecessorById.remove(s.getId());
        if (previousRoot != null) {
            predecessorById.put(previousRoot.getId(), s.getId());
        }
        indexedRoot = root;
    }

    @Override
    public Object get(long id) throws Exception {
        ensureIndex();
        IStep step = memoryById.get(id);
        if (step == null && persistentIds.contains(id) && mind.isStorageUsed()) {
            synchronized (((User) mind.getUser()).getStorage(schema)) {
                step = ((User) mind.getUser()).getStorage(schema).get(id);
            }
        }
        return step == null ? null : step.getData(mind);
    }

    private void deleteOne(long id) throws Exception {
        ensureIndex();
        IStep target = indexedStep(id);
        if (target == null) {
            return;
        }

        IStep successor = target.getNext();
        Long predecessorId = predecessorById.get(id);
        if (predecessorId == null) {
            if (root != null && root.getId() == id) {
                root = successor;
            }
        } else {
            IStep predecessor = indexedStep(predecessorId);
            if (predecessor != null) {
                predecessor.setNext(successor);
                if (predecessor instanceof Sapato) {
                    predecessor.update();
                }
            }
        }

        predecessorById.remove(id);
        if (successor != null) {
            if (predecessorId == null) {
                predecessorById.remove(successor.getId());
            } else {
                predecessorById.put(successor.getId(), predecessorId);
            }
        }
        removeIndexedStep(id, target);
        indexedRoot = root;

        if (mind.isStorageUsed()) {
            synchronized (((User) mind.getUser()).getStorage(schema)) {
                ((User) mind.getUser()).getStorage(schema).delete(id);
            }
        }
    }

    @Override
    public void delete(long id) throws Exception {
        deleteOne(id);
    }

    @Override
    public void deleteAll(Collection<Long> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        if (!mind.isStorageUsed() || ids.size() == 1) {
            LinkedHashSet<Long> unique = new LinkedHashSet<>(ids);
            for (Long id : unique) {
                if (id != null) {
                    deleteOne(id);
                }
            }
            return;
        }

        ensureIndex();
        LinkedHashSet<Long> targets = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && (memoryById.containsKey(id) || persistentIds.contains(id))) {
                targets.add(id);
            }
        }
        if (targets.isEmpty()) {
            return;
        }

        LinkedHashMap<Long, IStep> removed = new LinkedHashMap<>();
        LinkedHashSet<IStep> changedPersistentPredecessors = new LinkedHashSet<>();
        for (long startId : targets) {
            Long predecessorId = predecessorById.get(startId);
            if (predecessorId != null && targets.contains(predecessorId)) {
                continue;
            }

            IStep current = indexedStep(startId);
            if (current == null) {
                continue;
            }
            IStep predecessor = predecessorId == null ? null : indexedStep(predecessorId);
            while (current != null && targets.contains(current.getId())) {
                removed.put(current.getId(), current);
                current = current.getNext();
            }
            IStep successor = current;

            if (predecessor == null) {
                if (root != null && targets.contains(root.getId())) {
                    root = successor;
                }
            } else {
                predecessor.setNext(successor);
                if (predecessor instanceof Sapato) {
                    changedPersistentPredecessors.add(predecessor);
                }
            }

            if (successor != null) {
                if (predecessorId == null) {
                    predecessorById.remove(successor.getId());
                } else {
                    predecessorById.put(successor.getId(), predecessorId);
                }
            }
        }

        for (IStep predecessor : changedPersistentPredecessors) {
            predecessor.update();
        }
        for (Map.Entry<Long, IStep> entry : removed.entrySet()) {
            predecessorById.remove(entry.getKey());
            removeIndexedStep(entry.getKey(), entry.getValue());
        }
        indexedRoot = root;

        if (mind.isStorageUsed() && !removed.isEmpty()) {
            IBase base = ((User) mind.getUser()).getStorage(schema);
            base.deleteAll(removed.keySet());
        }
    }

    @Override
    public int size() {
        ensureIndex();
        return memoryById.size() + persistentIds.size();
    }

    @Override
    public boolean isEmpty() {
        return root == null;
    }

    @Override
    public Set<Long> find(int h) throws Exception {
        ensureIndex();
        Set<Long> ids = idsByHash.get(h);
        return ids == null ? new HashSet<Long>() : new HashSet<>(ids);
    }

    @Override
    public void clear() throws Exception {
        root = null;
        clearIndex();
        indexedRoot = null;
        indexValid = true;
        if (mind.isStorageUsed()) {
            synchronized (((User) mind.getUser()).getStorage(schema)) {
                ((User) mind.getUser()).getStorage(schema).clear();
            }
        } else {
            ((User) mind.getUser()).clearCounters(schema);
        }
    }

    @Override
    public long mark() {
        stack.push(new Checkpoint(root));
        return root == null ? -1 : root.getId();
    }

    @Override
    public long commit() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Escalera commit without an open checkpoint");
        }
        stack.pop();
        return root == null ? -1 : root.getId();
    }

    @Override
    public long release() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Escalera release without an open checkpoint");
        }
        root = stack.pop().root;
        invalidateIndex();
        return root == null ? -1 : root.getId();
    }

    @Override
    public boolean containsKey(long id) throws Exception {
        ensureIndex();
        return memoryById.containsKey(id) || persistentIds.contains(id);
    }

    @Override
    public Iterator<Object> iterator() {
        return new WalkIterator(-1);
    }

    @Override
    public Iterator<Object> iterator(long fromId) {
        return new WalkIterator(fromId);
    }

    @Override
    public IStep getRoot() {
        return root;
    }

    @Override
    public void setRoot(IStep newRoot) {
        if (!indexValid) {
            root = newRoot;
            return;
        }

        IStep oldRoot = root;
        root = newRoot;
        if (oldRoot == newRoot) {
            indexedRoot = root;
            return;
        }

        boolean reachedOldRoot = oldRoot == null;
        IStep predecessor = null;
        for (IStep step = newRoot; step != null; step = step.getNext()) {
            if (oldRoot != null && step.getId() == oldRoot.getId()) {
                reachedOldRoot = true;
                if (predecessor == null) {
                    predecessorById.remove(step.getId());
                } else {
                    predecessorById.put(step.getId(), predecessor.getId());
                }
                break;
            }
            indexStep(step);
            if (predecessor == null) {
                predecessorById.remove(step.getId());
            } else {
                predecessorById.put(step.getId(), predecessor.getId());
            }
            predecessor = step;
        }
        if (reachedOldRoot) {
            indexedRoot = root;
        } else {
            invalidateIndex();
        }
    }

    @Override
    public boolean update() throws Exception {
        if (mind.isStorageUsed()) {
            IBase base = ((User) mind.getUser()).getStorage(schema);
            synchronized (base) {
                Deque<IStep> pending = new ArrayDeque<>();
                for (IStep s = root; s != null && !(s instanceof Sapato); s = s.getNext()) {
                    pending.push(s);
                }

                while (!pending.isEmpty()) {
                    IStep s = pending.pop();
                    Sapato p = new Sapato(base, s);
                    p.append();
                    IStep e = base.get(s.getId());
                    p.setData(e.getData(mind));
                    root = p;
                    if (indexValid) {
                        memoryById.remove(s.getId());
                        persistentIds.add(s.getId());
                    }
                }
                if (indexValid) {
                    indexedRoot = root;
                }
                stack.clear();
                return true;
            }
        } else {
            return false;
        }
    }

    public class WalkIterator implements Iterator {

        @Override
        public void remove() {
        }

        private IStep step;

        public WalkIterator(long fromId) {
            step = root;
            try {
                if (root instanceof Sapato) {
                    root.setData(((User) mind.getUser()).getStorage(schema).get(root.getId()).getData(mind));
                }
            } catch (Exception e) {
                throw Escalera.<RuntimeException>propagate(e);
            }

            if (fromId >= 0) {
                for (; step != null; step = step.getNext()) {
                    if (step.getId() == fromId) {
                        break;
                    }
                }
            }
        }

        @Override
        public boolean hasNext() {
            return step != null;
        }

        @Override
        public Object next() {
            try {
                Object o = step.getData(mind);
                step = step.getNext();
                return o;
            } catch (Exception e) {
                throw Escalera.<RuntimeException>propagate(e);
            }
        }
    }
}
