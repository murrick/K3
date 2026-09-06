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
 * наличие активного query или child transaction. {@link #telemetry()} может
 * публиковать только уже существующее дешёвое состояние активного generation;
 * он не должен перечислять namespace, гидратировать semantic objects или
 * инициировать lifecycle. {@link #list()} перечисляет доступные storage names
 * и не является перечислением схем текущего Mind.</p>
 *
 * <p><strong>Concurrency, failure и compatibility.</strong> Интерфейс не
 * обещает конкурентное управление одним generation несколькими независимыми
 * callers. Вызывающий владелец сериализует lifecycle и обязан учитывать
 * checked failures. DUMB directory layout, WAL, locking и recovery являются
 * конкретной реализацией; окончательные внешние гарантии public API проверяются
 * отдельно на этапе 3.5.0.6.</p>
 *
 * @see IBase
 * @see IUser
 * @see IMind
 */
public interface IData {

    /**
     * Привязывает storage-плагин к пользовательскому контексту размещения и
     * конфигурации.
     *
     * <p>Метод не открывает generation и не публикует базы в Mind.</p>
     *
     * @param user внешний владелец конфигурации storage
     */
    void init(IUser user);

    /**
     * Выбирает или открывает physical storage generation с указанным именем.
     *
     * <p>Успешный вызов ещё не означает публикацию canonical schema set в
     * фабрики; acquisition и публикация выполняются владельцем отдельно.</p>
     *
     * @param name implementation-defined storage name
     * @throws Exception если generation нельзя открыть, проверить или восстановить
     */
    void use(String name) throws Exception;

    /**
     * Закрывает текущий generation и все принадлежащие storage-плагину базы.
     *
     * <p>Реализация должна попытаться освободить независимые ресурсы даже при
     * ошибке закрытия одного из них; aggregate failure определяется конкретным
     * storage contract.</p>
     *
     * @throws Exception если один или несколько ресурсов не закрыты корректно
     */
    void close() throws Exception;

    /**
     * Доводит накопленные physical changes всех открытых баз до storage-wide
     * durability boundary, не закрывая generation.
     *
     * @throws Exception если flush одной или нескольких баз не завершён
     */
    void flush() throws Exception;

    /**
     * Уничтожает storage generation с указанным именем согласно реализации.
     *
     * <p>Это destructive physical operation, не transaction rollback и не
     * очистка текущего Mind.</p>
     *
     * @param name удаляемый storage name
     * @throws Exception если generation активен, недоступен или не может быть удалён
     */
    void remove(String name) throws Exception;

    /**
     * Checks whether a named physical storage generation exists without
     * acquiring or creating it.
     *
     * <p>Implementations with a physical namespace should override this
     * method when {@link #list()} uses a presentation-oriented name.</p>
     *
     * @param name canonical physical storage name
     * @return {@code true} only when the generation already exists
     * @throws Exception when the namespace cannot be inspected truthfully
     */
    default boolean exists(String name) throws Exception {
        return list().contains(name);
    }

    /**
     * Выполняет storage-wide migration/reindex workflow.
     *
     * <p>{@code mind} задаёт контекст гидратации/materialization. Reactor
     * получает implementation-defined progress или schema events; его ошибки
     * считаются частью migration failure. Publication/swap destination должна
     * оставаться обратимой до успешного завершения workflow.</p>
     *
     * @param reactor callback наблюдения migration, допускается согласно реализации
     * @param mind контекст гидратации persistent units
     * @throws Exception при чтении source, построении destination, callback или swap
     */
    void reindex(IReactor<String> reactor, IMind mind) throws Exception;

    /**
     * Проверяет, завершён ли lifecycle текущего physical generation.
     *
     * @return {@code true}, если storage resources закрыты
     */
    boolean isClosed();

    /**
     * Возвращает имя текущего physical generation.
     *
     * @return выбранное storage name либо implementation-defined пустое значение,
     *         если generation не открыт
     */
    String getStorageName();

    /**
     * Получает или создаёт schema-specific base в текущем generation.
     *
     * <p>Это acquisition path. Возвращаемая база принадлежит {@code IData} и
     * передаётся вызывающей стороне как заимствованная ссылка.</p>
     *
     * @param context canonical schema name
     * @return открытая schema-specific base
     * @throws Exception если база не может быть создана, открыта или восстановлена
     */
    IBase getBase(String context) throws Exception;

    /**
     * Подключается к уже существующей schema-specific base текущего generation.
     *
     * <p>Метод не должен молча подменять отсутствие базы созданием новой, если
     * конкретный storage contract различает connect и acquisition.</p>
     *
     * @param context canonical schema name
     * @return подключённая base
     * @throws Exception если база отсутствует или подключение не удалось
     */
    IBase connect(String context) throws Exception;

    /**
     * Возвращает человекочитаемое описание storage implementation.
     *
     * @return описание плагина, формата или generation semantics
     */
    String getDescription();

    /**
     * Возвращает дешёвый snapshot уже открытого physical generation.
     *
     * <p>Default implementation объявляет метрики недоступными. Реализация не
     * должна ради telemetry выполнять {@link #list()}, acquisition, hydration,
     * full database scan, flush или иное изменение storage state.</p>
     *
     * @return provider telemetry либо unavailable snapshot
     */
    default StorageTelemetry telemetry() {
        return StorageTelemetry.unavailable();
    }

    /**
     * Перечисляет доступные physical storage generations.
     *
     * <p>Результат не является перечнем logical schemas текущего Mind и может
     * быть snapshot-представлением состояния внешнего storage namespace.</p>
     *
     * @return коллекция доступных storage names
     */
    Collection<String> list();
}
