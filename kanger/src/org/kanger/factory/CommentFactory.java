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
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.Comment;

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
 * Транзакционный реестр source/provenance {@link Comment}, принадлежащий одному
 * уровню {@link Mind}.
 *
 * <p><strong>Представление и роль.</strong> Комментарии адресуются ID Rule,
 * включая зарезервированные {@link #HEADER_ID} и {@link #FOOTER_ID}. Фабрика
 * поддерживает новые child-owned записи и безопасное изменение уже видимого
 * parent comment. Поэтому её transaction state состоит не только из
 * {@link Escalera}, но и из effective-view/copy-on-write metadata.</p>
 *
 * <p><strong>Владение и публикация.</strong> Каждый {@code Mind} создаёт
 * собственный экземпляр. Child {@code transaction(parentFactory)} строит cache
 * overlay, сохраняет {@code parentView} и начинает с пустых local overrides.
 * До typed parent commit изменение inherited Comment остаётся дочерней копией;
 * parent object не мутируется и release child уровня сохраняет исходный
 * комментарий.</p>
 *
 * <p><strong>Effective view.</strong> Lookup выполняется в порядке: local
 * override, effective parent view, затем raw local/cache value или root
 * storage hydration. При первой записи в inherited Comment {@code writable}
 * создаёт child-owned copy с тем же semantic ID. Iterator возвращает effective
 * values, а не обязательно raw objects Escalera.</p>
 *
 * <p><strong>Завершение транзакции.</strong> Typed
 * {@code commit(childFactory)} продвигает новые cache units, копирует child
 * overrides в parent context и объединяет dirty IDs. Решение о commit/release
 * принимает родительский {@code Mind}; фабрика реализует category-specific
 * publication, но не владеет reservation или всей composite atomicity.</p>
 *
 * <p><strong>Persistence.</strong> Только root factory заимствует
 * schema-specific {@link IBase} у {@link User}. Новые Comments проходят через
 * cache update. Изменения уже persistent IDs учитываются в
 * {@code dirtyUpdates}: root {@link #update()} записывает effective value в
 * существующий storage step. {@code applyOverrides()} материализует root
 * overrides перед pack. Storage generation и close authority принадлежат
 * {@code User}/{@code IData}, а не фабрике.</p>
 *
 * <p><strong>Checkpoint protocol.</strong> {@link #mark()}, {@link #commit()}
 * и {@link #release()} журналируют snapshot maps {@code overrides} и set
 * {@code dirtyUpdates} параллельно с Escalera frames. Cache rollback без этого
 * auxiliary journal не восстановил бы effective Comment view. Stack допускает
 * вложенные composite checkpoints.</p>
 *
 * <p><strong>Очистка.</strong> Child {@link #clear()} отбрасывает local
 * overrides/journal и заново создаёт overlay над текущим parent factory. Root
 * clear сбрасывает generation. {@link #pack()} сначала применяет только root
 * overrides, затем удаляет Comments, помеченные deleted, вместе с их auxiliary
 * metadata; child pack не публикует override непосредственно в parent raw
 * object.</p>
 *
 * <p><strong>Инварианты и concurrency.</strong> До commit inherited Comment
 * должен оставаться неизменным в parent view; release должен удалить child
 * override; committed persistent override должен переживать close/reopen.
 * Concurrent collections защищают отдельные publication operations, но не
 * делают mutable Comments, iterator или composite transaction protocol
 * независимо thread-safe.</p>
 *
 * <p><strong>Обязательства вызывающего кода.</strong> Нормальный доступ идёт
 * через фабрику актуального {@code Mind}. Вызывающая сторона не должна
 * удерживать raw object как авторитетный effective value между transaction
 * levels, изменять inherited Comment в обход {@link #add(long, String)} либо
 * закрывать общее storage через child factory.</p>
 *
 * @see DomainFactory
 * @see Comment
 */
public class CommentFactory {

    public static final String SCHEMA = "comments";

    public static final long HEADER_ID = -2L;
    public static final long FOOTER_ID = -3L;

    private static final class OverlayState {
        private final Map<Long, Comment> overrides;
        private final Set<Long> dirtyUpdates;

        private OverlayState(Map<Long, Comment> overrides, Set<Long> dirtyUpdates) {
            this.overrides = overrides;
            this.dirtyUpdates = dirtyUpdates;
        }
    }

    private ICache cache;
    private IStep top = null;
    private IBase connection = null;

    private final Mind mind;
    private CommentFactory parentView = null;
    private final ConcurrentHashMap<Long, Comment> overrides = new ConcurrentHashMap<>();
    private final Set<Long> dirtyUpdates = new CopyOnWriteArraySet<>();
    private final Stack<OverlayState> overlayStack = new Stack<>();

    public CommentFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(CommentFactory base) throws Exception {
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

    private Comment copyComment(Comment source, Mind owner) {
        Comment copy = new Comment(source.getId(), source.getComment(), owner);
        copy.setMindId(owner.getId());
        return copy;
    }

    private Map<Long, Comment> copyOverrides(Map<Long, Comment> source) {
        Map<Long, Comment> copy = new HashMap<>();
        for (Map.Entry<Long, Comment> entry : source.entrySet()) {
            copy.put(entry.getKey(), copyComment(entry.getValue(), mind));
        }
        return copy;
    }

    private Comment rawGet(long id) throws Exception {
        Comment value = (Comment) cache.get(id);
        if (value == null && connection != null) {
            IStep step = connection.get(id);
            if (step != null) {
                value = (Comment) step.getData(mind);
            }
        }
        return value;
    }

    private Comment effective(long id) throws Exception {
        Comment value = overrides.get(id);
        if (value != null) {
            value.setMind(mind);
            return value;
        }
        if (parentView != null) {
            Comment inherited = parentView.effective(id);
            if (inherited != null) {
                return inherited;
            }
        }
        return rawGet(id);
    }

    private Comment writable(Comment source) throws Exception {
        if (source.getMindId() == mind.getId() && source.getMind() == mind) {
            return source;
        }
        Comment copy = overrides.get(source.getId());
        if (copy == null) {
            Comment candidate = copyComment(source, mind);
            Comment previous = overrides.putIfAbsent(candidate.getId(), candidate);
            copy = previous == null ? candidate : previous;
        }
        return copy;
    }

    public void commit(CommentFactory base) throws Exception {
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
        Map<Long, Comment> childOverrides = base.copyOverrides(base.overrides);
        for (Map.Entry<Long, Comment> entry : childOverrides.entrySet()) {
            overrides.put(entry.getKey(), copyComment(entry.getValue(), mind));
        }
        dirtyUpdates.addAll(new HashSet<>(base.dirtyUpdates));
    }

    public void update() throws Exception {
        cache.update();
        if (connection != null) {
            for (long id : new HashSet<>(dirtyUpdates)) {
                Comment value = rawGet(id);
                IStep step = connection.get(id);
                if (value != null && step != null) {
                    step.setData(value);
                    step.update();
                }
            }
        }
        dirtyUpdates.clear();
    }

    public synchronized Comment add(long ruleId, String comment) throws Exception {
        Comment existing = get(ruleId);
        Comment target;
        if (existing != null) {
            target = writable(existing);
            target.setDeleted(false, mind);
            if (!target.getComment().equals(comment)) {
                target.setComment(comment);
                if (mind.getNext() == null && target == existing) {
                    dirtyUpdates.add(target.getId());
                }
            }
            return target;
        }

        target = new Comment(ruleId, comment, mind);
        target.setId(ruleId);
        target.setMindId(mind.getId());
        cache.add(target);
        if (top == null) {
            top = cache.getRoot();
        }
        return target;
    }

    public Comment get(long id) throws Exception {
        return effective(id);
    }

    public int size() throws Exception {
        return cache.size();
    }

    public void clear() throws Exception {
        overrides.clear();
        dirtyUpdates.clear();
        overlayStack.clear();
        if (mind.getNext() != null) {
            transaction((CommentFactory) ((Mind) mind.getNext()).getComments());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public Iterator iterator() {
        final Iterator raw = cache.iterator(-1);
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

    private void applyOverrides() throws Exception {
        if (mind.getNext() != null || overrides.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, Comment> entry : new HashMap<>(overrides).entrySet()) {
            Comment raw = rawGet(entry.getKey());
            if (raw != null) {
                raw.setMind(mind);
                raw.setMindId(mind.getId());
                raw.setComment(entry.getValue().getComment());
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

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    public boolean isEmpty() {
        return cache == null || cache.isEmpty();
    }

}
