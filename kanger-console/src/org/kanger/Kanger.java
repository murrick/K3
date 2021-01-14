package org.kanger;

import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IData;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;


/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 */
public class Kanger {


    public static void main(String[] args) throws Exception {

        String newlogin = null;
        String login = null;
        String password = null;
        String rootDir = "KANGER";
        boolean singleUser = false;

        if (System.getenv().containsKey("KANGER_HOME")) {
            rootDir = System.getenv().get("KANGER_HOME");
        }

        String envs[] = new String[]{};
        if (System.getenv().containsKey("KANGER_OPTIONS")) {
            envs = System.getenv().get("KANGER_OPTIONS").split(" ");
        }

        String[] params = concatenate(args, envs);

        for (int i = 0; i < params.length; ++i) {
            if ((params[i].equals("--adduser") || params[i].equals("-A")) && params.length > i + 1) {
                newlogin = params[++i];
            } else if ((params[i].equals("--user") || params[i].equals("-U")) && params.length > i + 1) {
                login = params[++i];
            } else if ((params[i].equals("--password") || params[i].equals("-P")) && params.length > i + 1) {
                password = params[++i];
            } else if ((params[i].equals("--rootdir") || params[i].equals("-R")) && params.length > i + 1) {
                rootDir = params[++i];
            } else if (params[i].equals("--singleuser") || params[i].equals("-S")) {
                singleUser = true;
            } else if (params[i].equals("--help") || params[i].equals("-H")) {
                String jarName = new File(Kanger.class.getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI()).getName();
                Screen.showCopyrigt();
                System.out.printf("Usage: java -jar %s [options]\n" +
                                "Options:\n" +
                                "\t--adduser or -A\t-Create new user. Password required.\n" +
                                "\t--user or -U\t-Login with selected user login.\n" +
                                "\t--password or -P\t-Select password.\n" +
                                "\t--rootdir or -R\t-Select home KANGER directory.\n" +
                                "\t--singleuser or -S\t-Local single user mode.\n" +
                                "\t--help or -H\t-Show this message.\n",
                        jarName
                );
                System.exit(0);
            }
        }

        if (singleUser) {
            try {
                User.createUser("singleuser", "singleuser", rootDir);
            } catch (Exception ex) {
                //
            }
        }

        if (newlogin != null) {
            try {
                if (password == null) {
                    throw new AuthenticationErrorException("Password must be defined");
                }
                User.createUser(newlogin, password, rootDir);
                System.out.println("New user created: " + newlogin);
                System.exit(0);
            } catch (Exception ex) {
                ex.printStackTrace(System.err);
            }
        }

        if (singleUser) {
            login = "singleuser";
            password = "singleuser";
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

        if (login == null) {
            Kanger k = new Kanger();
            Screen.showCopyrigt();
            login = k.readLine("login: ");
            password = new String(k.readPassword("password: "));
        }

        IUser user = new User(login, password, rootDir);

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

    private static <T> T[] concatenate(T[] a, T[] b) {
        int aLen = a.length;
        int bLen = b.length;

        @SuppressWarnings("unchecked")
        T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen + bLen);
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);

        return c;
    }

    private char[] readPassword(String format, Object... args)
            throws IOException {
        if (System.console() != null)
            return System.console().readPassword(format, args);
        return this.readLine(format, args).toCharArray();
    }
}
