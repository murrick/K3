package org.kanger;

import org.kanger.exception.RuntimeErrorException;
import org.kanger.units.SysOp;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public class Global {
    private static Class udf = null;

    public static SysOp getUdf() throws RuntimeErrorException {
        if (udf != null) {
            try {
                return (SysOp) udf.getConstructors()[0].newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeErrorException(e.toString());
            }
        } else {
            throw new RuntimeErrorException("UDF module doesn't loaded");
        }
    }

    public static void setUdf(Class udf) {
        Global.udf = udf;
    }

}
