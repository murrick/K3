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
import org.kanger.security.ConfirmationTokenStore;
import org.kanger.security.CredentialStore;
import org.kanger.security.SecureTokens;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class UserFactory {
    public static final int MAX_HISTORY_SIZE = 512;
    public static final long INACTIVITY_TIME = 1000L * 60 * 60 * 3;    // 3 hours
    public static final long CONFIRMATION_TIME = 1000L * 60 * 60 * 24; // 24 hours

    public static String rootDir = "KANGER";

    private static final Map<String, IUser> users = new ConcurrentHashMap<String, IUser>();
    private static final Map<Long, List<String>> history = new ConcurrentHashMap<Long, List<String>>();
    private static final Map<Long, Long> activity = new ConcurrentHashMap<Long, Long>();
    private static final TimerThread timerThread = new TimerThread(activity);
    private static final ThreadLocal<String> pendingConfirmationToken = new ThreadLocal<String>();

    private static CredentialStore credentialStore;
    private static ConfirmationTokenStore confirmationTokenStore;

    static {
        if (System.getenv().containsKey("KANGER_HOME")) {
            rootDir = System.getenv().get("KANGER_HOME");
        }

        String credentialFile = resolveCredentialFile();
        credentialStore = new CredentialStore(Paths.get(credentialFile));
        confirmationTokenStore = new ConfirmationTokenStore(Paths.get(
                new File(credentialFile).getParent(), "confirmations.conf"));

        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(timerThread, 0, INACTIVITY_TIME / 10);
    }

    public static IUser getUser(String token) throws Exception {
        if (users.containsKey(token)) {
            IUser user = users.get(token);
            activity.put(user.getId(), System.currentTimeMillis());
            return user;
        }
        throw new AuthenticationErrorException("token " + token);
    }

    public static String addUser(IUser user) {
        for (Map.Entry<String, IUser> entry : users.entrySet()) {
            if (entry.getValue().getId() == user.getId()) {
                users.remove(entry.getKey());
                break;
            }
        }
        String token = SecureTokens.random256();
        users.put(token, user);
        activity.put(user.getId(), System.currentTimeMillis());
        return token;
    }

    public static void dropUser(IUser user) {
        dropUser(user.getId());
    }

    /**
     * Issues a new one-time confirmation token. Any previous token for this
     * user is invalidated.
     */
    public static String getUserToken(IUser user) throws Exception {
        return confirmationTokenStore.issue(user.getId(), CONFIRMATION_TIME);
    }

    public static void updateUserToken(IUser user, String login, String password) throws Exception {
        credentialStore.update(user.getId(), login, password);
    }

    /**
     * Consumes a one-time confirmation token and resolves its user.
     */
    public static IUser getUserByToken(String token) throws Exception {
        long userId = confirmationTokenStore.consume(token);
        return getUserById(userId);
    }

    private static IUser getUserById(long userId) throws Exception {
        IUser user = null;
        for (Map.Entry<String, IUser> entry : users.entrySet()) {
            if (entry.getValue().getId() == userId) {
                user = entry.getValue();
                break;
            }
        }

        if (user == null) {
            user = new User();
            user.setProperty("user.home", getHome());
            user.setId(userId);

            String userDir = getDir(rootDir + Enums.FILE_SEPARATOR + user.getId()
                    + Enums.FILE_SEPARATOR);
            Files.createDirectories(Paths.get(userDir));
            user.setProperty("user.dir", userDir);
            user.loadProperties();

            if (!user.containsProperty("sources.dir")) {
                String sourcesDir = userDir + "SRC" + Enums.FILE_SEPARATOR;
                user.setProperty("sources.dir", sourcesDir);
                Files.createDirectories(Paths.get(sourcesDir));
            }

            if (!user.containsProperty("database.dir")) {
                String databaseDir = userDir + "DB" + Enums.FILE_SEPARATOR;
                user.setProperty("database.dir", databaseDir);
                Files.createDirectories(Paths.get(databaseDir));
            }
        }
        return user;
    }

    public static IUser getUser(String login, String password) throws Exception {
        long userId = credentialStore.authenticate(login, password);
        return getUserById(userId);
    }

    public static IUser createUser(String login, String password) throws Exception {
        String pendingToken = pendingConfirmationToken.get();
        try {
            long userId = credentialStore.create(login, password);
            if (pendingToken != null) {
                confirmationTokenStore.bind(pendingToken, userId, CONFIRMATION_TIME);
            }
            return getUserById(userId);
        } finally {
            pendingConfirmationToken.remove();
        }
    }

    /**
     * Transitional registration hook preserving the historical QueryProcessor
     * call order. The returned value is now an opaque one-time confirmation
     * token and is never derived from login or password.
     */
    public static String token(String login, String password) {
        String token = SecureTokens.random256();
        pendingConfirmationToken.set(token);
        return token;
    }

    public static String getHome() {
        String home = System.getProperty("user.home");
        if (home.isEmpty()) {
            home = new File("").getAbsolutePath();
            if (home.isEmpty() || home.equals(Enums.FILE_SEPARATOR)) {
                String tmp = "/storage/emulated/0";
                if (Files.exists(Paths.get(tmp))) {
                    return tmp;
                }
                return home;
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
            history.put(user.getId(), new ArrayList<String>());
        }
        history.get(user.getId()).add(record);
        while (history.get(user.getId()).size() > Integer.parseInt(
                user.getProperty("user.history.size", MAX_HISTORY_SIZE + ""))) {
            history.get(user.getId()).remove(0);
        }
    }

    public static List<String> getHistory(IUser user) {
        if (history.containsKey(user.getId())) {
            return history.get(user.getId());
        }
        return new ArrayList<String>();
    }

    public static void dropUser(Long id) {
        for (Map.Entry<String, IUser> entry : users.entrySet()) {
            if (entry.getValue().getId() == id.longValue()) {
                users.remove(entry.getKey());
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

    private static String resolveCredentialFile() {
        String direct = getDir("users.conf");
        if (new File(direct).exists()) {
            return direct;
        }
        return getDir(rootDir) + Enums.FILE_SEPARATOR + "users.conf";
    }
}
