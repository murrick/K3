package kanger.enums;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by murray on 28.05.15.
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

    public static final long INTERVAL_MONTH = -1L;
    public static final long INTERVAL_YEAR = -2L;
    public static final Map<String, Long> INTERVALS = new LinkedHashMap<String, Long>() {
        {
            put("ms", 1L);
        }

        {
            put("millisecond", 1L);
        }

        {
            put("milliseconds", 1L);
        }

        {
            put("sec", 1000L);
        }

        {
            put("secs", 1000L);
        }

        {
            put("second", 1000L);
        }

        {
            put("seconds", 1000L);
        }


        {
            put("min", 1000L * 60L);
        }

        {
            put("mins", 1000L * 60L);
        }

        {
            put("minute", 1000L * 60L);
        }

        {
            put("minutes", 1000L * 60L);
        }

        {
            put("hr", 1000L * 60L * 60L);
        }

        {
            put("hrs", 1000L * 60L * 60L);
        }

        {
            put("hour", 1000L * 60L * 60L);
        }

        {
            put("hours", 1000L * 60L * 60L);
        }

        {
            put("dy", 1000L * 60L * 60L * 24L);
        }

        {
            put("day", 1000L * 60L * 60L * 24L);
        }

        {
            put("days", 1000L * 60L * 60L * 24L);
        }

        {
            put("week", 1000L * 60L * 60L * 24L * 7);
        }

        {
            put("weeks", 1000L * 60L * 60L * 24L * 7);
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

}
