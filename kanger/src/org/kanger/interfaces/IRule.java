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

import java.util.Set;

/**
 * Описатель правила или утверждения. Правило имеет признак
 * отношения к сукцеденту или антецеденту и может содержать
 * множество предикатов, пропозициональных связок и кванторов.
 * Утверждение это правило, содержащее единственный предикат и
 * признак отношения к сукцеденту или антецеденту.
 * или антецеденту.
 */
public interface IRule {

    /**
     * Получить идентификатор правила.
     *
     * @return идентификатор правила.
     */
    long getId();

    /**
     * Получить исходное текстовое представление правила.
     *
     * @return текстовое представление правила.
     * @throws Exception
     */
    String getOrigin() throws Exception;

    /**
     * Признак того что правило является производным утверждением,
     * полученным в процессе вывода.
     *
     * @return true - правило является производным.
     */
    boolean isGenerated();

    /**
     * Признак того что правило является или относится к запросу
     * инициированному методом IMind.query().
     *
     * @return true - правило относится к запросу.
     */
    boolean isQuery();

    /**
     * Признак того что правило помечено для удаления на указанном
     * уровне транзакции.
     *
     * @param mind уровень транзакции.
     * @return true если правило помечено для удаления.
     */
    boolean isDeleted(IMind mind);

    /**
     * Признак того что правило было восстановлено на указанном
     * уровне транзакции. Это может означать что правило, помеченное
     * для удаления на низших уровнях транзакции, было повторно
     * введено или продучировано на указанном уровне.
     *
     * @param mind уровень транзакции.
     * @return true если правило было восстановлено.
     */
    boolean isRestored(IMind mind);

    /**
     * Признак того что правило содержит t-переменные, в которые
     * может быть осуществлена подстановка в процессе вывода.
     *
     * @return true если правило содержит t-переменные.
     */
    boolean isSubstitutable();

    /**
     * Признак того что правило содержит u-переменные.
     *
     * @return true если правило содержит u-переменные.
     */
    boolean isAbstractive();

    /**
     * Признак того что правило является утверждением.
     *
     * @return true если правило является утверждением.
     */
    boolean isStored();

    /**
     * Признак принадлежности правила к антецеденту или сукцеденту.
     *
     * @return true если антецедент, false - сукцедент.
     * @throws Exception
     */
    boolean isAntc() throws Exception;

    /**
     * Получить описатель предиката для правила являющегося
     * утверждением. Если правилло не является утверждением то
     * возникнет ошибка времени выполнения.
     *
     * @return описатель предиката утверждения.
     * @throws Exception
     */
    IPredicate getPredicate() throws Exception;

    /**
     * Получить список аргументов утверждения. Если правилло не
     * является утверждением то возникнет ошибка времени выполнения.
     *
     * @return список аргументов утверждения.
     * @throws Exception
     */
    IList getArguments() throws Exception;

    /**
     * Получить множество узлов дерева вывода, явившихся основанием
     * для появления текущего утверждения.
     *
     * @return множество узлов дерева вывода.
     */
    Set<ICause> getCauses();

    /**
     * Получить текст комментария для правила. Текст будет
     * содержать ключевые символы // или /&#42; и &#42;/.
     *
     * @return текст комментария для правила.
     * @throws Exception
     */
    String getComment() throws Exception;

    /**
     * Установить текст комментария для правила. Текст должен быть
     * корректно сформирован с использованием ключевых
     * символов // или /&#42; и &#42;/.
     *
     * @param comment текст комментария для правила
     * @throws Exception
     */
    void setComment(String comment) throws Exception;
}
