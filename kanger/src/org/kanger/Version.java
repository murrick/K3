package org.kanger;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public abstract class Version {

    public static final int VERSION = 3;
    public static final int RELEASE = 2;
    public static final String REVISION = "5803";
    public static final String DATE = "2021-02-19_16:41:42";
    public static final int YEAR = getYear(parseDate(DATE));
    public static final int VERSION_CODE = ((VERSION & 0xFF) << 8) | (RELEASE & 0xFF);
    public static final String VERSION_S = String.format("%d.%d.%s", VERSION, RELEASE, REVISION);
    public static final String DATE_S = formatDate(parseDate(DATE));

    private static String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(date);
    }

    private static int getYear(Date date) {
        return Integer.parseInt(new SimpleDateFormat("yyyy").format(date));
    }

    private static Date parseDate(String date) {
        Date d = null;
        try {
            d = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").parse(date);
        } catch (ParseException ex) {
            //
        }

        return d;
    }
}

//////////