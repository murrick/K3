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

package org.kanger.interfaces;

/**
 * Внешний пользовательский контекст KANGER и граница пользовательского владения.
 *
 * <p><strong>Архитектурная роль.</strong> {@code IUser} связывает приложение
 * с пользовательскими параметрами, физическим storage lifecycle и
 * caller-managed ссылкой на активный {@link IMind}. Пользователь не является
 * частью логического вывода: он задаёт внешний контекст размещения,
 * конфигурации и владения storage, но не определяет canonical identity правил,
 * термов или иных сущностей знания.</p>
 *
 * <p><strong>Inside.</strong> Собственное состояние пользователя состоит из
 * прикладного идентификатора, набора строковых параметров, выбранного storage
 * module и compatibility slot текущего Mind. Это состояние принадлежит
 * объекту пользователя и не является transaction overlay.</p>
 *
 * <p><strong>Outside.</strong> Связи пользователя с файловой системой,
 * storage-модулем и текущим Mind являются внешними проекциями. Значения
 * {@code user.dir}, {@code database.dir} и {@code sources.dir} лишь передают
 * пути другим компонентам. {@link #getCurrentMind()} не вычисляет вершину
 * transaction chain и не является lifecycle authority.</p>
 *
 * <p><strong>Lifecycle и persistence.</strong> Transaction lifecycle и
 * physical storage lifecycle разделены. {@link #use(IMind, String)} допустим
 * только при закрытом storage; {@link #checkpoint(IMind)} фиксирует root state
 * без закрытия; {@link #close(IMind)} допустим только на transaction level 0 и
 * завершает physical storage lifecycle. Ни одна из этих операций не выполняет
 * неявный commit или rollback пользовательской транзакции.</p>
 *
 * <p><strong>Инварианты.</strong> Идентификатор пользователя не является
 * идентификатором Mind; currentMind не является опубликованной транзакцией;
 * изменение параметров пользователя не выполняет commit или rollback;
 * повторный use не закрывает уже открытый storage; close не уничтожает
 * незавершённую транзакцию.</p>
 */
public interface IUser {

    /**
     * Получить ранее установленный идентификатор пользователя.
     * КАНГЕР не контролирует идентификацию пользователей,
     * идентификатор в составе объекта служит как простое хранилище
     * значения предназначенное для внешнего контроля и обработок.
     *
     * @return ранее установленный идентификатор пользователя
     */
    long getId();

    /**
     * Установить идентификатор пользователя. КАНГЕР не контролирует
     * идентификацию пользователей, идентификатор в составе объекта
     * служит как простое хранилище значения предназначенное для
     * внешнего контроля и обработок.
     *
     * @param id идентификатор пользователя.
     */
    void setId(long id);

    /**
     * Получить параметр пользователя с указанным ключем. КАНГЕР
     * не контролирует параметры пользователя, коллекция параметров
     * в составе объекта служит как простое хранилище предназначенное для
     * внешних обработок, в том числе подключаемыми модулями.
     * <br>
     * Если на момент запроса параметра он не задан, то будет
     * использовано значение по умолчанию. При этом новое значение
     * будет добавлено в коллекцию параметров.
     * <br>
     * Если при этом параметром с ключем "user.dir" задан домашний
     * каталог пользователя, то в нем будет создан файл kanger.conf
     * в котором будут сохранены все текущие параметры.
     *
     * @param key          текстовый ключ запрашиваемого параметра.
     * @param defaultValue значение по умолчанию.
     * @return текстовое значение параметра.
     * @throws Exception если параметр нельзя прочитать или сохранить в пользовательской конфигурации
     */
    String getProperty(String key, String defaultValue) throws Exception;

    /**
     * Задать параметр пользователя. КАНГЕР не контролирует параметры
     * пользователя, коллекция параметров в составе объекта служит как простое
     * хранилище предназначенное для внешних обработок, в том числе
     * подключаемыми модулями.
     * <br>
     * Для удаления ключа нужно установить его значение в null.
     * <br>
     * Если в этот момент параметром с ключем "user.dir" задан домашний
     * каталог пользователя, то в нем будет создан файл kanger.conf
     * в котором будут сохранены все текущие параметры.
     *
     * @param key          текстовый ключ параметра.
     * @param defaultValue текстовое значение параметра.
     * @throws Exception если изменение нельзя сохранить в пользовательской конфигурации
     */
    void setProperty(String key, String defaultValue) throws Exception;

    /**
     * Загрузить параметры пользователя из ранее созданного файла
     * kanger.conf. Если параметр с ключем "user.dir" не задан
     * то операция игнорируется.
     *
     * @throws Exception если конфигурационный файл нельзя прочитать
     */
    void loadProperties() throws Exception;

    /**
     * Проверка наличия в списке параметров пользователя параметра
     * с указанным ключем.
     *
     * @param key текстовый ключ параметра.
     * @return true если параметр установлен.
     */
    boolean containsProperty(String key);

    /**
     * Получить путь к домашней папке пользователя. Возвращает
     * значение параметра с ключем "user.dir".
     *
     * @return домашняя папка пользователя.
     */
    String getUserDir();

    /**
     * Установить путь к домашней папке пользователя. Устанавливает
     * значение параметра с ключем "user.dir". Если устанавливается
     * не пустой путь то он должен оканчиваться символом файлового
     * разделителя.
     *
     * @param dir путь к домашней папке пользователя
     */
    void setUserDir(String dir);

    /**
     * Получить пусть к каталогу баз данных пользователя. Возвращает
     * значение параметра с ключем "database.dir". Параметр
     * рекомендуется устанавливать при работе с модулем файловой
     * базы данных.
     *
     * @return пусть к каталогу баз данных пользователя.
     */
    String getDatabaseDir();

    /**
     * Установить путь к каталогу баз данных пользователя. Устанавливает
     * значение параметра с ключем "database.dir". Если устанавливается
     * не пустой путь то он должен оканчиваться символом файлового
     * разделителя. Параметр рекомендуется устанавливать при работе с
     * модулем файловой базы данных.
     *
     * @param dir путь к каталогу баз данных пользователя.
     */
    void setDatabaseDir(String dir);

    /**
     * Получить путь к каталогу исходных текстов пользователя. Возвращает
     * значение параметра с ключем "sources.dir". КАНГЕР не контролирует
     * этот параметр, путь к каталогу исходных текстов в составе объекта
     * служит как простое хранилище значения предназначенное для
     * внешних обработок.
     *
     * @return путь к каталогу исходных текстов пользователя.
     */
    String getSourceDir();

    /**
     * Установить путь к каталогу баз данных пользователя. Устанавливает
     * значение параметра с ключем "sources.dir". Если устанавливается
     * не пустой путь то он должен оканчиваться символом файлового
     * разделителя. КАНГЕР не контролирует этот параметр, путь к каталогу
     * исходных текстов в составе объекта служит как простое хранилище
     * значения предназначенное для внешних обработок.
     *
     * @param dir путь к каталогу баз данных пользователя
     */
    void setSourceDir(String dir);

    /**
     * Открыть или создать physical storage для указанного Mind.
     *
     * <p>Операция допустима только когда storage пользователя закрыт. Уже
     * открытый storage, включая тот же target, не закрывается и не заменяется:
     * вызывающая сторона обязана сначала успешно выполнить
     * {@link #close(IMind)}. Rejection происходит до открытия target и не
     * изменяет active Mind или physical generation.</p>
     *
     * @param mind актуальный Mind; при {@code null} создаётся новый root Mind
     * @param name физическое имя storage
     * @return актуальный Mind, связанный с открытым storage
     * @throws Exception при ошибке storage или нарушении lifecycle precondition
     */
    IMind use(IMind mind, String name) throws Exception;

    /**
     * Выполнить durable checkpoint открытого storage без его закрытия.
     *
     * <p>Операция допустима только на transaction level 0. Реализация повторно
     * использует квалифицированный root-finalization path, включая
     * pack/update/flush ordering, но не выполняет compaction, close или
     * очистку runtime context.</p>
     *
     * @param mind актуальный root Mind
     * @return тот же актуальный Mind; storage остаётся открытым
     * @throws Exception при отсутствии открытого storage, активной транзакции
     * или ошибке durable publication
     */
    IMind checkpoint(IMind mind) throws Exception;

    /**
     * Закрыть physical storage без неявного решения транзакции.
     *
     * <p>При transaction level больше нуля операция отвергается до checkpoint,
     * flush, compaction, cleanup или physical close. На level 0 сначала
     * выполняется durable checkpoint, затем обычный physical close и очистка
     * storage-bound runtime state. Повторный close уже закрытого storage
     * является безопасной no-op.</p>
     *
     * @param mind актуальный Mind
     * @return актуальный root Mind после закрытия
     * @throws Exception при активной транзакции или ошибке checkpoint/close
     */
    IMind close(IMind mind) throws Exception;

    /**
     * Возвращает сохранённую приложением ссылку на текущий Mind.
     *
     * <p>Метод не вычисляет вершину transaction chain и не проверяет, что
     * объект ещё активен. Значение может быть {@code null}; ядро KANGER не
     * использует этот slot как источник истины lifecycle.</p>
     *
     * @return caller-managed ссылка на Mind или {@code null}
     */
    IMind getCurrentMind();

    /**
     * Сохраняет caller-managed ссылку на текущий Mind.
     *
     * <p>Операция не выполняет commit, release, reservation, storage
     * publication или cleanup. Передача {@code null} только очищает slot.
     * Вызывающая сторона отвечает за согласованность ссылки со своим
     * transaction workflow.</p>
     *
     * @param mind сохраняемый Mind или {@code null}
     */
    void setCurrentMind(IMind mind);

}
