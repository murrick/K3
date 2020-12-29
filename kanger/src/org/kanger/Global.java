package org.kanger;

import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IData;
import org.kanger.units.SysOp;

import java.lang.reflect.InvocationTargetException;

public class Global {
    private static IData data = null;
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

    public static IData getData() throws RuntimeErrorException {
        if (data != null) {
            return data;
        } else {
            throw new RuntimeErrorException("DB module doesn't loaded");
        }
    }

    public static void setData(IData data) {
        Global.data = data;
    }

}
