package kanger;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * $Rev: 60 $ $Date: 2017-01-19 11:24:18 +0300 (Thu, 19 Jan 2017) $
 * $Revision: 60 $
 * $Author: murray $
 * 
 *
 * @author murray
 */
public abstract class Version {

    public static final int VERSION = 3;
    public static final int RELEASE = 0;
    public static final int REVISION = Integer.parseInt("$LastChangedRevision: 60 $".replace("$LastChangedRevision:", "").replace("$", "").trim());
    public static final Date DATE = parseDate("$LastChangedDate: 2017-01-19 11:24:18 +0300 (Thu, 19 Jan 2017) $".replace("$LastChangedDate:", "").replace("$", "").trim());
    public static final int YEAR = getYear(DATE);
    public static final int VERSION_B = (VERSION << 24) | (RELEASE << 16) | REVISION;
    public static final String VERSION_S = String.format("%d.%d.%d", VERSION, RELEASE, REVISION);
    public static final String DATE_S = formatDate(DATE);

    private static String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(date);
    }

    private static int getYear(Date date) {
        return Integer.parseInt(new SimpleDateFormat("yyyy").format(date));
    }

    private static Date parseDate(String date) {
        Date d = null;
        try {
            d = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse(date);
        } catch (ParseException ex) {
            //
        }

        return d;
    }
}

//////////