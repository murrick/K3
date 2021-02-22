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

package org.kanger.enums;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by Dmitry G. Qusnetsov on 28.05.15.
 */
public class Enums {

    public static final int CON = '&';        /* Конъюнкция AND */
    public static final int DIS = '|';        /* Дизъюнкция OR */
    public static final int NOT = '~';        /* Отрицание NOT */
    public static final int IMP = '}';        /* Импликация IF-THEN */
    public static final int AQN = '@';        /* Квантор общности FOR ALL */
    public static final int PQN = '$';        /* Квантор существования PRESENT */

    public static final int SUC = '?';        /* Сукцедент */
    public static final int ANT = '!';        /* Антецедент */

    public static final int INS = '+';        /* Insert database character */
    public static final int DEL = '-';        /* Delete database character */
    public static final int WIPE = '#';       /* Wipe database character */
    public static final int FOO = '=';       /* Implement function */

    public static final int LB = '(';         /* Левая скобка */
    public static final int RB = ')';         /* Правая скобка */

    public static final int CVC = '%';        /* Символ для с-переменных */
    public static final int TVC = '#';        /* Символ для т-переменных */
    public static final int XVC = '*';        /* Символ для cc-переменных */

    //    public static final int REM = '*';        /* Коментарий */
    public static final int COMMA = ',';
    public static final int EOLN = ';';

    public static final long INTERVAL_MILLISECOND = 1L;
    public static final long INTERVAL_SECOND = 1000L;
    public static final long INTERVAL_MINUTE = 1000L * 60L;
    public static final long INTERVAL_HOUR = 1000L * 60L * 60L;
    public static final long INTERVAL_DAY = 1000L * 60L * 60L * 24L;
    public static final long INTERVAL_WEEK = 1000L * 60L * 60L * 24L * 7;
    public static final long INTERVAL_MONTH = -1L;
    public static final long INTERVAL_YEAR = -2L;

    public static final Map<String, Long> INTERVALS = new LinkedHashMap<String, Long>() {
        {
            put("..", 0L);
        }

        {
            put("ms", INTERVAL_MILLISECOND);
        }

        {
            put("msec", INTERVAL_MILLISECOND);
        }

        {
            put("msecs", INTERVAL_MILLISECOND);
        }

        {
            put("millisecond", INTERVAL_MILLISECOND);
        }

        {
            put("milliseconds", INTERVAL_MILLISECOND);
        }

        {
            put("sec", INTERVAL_SECOND);
        }

        {
            put("secs", INTERVAL_SECOND);
        }

        {
            put("second", INTERVAL_SECOND);
        }

        {
            put("seconds", INTERVAL_SECOND);
        }


        {
            put("min", INTERVAL_MINUTE);
        }

        {
            put("mns", INTERVAL_MINUTE);
        }

        {
            put("mins", INTERVAL_MINUTE);
        }

        {
            put("minute", INTERVAL_MINUTE);
        }

        {
            put("minutes", INTERVAL_MINUTE);
        }

        {
            put("hs", INTERVAL_HOUR);
        }

        {
            put("hr", INTERVAL_HOUR);
        }

        {
            put("hrs", INTERVAL_HOUR);
        }

        {
            put("hour", INTERVAL_HOUR);
        }

        {
            put("hours", INTERVAL_HOUR);
        }

        {
            put("dy", INTERVAL_DAY);
        }

        {
            put("ds", INTERVAL_DAY);
        }

        {
            put("day", INTERVAL_DAY);
        }

        {
            put("days", INTERVAL_DAY);
        }

        {
            put("wk", INTERVAL_WEEK);
        }

        {
            put("wks", INTERVAL_WEEK);
        }

        {
            put("week", INTERVAL_WEEK);
        }

        {
            put("weeks", INTERVAL_WEEK);
        }

        {
            put("mn", INTERVAL_MONTH);
        }

        {
            put("mon", INTERVAL_MONTH);
        }

        {
            put("mons", INTERVAL_MONTH);
        }

        {
            put("month", INTERVAL_MONTH);
        }

        {
            put("months", INTERVAL_MONTH);
        }

        {
            put("yr", INTERVAL_YEAR);
        }

        {
            put("year", INTERVAL_YEAR);
        }

        {
            put("years", INTERVAL_YEAR);
        }
    };

    public static final int DEBUG_LEVEL_QUIET = 0;
    public static final int DEBUG_LEVEL_DEBUG = 1;

    public static final int DEBUG_OPTION_VALUES = 0x100;
    public static final int DEBUG_OPTION_STATUS = 0x200;
    //    public static final int DEBUG_OPTION_RULES = 0x400;
    public static final int DEBUG_OPTION_RTLOGS = 0x800;

    public static final String FILE_SEPARATOR = System.getProperty("file.separator");
    public static final String LINE_SEPARATOR = System.getProperty("line.separator");
}
