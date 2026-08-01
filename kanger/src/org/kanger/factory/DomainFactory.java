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
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.ArgumentsList;
import org.kanger.storage.Escalera;
import org.kanger.units.CachedDomain;
import org.kanger.units.Domain;
import org.kanger.units.Predicate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Канонический реестр и транзакционный overlay {@link Domain} одного уровня
 * {@link Mind}.
 *
 * <p><strong>Представление и роль.</strong> Фабрика канонизирует домены по
 * фактическому descriptor, включающему предикат, полярность, аргументы и Rule
 * context текущего equality/hash contract. При создании она также фиксирует
 * substitutable/abstractive свойства аргументов. Это граница identity,
 * hydration и transaction visibility, а не только конструктор Domain.</p>
 *
 * <p><strong>Владение и публикация.</strong> Каждый {@code Mind} создаёт
 * собственную {@code DomainFactory}. Дочерний уровень инициализирует её через
 * {@code transaction(parentFactory)}: {@link Escalera} образует overlay над
 * cache родителя, а auxiliary set {@code waiters} копируется как исходное
 * видимое состояние. До parent commit дочерние добавления принадлежат child
 * factory и не должны публиковаться как состояние родителя.</p>
 *
 * <p><strong>Завершение транзакции.</strong> Typed
 * {@code commit(childFactory)} продвигает child cache, переводит promoted
 * units в контекст родительского {@code Mind} и объединяет child waiters.
 * Release всего child уровня координирует родительский {@code Mind}; сама
 * фабрика не принимает решения о принятии или отклонении логической
 * транзакции.</p>
 *
 * <p><strong>Deferred-domain metadata.</strong> {@code waiters} содержит
 * домены, опубликованные {@link RuleFactory} для последующего использования
 * Linker при сопоставлении с opposite-polarity masters. Это семантическое
 * inference state, а не diagnostic cache. Поэтому checkpoint protocol
 * {@link #mark()}, {@link #commit()} и {@link #release()} сохраняет и
 * восстанавливает waiters параллельно с Escalera; release обязан вернуть обе
 * части factory state к одному transaction snapshot.</p>
 *
 * <p><strong>Persistence.</strong> Только root factory при открытом storage
 * заимствует schema-specific {@link IBase} у {@link User}. Cache miss может
 * materialize Domain через эту базу, а root update — передать изменения в
 * storage. Child overlay получает видимость через parent cache, но не storage
 * connection или close authority; generation и закрытием владеют
 * {@code User}/{@code IData}.</p>
 *
 * <p><strong>Очистка.</strong> Child {@link #clear()} заново строит overlay из
 * текущего parent view; root clear сбрасывает canonical generation. {@link
 * #pack()} удаляет только Domains, помеченные deleted, и синхронно исключает
 * их из waiters. Метод не является общей сборкой достижимости всех активных
 * доменов.</p>
 *
 * <p><strong>Инварианты и concurrency.</strong> Canonical add защищён
 * локальной синхронизацией, а waiter set допускает безопасное snapshot-чтение,
 * но это не делает mutable Domains, iterator или весь transaction protocol
 * независимо thread-safe. Parent publication, composite checkpoint order и
 * transaction reservation остаются ответственностью {@code Mind}.</p>
 *
 * <p><strong>Обязательства вызывающего кода.</strong> Нормальный доступ идёт
 * через фабрику актуального {@code Mind}. Вызывающая сторона не должна
 * трактовать child overlay как самостоятельную persistent базу, изменять
 * parent state до commit либо считать Escalera единственной частью Domain
 * transaction state.</p>
 *
 * @see CommentFactory
 * @see IFactory
 * @see Domain
 */
public class DomainFactory implements IFactory<Domain> {

    public static final String SCHEMA = "domains";

    private ICache cache;
    private IStep top = null;
    private IBase connection = null;
    private final Mind mind;
    private final Set<Domain> waiters = new CopyOnWriteArraySet<>();
    private final Stack<Set<Domain>> waiterStack = new Stack<>();

    public DomainFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(DomainFactory base) throws Exception {
        // A new transaction/storage generation must not retain old chain anchors.
        top = null;
        connection = null;
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }

        waiters.clear();
        waiterStack.clear();
        if (base != null) {
            waiters.addAll(base.waiters);
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
        }
    }

    public void commit(DomainFactory base) throws Exception {
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
        waiters.addAll(base.waiters);
    }

    public void update() throws Exception {
        if (cache.update()) {
        }
    }

    public synchronized Domain add(Predicate pred, boolean antc, ArgumentsList arg, IRule r) throws Exception {
        Domain p = find(pred, antc, arg, r);
        if (p != null) {
            p.setDeleted(false, mind);
            return p;
        } else {
            p = new CachedDomain(mind);
            p.setPredicate(pred);
            p.setAntc(antc);
            p.setRule(r);
            p.setId(((User) mind.getUser()).nextId(SCHEMA));
            p.setMindId(mind.getId());

            if (arg != null) {
                if (!arg.getTVariables(mind).isEmpty()) {
                    p.setSubstitutable();
                }
                if (!arg.getCVariables(mind).isEmpty()) {
                    p.setAbstractive();
                }
                for (IArgument t : arg) {
                    p.add(t);
                }
            }
            return add(p);
        }
    }

    public synchronized Domain add(Domain p) throws Exception {
        cache.add(p);
        if (top == null) {
            top = cache.getRoot();
        }
        return p;
    }

    public Domain find(Predicate pred, boolean antc, ArgumentsList arg, IRule r) throws Exception {
        Domain temp = new Domain(pred, antc, arg, r);
        return find(temp);
    }

    public Domain find(Domain d) throws Exception {
        for (long id : cache.find(d.getHash(mind))) {
            IUnit one = get(id);
            if (one.equalsTo(d)) {
                return (Domain) one;
            }
        }
        return null;
    }

    @Override
    public Domain get(long id) throws Exception {
        Domain t = (Domain) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (Domain) s.getData(mind);
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
            waiters.remove(o);
            cache.delete(((IUnit) o).getId());
        }
    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction(((Mind) mind.getNext()).getDomains());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public void mark() throws Exception {
        cache.mark();
        waiterStack.push(new HashSet<>(waiters));
    }

    public void commit() throws Exception {
        cache.commit();
        if (!waiterStack.isEmpty()) {
            waiterStack.pop();
        }
    }

    public void release() throws Exception {
        cache.release();
        if (!waiterStack.isEmpty()) {
            Set<Domain> waiterSnapshot = waiterStack.pop();
            waiters.clear();
            waiters.addAll(waiterSnapshot);
        }
    }

    @Override
    public int size() throws Exception {
        return cache.size();
    }

    public Set<Domain> getWaiters() {
        return waiters;
    }

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Override
    public boolean isEmpty() {
        return cache == null || cache.isEmpty();
    }

}
