package kanger.enums;

import kanger.compiler.Parser;
import kanger.exception.ParseErrorException;
import kanger.primitives.Argument;
import kanger.primitives.Domain;
import kanger.primitives.Function;
import kanger.primitives.TVariable;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Created by murray on 07.06.15.
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

    public static boolean isInterval(String ch) {
        String[] s = ch.split(" ");
        for (int i = 0; i < s.length; ++i) {
            if (i + 1 < s.length && isInt(s[i]) && Enums.INTERVALS.keySet().contains(s[i + 1].toLowerCase())) {
                ++i;
            } else {
                return false;
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
        pos = Parser.skipSpaces(line, pos);
        if (pos < line.length()) {
            int start = pos;
            if (line.charAt(pos) == 0) {
                return null;
            } else if (line.charAt(pos) == Enums.REM) {
                while (pos < line.length() && line.charAt(pos) != '\n' && line.charAt(pos) != '\r') {
                    ++pos;
                }
            } else {
                Object[] t;
                while ((t = Parser.getToken(line, pos)) != null && ((String) t[0]).charAt(0) != Enums.EOLN) {
                    pos = (int) t[1];
                }
                if (line.length() < pos || line.charAt(pos) != Enums.EOLN) {
                    throw new ParseErrorException(pos, ParseError.EOLN);
                }
                ++pos;
            }
            String s = line.substring(start, pos);
            return new Object[]{s, pos};
        } else {
            return null;
        }
    }

    public static List<TVariable> getTVariables(List<Argument> arg, boolean full) {
        List<TVariable> list = new ArrayList<>();
        for (Argument a : arg) {
            if (a.isTSet() && !list.contains(a.getT())) {
                list.add(a.getT());
            } else if (full && a.isFSet()) {
                List<TVariable> temp = getTVariables(a.getF().getArguments(), full);
                for (TVariable t : temp) {
                    if (!list.contains(t)) {
                        list.add(t);
                    }
                }
            }

        }
        return list;
    }

//    public static List<Function> getFunctions(List<Argument> arg) {
//        List<Function> list = new ArrayList<>();
//        for (Argument a : arg) {
//            if (a.isFSet() && !list.contains(a.getF())) {
//                list.add(a.getF());
//            }
//        }
//        return list;
//    }

}
