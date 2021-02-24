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

import java.util.Collection;
import java.util.Map;

/**
 * Интерфейс описателя транзакции. Все взаимодействие с КАНГЕР происходит через
 * этот интерфейс. Взаимодействие начинается с создания транзакции нулевого
 * уровня, или корневой транзакции, с помошью конструктора
 * <pre>
 *
 *     IMind rootLevel = new Mind(IUser user); </pre>
 * Для создания транзакций следующих уровней используется конструктор
 * <pre>
 *
 *     IMind level = new Mind(IMind previousLevel);  </pre>
 * Последовательность транзакций выстраивается в одно-свяханный список.
 * Уровень транзакции находящейся на вершине списка является текущим
 * уровнем.
 * <pre>
 *
 *     +----------------+
 *     | Current level  |
 *     +----------------+
 *              &darr;
 *             ...
 *              &darr;
 *     +----------------+
 *     | Level 1        |
 *     +----------------+
 *              &darr;
 *     +----------------+
 *     | Root level     |
 *     +----------------+ </pre>
 */
public interface IMind {

    /**
     * Получить описатель пользователя, с которым связан список транзакций.
     *
     * @return описатель пользователя.
     */
    IUser getUser();

    /**
     * Получить идентификатор транзакции.
     *
     * @return идентификатор транзакции.
     */
    long getId();

    /**
     * Получить описатель следующей транзакции относительно текущей
     * в списке.
     *
     * @return описатель следующей транзакции в списке, или null если
     * текущая транзакция яволяется корневой (имеет нулевой уровень).
     */
    IMind getNext();

    /**
     * Получить корневую транзакцию в списке относительно текущей.
     *
     * @return описатель корневой (имеющей нулевой уровень) транзакции относительно текущей.
     */
    IMind getTop();

    /**
     * Инициировать запрос КАНГЕР. Строка параметра может содержать только один запрос. Если
     * результат вывода true - возможно получение решений с использованием метода getSolutions()
     * и значений результатов с использованием getValues(). Если запрос неопределен то возможно
     * получение списка гипотез методом getHypothesis(). Например:
     * <pre>
     *     IMind mind = new Mind(user);
     *     Boolean res;
     *     res = mind.query("!@x @y father(x,y) -> child(y,x);");
     *     res = mind.query("?$x child(x, John);"); </pre>
     *
     * @param query запрос КАНГЕР в виде текстовой строки.
     * @return результат вывода true, false или null в случае неопределенного результата.
     * @throws Exception
     */
    Boolean query(String query) throws Exception;

    /**
     * Инициировать запрос КАНГЕР и передать ему параметры. Строка параметра может содержать
     * только один запрос. Параметры передаются в виде массива элементов произвольного
     * типа. Строка запроса, начиная с первого символа может содержать символы ?
     * (вопростительный знак) которые при компиляции последовательно заменяются элементами массива.
     * Если результат вывода true - возможно получение решений с использованием метода getSolutions()
     * и значений результатов с использованием getValues(). Если запрос неопределен то возможно
     * получение списка гипотез методом getHypothesis(). Например:
     * <pre>
     *     IMind mind = new Mind(user);
     *     Boolean res = mind.query("!age(?, ?);", new Object[]{"John", 37}); </pre>
     *
     * @param query запрос КАНГЕР в виде текстовой строки, содержащей символы ? (вопросительный
     *              знак) для подстановки параметров из массива.
     * @param ext   массив элементов произвольного типа, которые при комптляции запроса будут
     *              последовательно подствалены вместо символов ? (вопросительный знак)
     * @return результат вывода true, false или null в случае неопределенного результата.
     * @throws Exception
     */
    Boolean query(String query, Object[] ext) throws Exception;

    /**
     * Компиляция текста программы. Строка параметра может содержать множество выражений
     * на языке КАНГЕР и комментариев. Все выражения при компиляции воспринимаются как правила
     * и утверждения, обработка запросов не производится. Если текущее состояние КАНГЕР уже сожержит
     * информацию, будет предпринята попытка объединения новой информации с уже существующей. Если
     * компилируемая программа содеожит инфлрмацию противоречащую имеющейся - весь компилируемый текст
     * целиком будет отвергнут. Например:
     * <pre>
     *     boolean rc = mind.compile("!@x @y father(x,y) -> child(y,x);   // This is new rule \n"
     *                             + "?female(John);                      // John is not female");</pre>
     *
     * @param source текст программы на языке КАНГЕР.
     * @return true если текст принят, false если обнаружены конфликты или обнаружены ошибки.
     * @throws Exception
     */
    boolean compile(String source) throws Exception;

    /**
     * Применить все изменения ранее созданной транзакции. Пример:
     * <pre>
     *     IMind m = new Mind(current);
     *     ... операции на уровне транзакции m
     *     current.commit(m);
     *
     *     +----------------+
     *     | !b;            |
     *     +----------------+
     *              &darr;             +----------------+
     *     +----------------+     | !b;            |
     *     | !a;            |  &rarr;  | !a;            |
     *     +----------------+     +----------------+</pre>
     * Операция является потокобезопасной, т.к. на случай если после создания транзакции m
     * на уровне транзакции current произошли изменения, производится проверка на
     * противоречия и блокировка дублирования правил и утверждений. При любом результате
     * проверки текущей транзакцией становится current. Транзакция m удаляется из памяти.
     *
     * @param m ранее созданная на базе текущей транзакция
     * @return true если изменения были применены успешно, false если изменения были отвергнуты.
     * @throws Exception
     */
    boolean commit(IMind m) throws Exception;

    /**
     * Откатить все изменения ранее созданной транзакции. Пример:
     * <pre>
     *     IMind m = new Mind(current);
     *     ... операции на уровне транзакции m
     *     current.release(m);
     *
     *     +----------------+
     *     | !b;            |
     *     +----------------+
     *              &darr;
     *     +----------------+     +----------------+
     *     | !a;            |  &rarr;  | !a;            |
     *     +----------------+     +----------------+</pre>
     * Операция является потокобезопасной. Текущей транзакцией становится current.
     * Транзакция m удаляется из памяти.
     *
     * @param m ранее созданная на базе текущей транзакция
     * @throws Exception
     */
    void release(IMind m) throws Exception;


    IFactory<ITerm> getTerms();

    IFactory<IPredicate> getPredicates();

    IFactory<IRule> getRules();

    IFactory<IComment> getComments();

    IFactory<IOperation> getLibrary();

    IFactory<IHypothesis> getHypothesis();

    IFactory<Map<String, ITerm>> getValues();

    IFactory<IRule> getSolutions();

    IFactory<ILogEntry> getLog();


    String getSourceFileName();

    void setSourceFileName(String name);

    String getSourceCode() throws Exception;

    String getCompliedString();

    String getQueryString();

    Object getQueryResult();

    IRule getAcceptedRule();


    String getVersion();

    int getDebugLevel();

    void setDebugLevel(int debugLevel);

    int getFloodControlLimit();

    void setFloodControlLimit(int floodControlLimit);


    int getTransactionLevel();

    boolean isEmptyLevel();


    boolean isStorageUsed();

    String getStorageName();

    Collection<String> getStoragesList();

    IMind useStorage(String name) throws Exception;

    IMind closeStorage() throws Exception;

    IMind clearStorage() throws Exception;

    IMind reindexStorage(String name) throws Exception;

    IMind reindexStorage(String name, IReactor reactor) throws Exception;

    IMind removeStorage(String name) throws Exception;


    String getOrder();

    void setOrder(String order);

    boolean isAscending();

    void setAscending(boolean ascending);
}
