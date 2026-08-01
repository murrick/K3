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
 * Внешний пользовательский контекст KANGER.
 *
 * <p>Интерфейс объединяет три совместимые, но независимые роли: внешний
 * идентификатор пользователя, сохраняемый набор строковых параметров и
 * caller-managed ссылку на текущий {@link IMind}. Ядро KANGER не выполняет
 * аутентификацию и не придаёт {@link #getId()} глобальной семантики: значение
 * хранится для приложения, storage-модуля или иной внешней интеграции.</p>
 *
 * <p>Параметры принадлежат объекту пользователя и могут сохраняться в
 * {@code kanger.conf}, если задан {@code user.dir}. Методы чтения с default
 * имеют побочный эффект: отсутствующее значение регистрируется и может быть
 * записано в конфигурационный файл. Поэтому этот интерфейс не является
 * неизменяемым property view.</p>
 *
 * <p>{@code currentMind} — compatibility slot, управляемый вызывающей
 * стороной. Он не является lifecycle authority, владельцем storage,
 * указателем опубликованной транзакции или заменой {@link IMind#commit(IMind)}
 * и {@link IMind#release(IMind)}. Приложение обязано само поддерживать ссылку
 * в соответствии со своим transaction workflow.</p>
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
     */
    void setProperty(String key, String defaultValue) throws Exception;

    /**
     * Загрузить параметры пользователя из ранее созданного файла
     * kanger.conf. Если параметр с ключем "user.dir" не задан
     * то операция игнорируется.
     *
     * @throws Exception
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
