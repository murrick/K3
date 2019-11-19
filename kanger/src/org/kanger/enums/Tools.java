package org.kanger.enums;

import org.kanger.compiler.Parser;
import org.kanger.exception.ParseErrorException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by Dmitry G. Qusnetsov on 07.06.15.
 */
public abstract class Tools {


    public static double round(double n, int c) {
        double i;
        double k;
        double r;

        k = Math.pow(10, (double) c);
        i = n * k * 100;
        r = i % 100;
        i /= 100;
        if (n >= 0) {
            if (r >= 50) {
                i = Math.ceil(i);
            } else {
                i = Math.floor(i);
            }
        } else {
            if (r <= -50) {
                i = Math.floor(i);
            } else {
                i = Math.ceil(i);
            }
        }

        return i /= k;
    }

    public static boolean isKey(int ch) {
        return (ch == Enums.LB || ch == Enums.RB || ch == Enums.NOT || ch == Enums.CON || ch == Enums.DIS
                || ch == Enums.IMP || ch == Enums.PQN || ch == Enums.AQN || ch == Enums.SUC || ch == Enums.ANT
                || ch == Enums.CVC || ch == Enums.TVC || ch == Enums.COMMA || ch == Enums.EOLN || ch == ' ');
    }

    //    public static String cutBraces(String a) {
//        if (a.length() > 1 && ((a.charAt(0) == '\"' && a.charAt(a.length() - 1) == '\"') || (a.charAt(0) == '\'' && a.charAt(a.length() - 1) == '\''))) {
//            a = a.substring(1, a.length() - 1);
//        }
//        return a;
//    }

    public static boolean isBlob(String ch) {
        if (!ch.isEmpty() && ch.charAt(0) == '#') {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isNum(String ch) {
        return ch.length() > 0 && (Character.isDigit(ch.charAt(0))
                || (ch.length() > 1 && ch.charAt(0) == '-' && Character.isDigit(ch.charAt(1)))
                || (ch.length() > 1 && ch.charAt(0) == '+' && Character.isDigit(ch.charAt(1))));
    }

    public static boolean isFloat(String ch) {
        return isNum(ch) && ch.contains(".");
    }

    public static boolean isInt(String ch) {
        return isNum(ch) && !ch.contains(".");
    }

    //TODO !!!добавить численные/произвольные интервалы и множества. Операции IN, ~IN, +, -, ()
    //
    public static boolean isInterval(String ch) {
        if (ch.contains("..") && ch.charAt(0) != Enums.ANT && ch.charAt(0) != Enums.SUC) {
            return ch.split("\\.\\.").length == 2;
        } else {
            String[] s = ch.split(" ");
            for (int i = 0; i < s.length; ++i) {
                if (i + 1 < s.length && isInt(s[i]) && Enums.INTERVALS.keySet().contains(s[i + 1].toLowerCase())) {
                    ++i;
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    public static Date parseDate(String ch) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z").parse(ch);
        } catch (ParseException ex) {
            try {
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").parse(ch);
            } catch (ParseException e1) {
                try {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(ch);
                } catch (ParseException e2) {
                    try {
                        return new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(ch);
                    } catch (ParseException e3) {
                        try {
                            return new SimpleDateFormat("yyyy-MM-dd").parse(ch);
                        } catch (ParseException e4) {
                            return null;
                        }
                    }
                }
            }
        }
    }

    public static Date dateAdd(Date d, String interval, int invertor) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(d.getTime());
        String[] s = interval.split(" ");
        for (int i = 0; i < s.length; ++i) {
            if (i + 1 < s.length && isInt(s[i]) && Enums.INTERVALS.keySet().contains(s[i + 1].toLowerCase())) {
                long val = Long.parseLong(s[i]) * invertor;
                long inv = Enums.INTERVALS.get(s[i + 1].toLowerCase());
                if (inv > 0) {
                    c.add(Calendar.MILLISECOND, (int) (inv * val));
                } else if (inv == Enums.INTERVAL_MONTH) {
                    c.add(Calendar.MONTH, (int) val);
                } else if (inv == Enums.INTERVAL_YEAR) {
                    c.add(Calendar.YEAR, (int) val);
                }
            }
        }
        return c.getTime();
    }

    public static String dateDiff(Date a, Date b) {
        int months = 0;
        int years = 0;
        int days = 0;
        int hours = 0;
        int minutes = 0;
        int seconds = 0;
        int ms = 0;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(a.getTime());
        if (c.before(b)) {
            while (c.before(b)) {
                ++years;
                c.add(Calendar.YEAR, 1);
            }
        } else {
            while (c.after(b)) {
                ++years;
                c.add(Calendar.YEAR, -1);
            }
        }
        c.setTimeInMillis(a.getTime());
        if (c.before(b)) {
            c.add(Calendar.YEAR, years);
            while (c.before(b)) {
                ++months;
                c.add(Calendar.MONTH, 1);
            }
        } else {
            c.add(Calendar.YEAR, -years);
            while (c.after(b)) {
                ++months;
                c.add(Calendar.MONTH, -1);
            }
        }
        if (c.before(b)) {
            c.add(Calendar.YEAR, years);
            c.add(Calendar.MONTH, months);
        } else {
            c.add(Calendar.YEAR, -years);
            c.add(Calendar.MONTH, -months);
        }
        long diff = Math.abs(b.getTime() - c.getTimeInMillis());
        days = (int) (diff / Enums.INTERVALS.get("days"));
        diff -= days * Enums.INTERVALS.get("days");
        hours = (int) (diff / Enums.INTERVALS.get("hours"));
        diff -= hours * Enums.INTERVALS.get("hours");
        minutes = (int) (diff / Enums.INTERVALS.get("minutes"));
        diff -= minutes * Enums.INTERVALS.get("minutes");
        seconds = (int) (diff / Enums.INTERVALS.get("seconds"));
        diff -= seconds * Enums.INTERVALS.get("seconds");
        ms = (int) diff;

        String ret = "";
        if (years > 0) {
            ret += "" + years + (years > 1 ? " years " : " year ");
        }
        if (months > 0) {
            ret += "" + months + (months > 1 ? " months " : " month ");
        }
        if (days > 0) {
            ret += "" + days + (days > 1 ? " days " : " day ");
        }
        if (hours > 0) {
            ret += "" + hours + (hours > 1 ? " hours " : " hour ");
        }
        if (minutes > 0) {
            ret += "" + minutes + (minutes > 1 ? " minutes " : " minute ");
        }
        if (seconds > 0) {
            ret += "" + seconds + (seconds > 1 ? " seconds " : " second ");
        }
        if (ms > 0) {
            ret += "" + ms + " ms";
        }

        return ret.trim();
    }

    public static Object[] extractLine(String line, int pos) throws ParseErrorException {
//        pos = Parser.skipSpaces(line, pos);
        int start = -1;
        if (line.length() <= pos) {
            return null;
        } else {
            Object[] t;
            while ((t = Parser.getToken(line, pos)) != null && ((String) t[0]).charAt(0) != Enums.EOLN) {
                pos = (int) t[1];
                if (start == -1) {
                    start = pos - ((String) t[0]).length();
                }
            }
            if (t == null && start == -1) {
                return null;
            }
            if (line.length() < pos || (line.length() != 1 && line.charAt(pos) != Enums.EOLN)) {
                throw new ParseErrorException(pos, ParseError.EOLN);
            } else if (line.length() > 1) {
                ++pos;
            }
        }
        String s = line.substring(start, pos);
        return new Object[]{s, pos};
    }

//    public static List<TVariable> getTVariables(ArgList arg, boolean full) {
//        List<TVariable> list = new ArrayList<>();
//        for (Argument a : arg) {
//            if (a.isTSet() && !list.contains(a.getT())) {
//                list.add(a.getT());
//            } else if (full && a.isFSet()) {
//                List<TVariable> temp = getTVariables(a.getF().getArguments(), full);
//                for (TVariable t : temp) {
//                    if (!list.contains(t)) {
//                        list.add(t);
//                    }
//                }
//            }
//
//        }
//        return list;
//    }
//
//    public static List<TValue> getTValues(ArgList arg, boolean full) {
//        List<TValue> list = new ArrayList<>();
//        for (Argument a : arg) {
//            if (a.isTSet() && !a.isEmpty() && !list.contains(a.getT().getCurrent())) {
//                list.add(a.getT().getCurrent());
//            } else if (a.isVSet() && !list.contains(a.getV())) {
//                list.add(a.getV());
//            } else if (full && a.isFSet()) {
//                List<TValue> temp = getTValues(a.getF().getArguments(), full);
//                for (TValue t : temp) {
//                    if (!list.contains(t)) {
//                        list.add(t);
//                    }
//                }
//            } else if (full && a.isRSet()) {
//                List<TValue> temp = getTValues(a.getR().getCondition(), full);
//                for (TValue t : temp) {
//                    if (!list.contains(t)) {
//                        list.add(t);
//                    }
//                }
//            }
//
//        }
//        return list;
//    }

//    public static List<Function> getFunctions(ArgList arg) {
//        List<Function> list = new ArrayList<>();
//        for (Argument a : arg) {
//            if (a.isFunction() && !list.contains(a.getFunction())) {
//                list.createTVar(a.getFunction());
//            }
//        }
//        return list;
//    }

}
