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
 * Коментарий для правила или утверждения. Иденитификатор комментария должен
 * сооьвеьствовать идентификатору правила или утверждения. Если идентификатор
 * правила установлен в -2L то комментарий будет установлен в качестве заголовка
 * исходного текста. Если в -3L - то в качестве завершающего комментария.
 * <pre>
 *
 * Конструктор для комментария выглядит так:
 * public Comment(long id, String comment, IMind mind);
 *
 * Где
 * id - иденификатор правила или утверждения,
 * comment - текст комментария,
 * mind - текущий уровень транзакции
 * </pre>
 */
public interface IComment {

    /**
     * Получить идентификатор комментария.
     *
     * @return Идентификатор правила или утверждения, с которым связан
     * комментарий.
     */
    long getId();

    /**
     * Получить текст комментария.
     *
     * @return Текст комментария
     */
    String getComment();

    /**
     * Установить текст комментария. Для удаления комментария нужно
     * указать пустую строку.
     *
     * @param comment Новый текст комментария.
     */
    void setComment(String comment);

}
