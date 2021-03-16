/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger;

import org.kanger.enums.Enums;
import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IUser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class UserFactory {
    public static final int MAX_HISTORY_SIZE = 512;
    public static final long INACTIVITY_TIME = 1000L * 60 * 60 * 3;    // 3 hours

    public static String rootDir = "KANGER";

    private static Map<String, IUser> users = new ConcurrentHashMap<>();
    private static Map<Long, List<String>> history = new ConcurrentHashMap<>();
    private static Map<Long, Long> activity = new ConcurrentHashMap<>();
    private static TimerThread timerThread = new TimerThread(activity);

    static {
        if (System.getenv().containsKey("KANGER_HOME")) {
            rootDir = System.getenv().get("KANGER_HOME");
        }

        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(timerThread, 0, INACTIVITY_TIME / 10);
    }

    public static IUser getUser(String token) throws Exception {
        if (users.containsKey(token)) {
            IUser user = users.get(token);
            activity.put(user.getId(), System.currentTimeMillis());
            return user;
        } else {
            throw new AuthenticationErrorException("token " + token);
        }
    }

    public static String addUser(IUser user) {
        for (Map.Entry<String, IUser> e : users.entrySet()) {
            if (e.getValue().getId() == user.getId()) {
                users.remove(e.getKey());
                break;
            }
        }
        String token = token();
        users.put(token, user);
        activity.put(user.getId(), System.currentTimeMillis());
        return token;
    }

    public static void dropUser(IUser user) {
        dropUser(user.getId());
    }

    public static String getUserToken(IUser user) throws Exception {
        String confName = getDir("users.conf");
        if (!new File(confName).exists()) {
            confName = getDir(rootDir) + Enums.FILE_SEPARATOR + "users.conf";
        }
        if (new File(confName).exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(confName))) {
                String sCurrentLine;
                while ((sCurrentLine = br.readLine()) != null) {
                    if (sCurrentLine.split("\\=").length == 2 && (user.getId() + "").equalsIgnoreCase(sCurrentLine.split("\\=")[1])) {
                        return sCurrentLine.split("\\=")[0];
                    }
                }
            }
        }
        throw new AuthenticationErrorException();
    }

    public static void updateUserToken(IUser user, String login, String password) throws Exception {
        String token = token(login, password);
        String confName = getDir("users.conf");
        if (!new File(confName).exists()) {
            confName = getDir(rootDir) + Enums.FILE_SEPARATOR + "users.conf";
        }

        List<String> list = new ArrayList<>();
        if (new File(confName).exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(confName))) {
                String sCurrentLine;
                while ((sCurrentLine = br.readLine()) != null) {
                    if (sCurrentLine.split("\\=").length == 2 && token.equalsIgnoreCase(sCurrentLine.split("\\=")[0])) {
                        if (user.getId() != Long.parseLong(sCurrentLine.split("\\=")[1])) {
                            throw new Exception("Login and password used by another user");
                        }
                    } else {
                        list.add(sCurrentLine);
                    }
                }
            }
            list.add(token + "=" + user.getId());
        } else {
            new File(confName).createNewFile();
        }

        String tmp = confName + "temp";
        new File(tmp).createNewFile();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(tmp, true))) {
            for (String s : list) {
                bw.write(s);
                bw.newLine();
            }
        }

        new File(confName).delete();
        new File(tmp).renameTo(new File(confName));

    }

    public static IUser getUserByToken(String token) throws Exception {
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
                    if (sCurrentLine.split("\\=").length == 2 && token.equalsIgnoreCase(sCurrentLine.split("\\=")[0])) {
                        user.setId(Long.parseLong(sCurrentLine.split("\\=")[1]));
                        break;
                    }
                }
            }
        }

        if (user.getId() == -1L) {
            throw new AuthenticationErrorException();
        }

        boolean found = false;
        for (Map.Entry<String, IUser> e : users.entrySet()) {
            if (e.getValue().getId() == user.getId()) {
                user = e.getValue();
                found = true;
            }
        }

        if (!found) {
            String userDir = getDir(rootDir + Enums.FILE_SEPARATOR + user.getId() + Enums.FILE_SEPARATOR);
            Files.createDirectories(Paths.get(userDir));
            user.setProperty("user.dir", userDir);

            user.loadProperties();

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
        }

        return user;
    }

    public static IUser getUser(String login, String password) throws Exception {

        String token = token(login, password);
        return getUserByToken(token);
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
                    if (sCurrentLine.split("\\=").length == 2) {
                        if (token.equalsIgnoreCase(sCurrentLine.split("\\=")[0])) {
                            throw new AuthenticationErrorException("User already exists");
                        }
                        long idx = Long.parseLong(sCurrentLine.split("\\=")[1]);
                        if (idx > id) {
                            id = idx;
                        }
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

    public static String token(String login, String password) {
        return String.format("%04x%04x", login.hashCode(), password.hashCode());
    }

    private static String token() {
        return String.format("%04x%04x", UUID.randomUUID().hashCode(), UUID.randomUUID().hashCode());
    }

    public static String getHome() {
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

    public static String getDir(String subDir) {
        String home = getHome();
        if (!home.isEmpty()) {
            home += Enums.FILE_SEPARATOR;
        }
        return home + subDir;
    }

    public static void addHistory(IUser user, String record) throws Exception {
        if (!history.containsKey(user.getId())) {
            history.put(user.getId(), new ArrayList<>());
        }
        history.get(user.getId()).add(record);
        while (history.get(user.getId()).size() > Integer.parseInt(user.getProperty("user.history.size", MAX_HISTORY_SIZE + ""))) {
            history.get(user.getId()).remove(0);
        }
    }

    public static List<String> getHistory(IUser user) {
        if (history.containsKey(user.getId())) {
            return history.get(user.getId());
        } else {
            return new ArrayList<>();
        }
    }

    public static void dropUser(Long id) {
        for (Map.Entry<String, IUser> e : users.entrySet()) {
            if (e.getValue().getId() == id) {
                users.remove(e.getKey());
                break;
            }
        }
        history.remove(id);
        activity.remove(id);
    }

    public static void shutdown() {
        while (!users.isEmpty()) {
            dropUser(users.values().iterator().next().getId());
        }
    }
}
