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

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Standalone Java Console credential adapter.
 *
 * <p>The network Server owns the full account/session lifecycle, but the
 * standalone Console must authenticate against the same persistent credential
 * file. The store has two accepted on-disk forms:</p>
 *
 * <ul>
 *   <li>legacy {@code javaHash(login,password)=userId};</li>
 *   <li>versioned {@code v2} salted PBKDF2 records used by Server 0.18.</li>
 * </ul>
 *
 * <p>This adapter deliberately has no session/token authority. It resolves an
 * authenticated persistent user for a local Console process and keeps legacy
 * records readable while writing newly created Console users in the current
 * versioned format.</p>
 */
public class UserFactory {

    private static final String VERSION = "v2";
    private static final int ITERATIONS = 210000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private static String rootDir = "KANGER";
    private static final SecureRandom RANDOM = new SecureRandom();

    static {
        if (System.getenv().containsKey("KANGER_HOME")) {
            rootDir = System.getenv().get("KANGER_HOME");
        }
    }

    public static IUser getUser(String login, String password) throws Exception {
        validate(login, password);
        Path credentialFile = credentialFile();
        long userId = authenticate(credentialFile, login, password);
        if (userId < 0L) {
            throw new AuthenticationErrorException(login);
        }
        return loadUser(userId);
    }

    public static IUser createUser(String login, String password) throws Exception {
        validate(login, password);
        Path credentialFile = credentialFile();
        Path parent = credentialFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(credentialFile)) {
            Files.createFile(credentialFile);
        }

        List<String> lines = Files.readAllLines(credentialFile, StandardCharsets.UTF_8);
        String legacy = legacyToken(login, password);
        long maxId = 0L;
        for (String original : lines) {
            String line = original.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            if (line.startsWith(VERSION + "\t")) {
                String[] values = line.split("\\t", -1);
                if (values.length != 6) {
                    throw new IOException("Invalid versioned credential record in " + credentialFile);
                }
                String existingLogin = new String(decode(values[1]), StandardCharsets.UTF_8);
                long id = Long.parseLong(values[2]);
                maxId = Math.max(maxId, id);
                if (existingLogin.equals(login)) {
                    throw new AuthenticationErrorException("User already exists");
                }
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1) {
                throw new IOException("Unsupported credential record in " + credentialFile);
            }
            String token = line.substring(0, separator).trim();
            long id = Long.parseLong(line.substring(separator + 1).trim());
            maxId = Math.max(maxId, id);
            if (legacy.equalsIgnoreCase(token)) {
                throw new AuthenticationErrorException("User already exists");
            }
        }

        long id = allocateUserId(credentialFile, maxId + 1L);
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password, salt, ITERATIONS, HASH_BYTES);
        String record = VERSION + "\t"
                + encode(login.getBytes(StandardCharsets.UTF_8)) + "\t"
                + id + "\t"
                + ITERATIONS + "\t"
                + encode(salt) + "\t"
                + encode(hash);
        Files.write(credentialFile,
                java.util.Collections.singletonList(record),
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        return loadUser(id);
    }

    private static long authenticate(Path credentialFile,
                                     String login,
                                     String password) throws Exception {
        if (!Files.exists(credentialFile)) {
            return -1L;
        }
        String legacy = legacyToken(login, password);
        for (String original : Files.readAllLines(credentialFile, StandardCharsets.UTF_8)) {
            String line = original.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            if (line.startsWith(VERSION + "\t")) {
                String[] values = line.split("\\t", -1);
                if (values.length != 6) {
                    throw new IOException("Invalid versioned credential record in " + credentialFile);
                }
                String existingLogin = new String(decode(values[1]), StandardCharsets.UTF_8);
                if (!existingLogin.equals(login)) {
                    continue;
                }
                int iterations = Integer.parseInt(values[3]);
                byte[] salt = decode(values[4]);
                byte[] expected = decode(values[5]);
                byte[] candidate = derive(password, salt, iterations, expected.length);
                if (!MessageDigest.isEqual(expected, candidate)) {
                    return -1L;
                }
                return Long.parseLong(values[2]);
            }

            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1) {
                throw new IOException("Unsupported credential record in " + credentialFile);
            }
            if (legacy.equalsIgnoreCase(line.substring(0, separator).trim())) {
                return Long.parseLong(line.substring(separator + 1).trim());
            }
        }
        return -1L;
    }

    private static IUser loadUser(long userId) throws Exception {
        IUser user = new User();
        user.setProperty("user.home", getHome());
        user.setId(userId);

        String userDir = getDir(rootDir + Enums.FILE_SEPARATOR + user.getId()
                + Enums.FILE_SEPARATOR);
        user.setProperty("user.dir", userDir);
        Files.createDirectories(Paths.get(userDir));
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
        return user;
    }

    private static long allocateUserId(Path credentialFile, long minimum)
            throws IOException {
        Path sequence = credentialFile.toAbsolutePath().normalize()
                .resolveSibling("users.sequence");
        long next = minimum;
        if (Files.exists(sequence)) {
            String value = new String(Files.readAllBytes(sequence),
                    StandardCharsets.US_ASCII).trim();
            if (!value.isEmpty()) {
                next = Math.max(next, Long.parseLong(value));
            }
        }
        if (next <= 0L || next == Long.MAX_VALUE) {
            throw new IOException("User id sequence exhausted");
        }
        Files.write(sequence,
                java.util.Collections.singletonList(Long.toString(next + 1L)),
                StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        return next;
    }

    private static byte[] derive(String password,
                                 byte[] salt,
                                 int iterations,
                                 int hashBytes) throws Exception {
        char[] chars = password.toCharArray();
        PBEKeySpec specification = new PBEKeySpec(
                chars, salt, iterations, hashBytes * 8);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(
                    "PBKDF2WithHmacSHA256");
            return factory.generateSecret(specification).getEncoded();
        } finally {
            specification.clearPassword();
            java.util.Arrays.fill(chars, '\0');
        }
    }

    private static Path credentialFile() {
        String direct = getDir("users.conf");
        if (new File(direct).exists()) {
            return Paths.get(direct);
        }
        return Paths.get(getDir(rootDir) + Enums.FILE_SEPARATOR + "users.conf");
    }

    private static String legacyToken(String login, String password) {
        return String.format("%04x%04x", login.hashCode(), password.hashCode());
    }

    private static String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static void validate(String login, String password) {
        if (login == null || login.isEmpty()) {
            throw new IllegalArgumentException("login must not be empty");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
    }

    private static String getHome() {
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

    private static String getDir(String subDir) {
        String home = getHome();
        if (!home.isEmpty()) {
            home += Enums.FILE_SEPARATOR;
        }
        return home + subDir;
    }
}
