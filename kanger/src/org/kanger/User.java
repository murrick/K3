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

package org.kanger;

import org.kanger.enums.StorageLifecycleErrorCode;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.factory.*;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.IData;
import org.kanger.units.Operation;

import java.io.*;
import java.util.*;

/**
 * Пользовательский контекст верхнего уровня, объединяющий внешние ресурсы и
 * настройки, с которыми работает один экземпляр KANGER.
 *
 * <p><strong>Архитектурная роль.</strong> {@code User} является границей
 * bootstrap и владения ресурсами, но не транзакцией и не версией логического
 * состояния. Он хранит подключённый модуль {@link IData}, реестр открытых
 * логических баз {@link IBase}, пользовательские параметры, возможность
 * создания UDF и области выделения идентификаторов. Конкретное состояние
 * вывода и транзакции представляет {@link Mind}.</p>
 *
 * <p><strong>Владение и публикация.</strong> Объект создаётся и удерживается
 * приложением или оболочкой. Корневой {@code Mind} получает его явно через
 * конструктор и сохраняет ту же ссылку для дочерних уровней. Открытые базы
 * публикуются фабрикам корневого {@code Mind} при выборе хранилища; физические
 * операции выполняет подключённая реализация {@code IData}.</p>
 *
 * <p><strong>Жизненный цикл.</strong> Transaction lifecycle и physical storage
 * lifecycle являются независимыми state machines. {@link #use(IMind, String)}
 * не закрывает уже открытый storage и допустим только при отсутствии
 * незавершённых child-транзакций; вызывающая оболочка может после успешного
 * открытия вставить новый persistent baseline под прежний level-0 workspace,
 * повторно канонизировав workspace как новый level-1 overlay.
 * {@link #checkpoint(IMind)} публикует durable root state, сохраняя storage и
 * runtime context открытыми; {@link #close(IMind)} не принимает решение за
 * незавершённую транзакцию и допустим только при transaction quiescence.</p>
 *
 * <p><strong>Persistence.</strong> При открытом хранилище идентификаторы схем
 * выделяются соответствующими {@code IBase}; без открытого хранилища
 * используются локальные счётчики пользователя. {@link #flush()} передаёт
 * накопленные изменения storage-модулю. Durable checkpoint намеренно
 * переиспользует уже квалифицированный empty-child root-finalization path,
 * поэтому сохраняет единый pack/update/flush ordering и не дублирует его в
 * storage boundary.</p>
 *
 * <p><strong>Инварианты.</strong> Внутренний lifecycle KANGER опирается на
 * явные ссылки {@code IMind}. Поле {@code currentMind} является только
 * управляемым вызывающим кодом compatibility slot: оно не является владельцем
 * хранилища, корнем shutdown-cleanup, публикацией текущей транзакции или
 * авторитетным источником активного runtime-контекста. Rejected lifecycle
 * operation не меняет active Mind, storage identity или physical generation.
 * Transaction quiescence означает одновременно level 0 у переданного Mind и
 * отсутствие незавершённых child reservations у корневого Mind.</p>
 *
 * <p><strong>Обязательства вызывающего кода.</strong> Вызывающая сторона
 * должна хранить и передавать фактически актуальную ссылку {@code IMind},
 * учитывать возвращаемое lifecycle-операциями значение, явно завершать
 * транзакции и выполнять успешный close перед последующим use.</p>
 *
 * @see Mind
 * @see IUser
 */
public class User implements IUser {

    private long id = -1L;
    private final Object locker = new Object();
    private Properties userSettings = new Properties();
    private IData data = null;
    private Class udf = null;
    private Map<String, IBase> storage = new HashMap<>();
    private Map<String, Long> counters = new HashMap<>();
    private long lastId = 0L;
    private String sourceFileName = "mind.k";
    private IMind currentMind = null;


    public IBase getStorage(String schema) {
        return storage.get(schema);
    }

    protected IMind clear(IMind mind) throws Exception {
        if (data != null && !data.isClosed()) {
            for (Map.Entry<String, IBase> e : storage.entrySet()) {
                e.getValue().clear();
            }
            data.flush();
        }

        for (IMind m = mind; m != null; m = m.getNext()) {
            ((Mind) m).clearMind();
            mind = m;
        }
        return mind;
    }

    public IMind remove(IMind mind, String name) throws Exception {
        boolean needClose = false;
        if (!isClosed() && (name == null || name.isEmpty() || name.equals(data.getStorageName()))) {
            name = data.getStorageName();
            needClose = true;
        }
        data.remove(name);
        return needClose ? close(mind) : mind;
    }

    public IMind reindex(IReactor<String> reactor, IMind mind, String name) throws Exception {
        boolean reopened = true;
        String saveName = "";
        if (isClosed()) {
            reopened = false;
        } else {
            saveName = data.getStorageName();
            mind = close(mind);
        }
        mind = use(mind, name);
        if (data != null && !data.isClosed()) {
            data.reindex(reactor, mind);
        }

        mind = close(mind);
        if (reopened) {
            mind = use(mind, saveName);
        }
        return mind;
    }

    public long lastId(String schema) {
        if (isClosed()) {
            synchronized (this) {
                if (!counters.containsKey(schema)) {
                    counters.put(schema, 0L);
                }
                return counters.get(schema);
            }
        } else {
            return storage.get(schema).lastId();
        }
    }

    public long nextId(String schema) {
        if (isClosed()) {
            synchronized (this) {
                if (!counters.containsKey(schema)) {
                    counters.put(schema, 0L);
                }
                long id = counters.get(schema);
                counters.put(schema, id + 1);
                return id;
            }
        } else {
            return storage.get(schema).nextId();
        }
    }

    public void clearCounters(String schema) {
        counters.put(schema, 0L);
    }

    public long lastId() {
        synchronized (locker) {
            return lastId;
        }
    }

    public long nextId() {
        synchronized (locker) {
            return lastId++;
        }
    }

    private void requireTransactionQuiescence(
            IMind mind,
            String operation) throws StorageLifecycleException {

        int level = mind.getTransactionLevel();
        Mind root = (Mind) mind.getTop();
        if (level > 0 || root.hasPendingTransactions()) {
            String state = level > 0
                    ? "transaction level " + level + " is active"
                    : "a child transaction is still active";
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.ACTIVE_TRANSACTION,
                    "Cannot " + operation + " while " + state
                            + "; commit or rollback first");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IMind checkpoint(IMind mind) throws Exception {
        if (mind == null) {
            throw new IllegalStateException(
                    "Cannot checkpoint storage without an active Mind");
        }
        requireTransactionQuiescence(mind, "checkpoint storage");
        if (isClosed()) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.NO_STORAGE_OPEN,
                    "Cannot checkpoint storage because no database is open");
        }

        IMind root = mind.getTop();
        Mind checkpoint = new Mind(root);
        boolean applied = root.commit(checkpoint);
        if (!applied) {
            throw new IllegalStateException("Empty root checkpoint was rejected");
        }
        return mind;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IMind close(IMind mind) throws Exception {
        if (isClosed()) {
            return mind;
        }
        if (mind == null) {
            throw new IllegalStateException(
                    "Cannot close an open database without an active Mind");
        }
        requireTransactionQuiescence(mind, "close database");

        checkpoint(mind);

        for (Map.Entry<String, IBase> e : storage.entrySet()) {
            e.getValue().clearCache();
        }
        data.close();

        for (IMind m = mind; m != null; m = m.getNext()) {
            ((Mind) m).clearMind();
            mind = m;
        }

        return mind;
    }


    public boolean isClosed() {
        return data == null || data.isClosed();
    }

    public String getStorageName() {
        return data == null ? "" : data.getStorageName();
    }

    public Collection<String> getStoragesList() {
        if (data != null) {
            return data.list();
        } else {
            return new ArrayList<>();
        }
    }

    public void flush() throws Exception {
        if (data != null) {
            data.flush();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IMind use(IMind mind, String name) throws Exception {
        if (data == null) {
            throw new RuntimeErrorException("DB module doesn't loaded");
        }

        if (mind == null) {
            mind = new Mind(this);
        }

        if (data.isClosed()) {
            requireTransactionQuiescence(mind, "open database");
            return openClosedStorage((Mind) mind, name);
        }

        String activeName = data.getStorageName();
        if (Objects.equals(activeName, name)) {
            throw new StorageLifecycleException(
                    StorageLifecycleErrorCode.STORAGE_ALREADY_OPEN,
                    "Database " + activeName + " is already open");
        }

        return rebaseOpenStorage((Mind) mind, name, activeName);
    }

    private Mind rebaseOpenStorage(Mind top,
                                   String targetName,
                                   String originalName) throws Exception {
        requireExplicitStackOwnership(top);
        UserTransactionStackSnapshot snapshot =
                UserTransactionStackSnapshot.capture(top);

        String originalSourceFileName = sourceFileName;
        boolean ownsCurrentSlot = currentMind == top;
        Mind root = (Mind) top.getTop();
        boolean mutationStarted = false;

        try {
            mutationStarted = true;
            root = UserTransactionStackSnapshot.rollbackToRoot(top);
            if (ownsCurrentSlot) {
                currentMind = root;
            }

            root = (Mind) close(root);
            root = openClosedStorage(root, targetName);
            Mind rebasedTop = snapshot.replay(root);

            sourceFileName = originalSourceFileName;
            if (ownsCurrentSlot) {
                currentMind = rebasedTop;
            }
            return rebasedTop;
        } catch (Throwable failure) {
            Throwable propagated = failure;
            if (mutationStarted) {
                try {
                    Mind restoredRoot = restoreOriginalStorage(
                            root, originalName, originalSourceFileName);
                    Mind restoredTop = snapshot.replay(restoredRoot);
                    sourceFileName = originalSourceFileName;
                    if (ownsCurrentSlot) {
                        currentMind = restoredTop;
                    }
                } catch (Throwable restoreFailure) {
                    if (restoreFailure != failure) {
                        propagated.addSuppressed(restoreFailure);
                    }
                    if (ownsCurrentSlot) {
                        currentMind = root;
                    }
                }
            }
            rethrow(propagated);
            throw new AssertionError("unreachable");
        }
    }

    private Mind restoreOriginalStorage(Mind root,
                                        String originalName,
                                        String originalSourceFileName) throws Exception {
        if (!data.isClosed()
                && !Objects.equals(data.getStorageName(), originalName)) {
            root = (Mind) close(root);
        }

        if (data.isClosed()) {
            root = openClosedStorage(root, originalName);
        } else if (!Objects.equals(data.getStorageName(), originalName)) {
            throw new IllegalStateException(
                    "Cannot restore storage " + originalName
                            + "; active storage is " + data.getStorageName());
        }

        sourceFileName = originalSourceFileName;
        return root;
    }

    private void requireExplicitStackOwnership(Mind top) {
        Mind current = top;
        if (current.hasPendingTransactions()) {
            throw new IllegalStateException(
                    "Cannot rebase storage while the current user level owns an unfinished technical transaction");
        }

        while (current.getNext() != null) {
            Mind parent = (Mind) current.getNext();
            if (!parent.hasPendingTransactions()) {
                throw new IllegalStateException(
                        "Explicit transaction chain is missing its child reservation");
            }
            current = parent;
        }
    }

    private Mind openClosedStorage(Mind mind, String name) throws Exception {
        if (!data.isClosed()) {
            throw new IllegalStateException(
                    "Cannot open storage " + name + " while "
                            + data.getStorageName() + " is still active");
        }
        if (mind.getNext() != null || mind.getTransactionLevel() != 0) {
            throw new IllegalStateException(
                    "Physical storage can only be attached to a root Mind");
        }

        Map<String, IBase> acquiredStorage = new HashMap<>();
        try {
            data.use(name);

            acquiredStorage.put(DictionaryFactory.SCHEMA, data.getBase(DictionaryFactory.SCHEMA));
            acquiredStorage.put(DomainFactory.SCHEMA, data.getBase(DomainFactory.SCHEMA));
            acquiredStorage.put(FunctionFactory.SCHEMA, data.getBase(FunctionFactory.SCHEMA));
            acquiredStorage.put(PredicateFactory.SCHEMA, data.getBase(PredicateFactory.SCHEMA));
            acquiredStorage.put(RuleFactory.SCHEMA, data.getBase(RuleFactory.SCHEMA));
            acquiredStorage.put(TVariableFactory.SCHEMA, data.getBase(TVariableFactory.SCHEMA));
            acquiredStorage.put(LibraryFactory.SCHEMA, data.getBase(LibraryFactory.SCHEMA));
            acquiredStorage.put(TValueFactory.SCHEMA, data.getBase(TValueFactory.SCHEMA));
            acquiredStorage.put(FValueFactory.SCHEMA, data.getBase(FValueFactory.SCHEMA));
            acquiredStorage.put(CommentFactory.SCHEMA, data.getBase(CommentFactory.SCHEMA));
        } catch (Exception error) {
            try {
                if (!data.isClosed()) {
                    data.close();
                }
            } catch (Exception closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }

        storage.clear();
        storage.putAll(acquiredStorage);

        ((DictionaryFactory) mind.getTerms()).transaction(null);
        mind.getDomains().transaction(null);
        mind.getFunctions().transaction(null);
        mind.getFValues().transaction(null);
        ((PredicateFactory) mind.getPredicates()).transaction(null);
        ((RuleFactory) mind.getRules()).transaction(null);
        mind.getComments().transaction(null);
        mind.getTValues().transaction(null);
        mind.getTVars().transaction(null);
        ((LibraryFactory) mind.getLibrary()).transaction(null);

        return mind;
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new RuntimeException(failure);
    }

    public IData getData() throws RuntimeErrorException {
        if (data != null) {
            return data;
        } else {
            throw new RuntimeErrorException("DB module doesn't loaded");
        }
    }

    public void setData(IData db) {
        data = db;
    }

    @Override
    public String getProperty(String key, String val) throws Exception {
        if (userSettings.containsKey(key)) {
            return userSettings.getProperty(key);
        } else {
            setProperty(key, val);
        }
        return val;
    }

    @Override
    public void setProperty(String key, String val) throws Exception {
        if (val != null) {
            userSettings.setProperty(key, val);
        } else {
            userSettings.remove(key);
        }
        loadProperties();
        if (val != null) {
            userSettings.setProperty(key, val);
        } else {
            userSettings.remove(key);
        }
        if (userSettings.containsKey("user.dir")) {
            String confName = userSettings.getProperty("user.dir") + "kanger.conf";
            try {
                new File(confName).getParentFile().mkdirs();
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(confName))) {
                    userSettings.store(bw, new Date().toString());
                }
            } catch (IOException e) {
                System.err.println(new Date());
                e.printStackTrace(System.err);
            }
        }
    }

    @Override
    public String getUserDir() {
        if (userSettings.containsKey("user.dir")) {
            return userSettings.getProperty("user.dir");
        } else {
            return "";
        }
    }

    @Override
    public String getDatabaseDir() {
        if (userSettings.containsKey("database.dir")) {
            return userSettings.getProperty("database.dir");
        } else {
            return "";
        }
    }

    @Override
    public String getSourceDir() {
        if (userSettings.containsKey("sources.dir")) {
            return userSettings.getProperty("sources.dir");
        } else {
            return "";
        }
    }

    @Override
    public void setUserDir(String dir) {
        userSettings.setProperty("user.dir", dir);
    }

    @Override
    public void setDatabaseDir(String dir) {
        userSettings.setProperty("database.dir", dir);
    }

    @Override
    public void setSourceDir(String dir) {
        userSettings.setProperty("sources.dir", dir);
    }

    @Override
    public void loadProperties() throws Exception {
        if (userSettings.containsKey("user.dir")) {
            String confName = userSettings.getProperty("user.dir") + "kanger.conf";
            if (new File(confName).exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(confName))) {
                    userSettings.load(br);
                }
            }
        }
    }

    @Override
    public boolean containsProperty(String key) {
        return userSettings.containsKey(key);
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    public Operation getUdf() throws Exception {
        if (udf != null) {
            return (Operation) udf.getConstructors()[0].newInstance();
        } else {
            throw new RuntimeErrorException("UDF module doesn't loaded");
        }
    }

    public void setUdf(Class udf) {
        this.udf = udf;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    @Override
    public IMind getCurrentMind() {
        return currentMind;
    }

    @Override
    public void setCurrentMind(IMind currentMind) {
        this.currentMind = currentMind;
    }

}
