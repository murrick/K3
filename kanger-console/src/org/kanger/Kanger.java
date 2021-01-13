package org.kanger;

import org.kanger.exception.AuthenticationErrorException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IData;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 */
public class Kanger {


    public static void main(String[] args) throws Exception {

        String newlogin = null;
        String login = null;
        String password = null;
        boolean singleUser = false;

        for (int i = 0; i < args.length; ++i) {
            if ((args[i].equals("--adduser") || args[i].equals("-A")) && args.length > i + 1) {
                newlogin = args[++i];
            } else if ((args[i].equals("--user") || args[i].equals("-U")) && args.length > i + 1) {
                login = args[++i];
            } else if ((args[i].equals("--password") || args[i].equals("-P")) && args.length > i + 1) {
                password = args[++i];
            } else if (args[i].equals("--singleuser") || args[i].equals("-S")) {
                singleUser = true;
            }
        }

        if (newlogin != null) {
            try {
                if(password == null) {
                    throw new AuthenticationErrorException("Password must be defined");
                }
                User.createUser(newlogin, password);
                System.exit(1);
            } catch (Exception ex) {
                ex.printStackTrace(System.err);
            }
        }

        if(singleUser) {
            login = "singleuser";
            password = "singleuser";
            try {
                User.createUser(login, password);
            } catch (Exception ex) {
                //
            }
        }
//        IData db = null;
//        Class udf = null;

//        String confName = System.getProperty("user.home") + "K3.conf";
//        if (new File(confName).exists()) {
//            try (BufferedReader br = new BufferedReader(new FileReader(confName))) {
//                String sCurrentLine;
//                while ((sCurrentLine = br.readLine()) != null) {
//                    if (sCurrentLine.split("\\=").length == 2) {
//                        System.setProperty(sCurrentLine.split("\\=")[0], sCurrentLine.split("\\=")[1]);
//                    }
//                }
//            }
//        }


//        try {
//            udf = UDF.class;
//            Global.setUdf(udf);
//        } catch (NoClassDefFoundError ex) {
//        }
//        try {
//            db = new DB();
//            db.init(user);
//        } catch (NoClassDefFoundError ex) {
//        }
//
//        Runtime.getRuntime().addShutdownHook(new ShutdownHook(user));

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

        if(login == null) {
            Kanger k = new Kanger();
            Screen.showCopyrigt();
            login = k.readLine("login: ");
            password = new String(k.readPassword("password: "));
        }

        IUser user = new User(login, password);

        Screen.showCopyrigt();

        IData db = null;
        Class udf = null;
        try {
            udf = UDF.class;
            Global.setUdf(udf);
            System.out.println("UDF module loaded");
        } catch (NoClassDefFoundError ex) {
        }
        try {
            db = new DB();
            db.init(user);
            System.out.println("DB module loaded: " + user.getData().getDescription());
        } catch (NoClassDefFoundError ex) {
        }

        System.out.println("Current user: " + login);

        Runtime.getRuntime().addShutdownHook(new ShutdownHook(user));

        Mind mind = new Mind(user);
        //TODO: Волшебство
        mind.query("?a;");
        mind.clear();

        Screen.session(mind);
    }

    private String readLine(String format, Object... args) throws IOException {
        if (System.console() != null) {
            return System.console().readLine(format, args);
        }
        System.out.print(String.format(format, args));
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                System.in));
        return reader.readLine();
    }

    private char[] readPassword(String format, Object... args)
            throws IOException {
        if (System.console() != null)
            return System.console().readPassword(format, args);
        return this.readLine(format, args).toCharArray();
    }
}
