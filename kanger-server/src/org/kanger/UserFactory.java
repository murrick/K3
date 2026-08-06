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
import org.kanger.interfaces.IUser;
import org.kanger.security.ConfirmationTokenStore;
import org.kanger.security.CredentialStore;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side user, credential and session lifecycle authority.
 */
public class UserFactory {
    public static final int MAX_HISTORY_SIZE = 512;
    public static final long INACTIVITY_TIME = 1000L * 60 * 60 * 3;    // 3 hours
    public static final long CONFIRMATION_TIME = 1000L * 60 * 60 * 24; // 24 hours

    public static String rootDir = "KANGER";

    private static final ConcurrentHashMap<Long, List<String>> history =
            new ConcurrentHashMap<Long, List<String>>();
    private static final ThreadLocal<String> pendingConfirmationToken =
            new ThreadLocal<String>();

    private static CredentialStore credentialStore;
    private static ConfirmationTokenStore confirmationTokenStore;
    private static SessionRegistry sessions;

    static {
        if (System.getenv().containsKey("KANGER_HOME")) {
            rootDir = System.getenv().get("KANGER_HOME");
        }

        String credentialFile = resolveCredentialFile();
        credentialStore = new CredentialStore(Paths.get(credentialFile));
        confirmationTokenStore = new ConfirmationTokenStore(Paths.get(
                new File(credentialFile).getParent(), "confirmations.conf"));
        sessions = new SessionRegistry(
                INACTIVITY_TIME,
                new SessionRegistry.TimeSource() {
                    @Override
                    public long now() {
                        return System.currentTimeMillis();
                    }
                },
                new SessionRegistry.RuntimeCloser() {
                    @Override
                    public void close(IUser user) throws Exception {
                        closeRuntime(user);
                    }
                });

        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerThread(), 0, INACTIVITY_TIME / 10);
    }

    /**
     * Compatibility lookup. Mutable request workflows must be wrapped by the
     * HTTP boundary so the registry lock remains held for the complete call.
     */
    public static IUser getUser(String token) throws Exception {
        return sessions.execute(token, new SessionRegistry.UserAction<IUser>() {
            @Override
            public IUser run(IUser user) {
                return user;
            }
        });
    }

    static <T> T executeWithSessionIfPresent(String token,
                                             SessionRegistry.Work<T> work)
            throws Exception {
        return sessions.executeIfPresent(token, work);
    }

    static <T> T executeWithAuthenticatedUserIfPresent(String login,
                                                       String password,
                                                       SessionRegistry.Work<T> work)
            throws Exception {
        long userId = credentialStore.authenticate(login, password);
        return sessions.executeUserIfPresent(userId, work);
    }

    public static String addUser(IUser user) {
        return sessions.open(user);
    }

    public static void dropUser(IUser user) throws Exception {
        if (user != null) {
            sessions.closeUser(user.getId());
        }
    }

    public static void dropUser(Long id) throws Exception {
        if (id != null) {
            sessions.closeUser(id.longValue());
        }
    }

    public static void logout(String token) throws Exception {
        sessions.closeToken(token);
    }

    /**
     * Issues a new one-time confirmation token. Any previous token for this
     * user is invalidated.
     */
    public static String getUserToken(IUser user) throws Exception {
        return confirmationTokenStore.issue(user.getId(), CONFIRMATION_TIME);
    }

    public static void updateUserToken(IUser user, String login, String password)
            throws Exception {
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
        IUser user = sessions.findActiveUser(userId);
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
     * call order. The returned value is an opaque one-time confirmation token
     * and is never derived from login or password.
     */
    public static String token(String login, String password) {
        String token = org.kanger.security.SecureTokens.random256();
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
        List<String> records = history.get(user.getId());
        if (records == null) {
            List<String> created = new ArrayList<String>();
            List<String> previous = history.putIfAbsent(user.getId(), created);
            records = previous == null ? created : previous;
        }
        records.add(record);
        while (records.size() > Integer.parseInt(
                user.getProperty("user.history.size", MAX_HISTORY_SIZE + ""))) {
            records.remove(0);
        }
    }

    public static List<String> getHistory(IUser user) {
        List<String> records = history.get(user.getId());
        if (records == null) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(records);
    }

    static void expireInactiveSessions() {
        try {
            sessions.expireInactive();
        } catch (Exception ex) {
            Watchdog.err("Unable to expire inactive user session: " + ex);
        }
    }

    public static void shutdown() {
        try {
            sessions.shutdown();
        } catch (Exception ex) {
            Watchdog.err("Unable to close all user sessions: " + ex);
        }
    }

    private static void closeRuntime(IUser user) throws Exception {
        Exception failure = null;
        try {
            MindRuntimeLifecycle.close(user);
        } catch (Exception ex) {
            failure = ex;
        } finally {
            history.remove(user.getId());
        }
        if (failure != null) {
            throw failure;
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
