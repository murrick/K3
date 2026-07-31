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

import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;

import java.util.Collection;

/**
 * Внутренняя граница storage-плагина, владеющего одним выбранным физическим
 * generation и набором его логических {@link IBase}.
 *
 * <p><strong>Роль и публикация.</strong> Реализация регистрируется в
 * {@link IUser} через {@link #init(IUser)}. {@link #use(String)} выбирает или
 * открывает один storage generation, после чего {@link #getBase(String)}
 * создаёт либо получает schema-specific bases. Публикация полного набора в
 * фабрики выполняется {@code User}; {@code IData} не выбирает текущий
 * {@link IMind} и не является логической транзакцией.</p>
 *
 * <p><strong>Атомарность acquisition.</strong> Открытый generation становится
 * видимым KANGER только после успешного получения полного canonical schema set.
 * Если acquisition одной базы завершается исключением до публикации, владелец
 * обязан закрыть частично открытый {@code IData}; partial set не должен
 * публиковаться фабрикам, а существующий in-memory Mind не должен уничтожаться.
 * Ошибка cleanup сохраняется как дополнительная к исходной acquisition error.</p>
 *
 * <p><strong>Владение ресурсами.</strong> {@code IData} владеет физическим
 * контекстом, registry логических баз и storage-wide close/flush/remove/reindex
 * sequencing. Фабрики и Escalera получают {@code IBase} как заимствованные
 * generation-local ссылки и не закрывают контейнер самостоятельно.</p>
 *
 * <p><strong>Операции lifecycle.</strong> {@link #flush()} публикует
 * накопленные physical changes без завершения generation; {@link #close()}
 * освобождает открытые ресурсы; {@link #remove(String)} уничтожает выбранное
 * хранилище согласно реализации. Эти методы не являются синонимами и не
 * заменяют transaction commit/release конкретного Mind.</p>
 *
 * <p><strong>Base lookup.</strong> {@link #getBase(String)} является
 * acquisition path и может создать отсутствующую логическую базу.
 * {@link #connect(String)} подключается к уже существующей базе в текущем
 * generation согласно контракту реализации. Вызывающая сторона не должна
 * смешивать эти операции либо предполагать, что отсутствие базы эквивалентно
 * пустой semantic schema.</p>
 *
 * <p><strong>Reindex.</strong> {@link #reindex(IReactor, IMind)} является
 * storage-wide migration/reconstruction workflow. Переданный Mind служит
 * контекстом гидратации и materialization; порядок схем, base codes, recovery и
 * swap policy принадлежат реализации и orchestration {@code User}. Reindex не
 * разрешает transaction overlay самостоятельно владеть physical destination.</p>
 *
 * <p><strong>Наблюдаемость.</strong> {@link #isClosed()} и
 * {@link #getStorageName()} описывают состояние физического generation, а не
 * наличие активного query или child transaction. {@link #list()} перечисляет
 * доступные storage names и не является перечислением схем текущего Mind.</p>
 *
 * <p><strong>Concurrency, failure и compatibility.</strong> Интерфейс не
 * обещает конкурентное управление одним generation несколькими независимыми
 * callers. Вызывающий владелец сериализует lifecycle и обязан учитывать
 * checked failures. DUMB directory layout, WAL, locking и recovery являются
 * конкретной реализацией; окончательные внешние гарантии public API проверяются
 * отдельно на этапе 3.5.0.5.</p>
 *
 * @see IBase
 * @see IUser
 * @see IMind
 */
public interface IData {
    void init(IUser user);

    void use(String name) throws Exception;

    void close() throws Exception;

    void flush() throws Exception;

    void remove(String name) throws Exception;

    void reindex(IReactor<String> reactor, IMind mind) throws Exception;

    boolean isClosed();

    String getStorageName();

    IBase getBase(String context) throws Exception;

    IBase connect(String context) throws Exception;

    String getDescription();

    Collection<String> list();
}
