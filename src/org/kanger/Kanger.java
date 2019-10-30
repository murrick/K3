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

//        List<Object> params = new ArrayList<>();
//        params.add(1);
//        params.add(18);
//        Boolean res = user.getMind().query("?$x x + ? = ?;", params);
//        if(res) {
//            for(Map<String,Object> m : user.getMind().getValues()) {
//                for(Map.Entry<String,Object> e : m.entrySet()) {
//                    System.out.print(e.getKey() + "=" + e.getValue() + "\t");
//                }
//                System.out.println();
//            }
//        }


        Screen.session(user);
    }
}
