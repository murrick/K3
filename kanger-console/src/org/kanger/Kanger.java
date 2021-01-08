package org.kanger;

import org.kanger.interfaces.IData;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;


/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 */
public class Kanger {


    public static void main(String[] args) throws Exception {

        IData db = null;
        Class udf = null;

        String confName = System.getProperty("user.home") + "K3.conf";
        if (new File(confName).exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(confName))) {
                String sCurrentLine;
                while ((sCurrentLine = br.readLine()) != null) {
                    if (sCurrentLine.split("\\=").length == 2) {
                        System.setProperty(sCurrentLine.split("\\=")[0], sCurrentLine.split("\\=")[1]);
                    }
                }
            }
        }


        try {
            db = new DB();
            db.init();
        } catch (NoClassDefFoundError ex) {
        }

        try {
            udf = UDF.class;
        } catch (NoClassDefFoundError ex) {
        }

        Global.setData(db);
        Global.setUdf(udf);

        IUser user = new User();

        Runtime.getRuntime().addShutdownHook(new ShutdownHook(user));

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
