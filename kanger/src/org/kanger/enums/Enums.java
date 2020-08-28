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

    public static final int REM = '*';        /* Коментарий */
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
            put("wks", INTERVAL_WEEK);
        }

        {
            put("week", INTERVAL_WEEK);
        }

        {
            put("weeks", INTERVAL_WEEK);
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
    public static final int DEBUG_LEVEL_INFO = 1;
    public static final int DEBUG_LEVEL_DEBUG = 2;

    public static final int DEBUG_OPTION_VALUES = 0x100;
    public static final int DEBUG_OPTION_STATUS = 0x200;
    public static final int DEBUG_OPTION_RIGHTS = 0x400;
    public static final int DEBUG_OPTION_RTLOGS = 0x800;
    public static final int DEBUG_OPTION_RVALUES = 0x1000;
}
