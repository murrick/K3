package org.kanger;

import org.kanger.interfaces.IData;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;


/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 */
public class Kanger {


    public static void main(String[] args) throws Exception {

        IData db = new DB();
        IUser user = new User(db, UDF.class);

        Screen.session(user);
    }
}
