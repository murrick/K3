package org.kanger;

import org.kanger.enums.Enums;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IUser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public class UserFactory {

    private static String rootDir = "KANGER";

    static {
        if (System.getenv().containsKey("KANGER_HOME")) {
            rootDir = System.getenv().get("KANGER_HOME");
        }
    }

    public static IUser getUser(String login, String password) throws Exception {

        String token = token(login, password);

        IUser user = new User();
        user.setProperty("user.home", getHome());

        String confName = getDir("users.conf");
        if (!new File(confName).exists()) {
            confName = getDir(rootDir) + Enums.FILE_SEPARATOR + "users.conf";
        }
        if (new File(confName).exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(confName))) {
                String sCurrentLine;
                while ((sCurrentLine = br.readLine()) != null) {
                    if (sCurrentLine.split("\\=").length == 2 && token.toLowerCase().equals(sCurrentLine.split("\\=")[0].toLowerCase())) {
                        user.setId(Long.parseLong(sCurrentLine.split("\\=")[1]));
                        break;
                    }
                }
            }
        }

        if (user.getId() == -1L) {
            throw new AuthenticationErrorException(login);
        }

        String userDir = getDir(rootDir + Enums.FILE_SEPARATOR + user.getId() + Enums.FILE_SEPARATOR);
        user.setProperty("user.dir", userDir);
        Files.createDirectories(Paths.get(userDir));

        confName = userDir + "kanger.conf";
        user.loadProperties(confName);

        if (!user.containsProperty("sources.dir")) {
            String sourcesDir = userDir + "SRC" + Enums.FILE_SEPARATOR;
            user.setProperty("sources.dir", sourcesDir);
            Files.createDirectories(Paths.get(sourcesDir));
        }

        if (!user.containsProperty("database.dir")) {
            String sourcesDir = userDir + "DB" + Enums.FILE_SEPARATOR;
            user.setProperty("database.dir", sourcesDir);
            Files.createDirectories(Paths.get(sourcesDir));
        }

        return user;
    }


    public static IUser createUser(String login, String password) throws Exception {

        String token = token(login, password);
        String confName = getDir("users.conf");
        if (!new File(confName).exists()) {
            Files.createDirectories(Paths.get(getDir(rootDir)));
            confName = getDir(rootDir) + Enums.FILE_SEPARATOR + "users.conf";
        }
        long id = 0;
        if (new File(confName).exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(confName))) {
                String sCurrentLine;
                while ((sCurrentLine = br.readLine()) != null) {
                    if (sCurrentLine.split("\\=").length == 2 && token.toLowerCase().equals(sCurrentLine.split("\\=")[0].toLowerCase())) {
                        throw new AuthenticationErrorException("User already exists");
                    }
                    long idx = Long.parseLong(sCurrentLine.split("\\=")[1]);
                    if (idx > id) {
                        id = idx;
                    }
                }
            }
        } else {
            new File(confName).createNewFile();
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(confName, true))) {
            bw.write(token + "=" + (++id));
            bw.newLine();
        }

        return getUser(login, password);
    }

    private static String token(String login, String password) {
        return String.format("%04x%04x", login.hashCode(), password.hashCode());
    }

    private static String getHome() {
        String home = System.getProperty("user.home");
        if (home.isEmpty()) {
            home = new File("").getAbsolutePath();
            if (home.isEmpty() || home.equals(Enums.FILE_SEPARATOR)) {
                String tmp = "/storage/emulated/0";
                if (Files.exists(Paths.get(tmp))) {
                    return tmp;
                } else {
                    return home;
                }
            }
        }
        return home;
    }

    private static String getDir(String subDir) {
        String home = getHome();
        if (!home.isEmpty()) {
            home += Enums.FILE_SEPARATOR;
        }
        return home + subDir;
    }


}
