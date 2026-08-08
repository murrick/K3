/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package org.kanger;

import org.kanger.exception.AuthenticationErrorException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.util.Date;

/**
 * Java Console entry point.
 */
public class Kanger {

    public static void main(String[] args) throws Exception {
        String newlogin = null;
        String login = null;
        String password = null;
        boolean singleUser = false;

        String[] envs = new String[]{};
        if (System.getenv().containsKey("KANGER_OPTIONS")) {
            envs = System.getenv().get("KANGER_OPTIONS").split(" ");
        }

        String[] params = concatenate(args, envs);
        for (int i = 0; i < params.length; ++i) {
            if ((params[i].equals("--adduser") || params[i].equals("-A"))
                    && params.length > i + 1) {
                newlogin = params[++i];
            } else if ((params[i].equals("--user") || params[i].equals("-U"))
                    && params.length > i + 1) {
                login = params[++i];
            } else if ((params[i].equals("--password") || params[i].equals("-P"))
                    && params.length > i + 1) {
                password = params[++i];
            } else if (params[i].equals("--singleuser") || params[i].equals("-S")) {
                singleUser = true;
            } else if (params[i].equals("--help") || params[i].equals("-H")) {
                String jarName = new File(Kanger.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).getName();
                Console.showCopyrigt();
                System.out.println();
                System.out.printf("Usage: java -jar %s [options]%n"
                                + "Options:%n"
                                + "\t--adduser or -A\t-Create new user. Password required.%n"
                                + "\t--user or -U\t-Login with selected user login.%n"
                                + "\t--password or -P\t-Select password.%n"
                                + "\t--singleuser or -S\t-Local single user mode.%n"
                                + "\t--help or -H\t-Show this message.%n",
                        jarName);
                System.exit(0);
            }
        }

        try {
            UserFactory.createUser("singleuser", "singleuser");
        } catch (Exception ignored) {
        }

        if (newlogin != null) {
            try {
                if (password == null) {
                    throw new AuthenticationErrorException("Password must be defined");
                }
                UserFactory.createUser(newlogin, password);
                System.out.println("New user created: " + newlogin);
                System.exit(0);
            } catch (Exception ex) {
                System.err.println(new Date());
                ex.printStackTrace(System.err);
            }
        }

        if (singleUser) {
            login = "singleuser";
            password = "singleuser";
        }

        Console.showCopyrigt();
        System.out.println();

        if (login == null) {
            Kanger kanger = new Kanger();
            login = kanger.readLine("login: ");
            password = new String(kanger.readPassword("password: "));
            System.out.println();
        }

        IUser user = UserFactory.getUser(login, password);
        try {
            new UDF().init(user);
            System.out.println("UDF module loaded");
        } catch (NoClassDefFoundError ignored) {
        }
        try {
            new DB().init(user);
            System.out.println("DB module loaded: " + new DB().getDescription());
        } catch (NoClassDefFoundError ignored) {
        }

        System.out.println("Current user: " + login);
        System.out.println("User directory: " + user.getUserDir());
        System.out.println("Path to source files: " + user.getSourceDir());
        System.out.println("Path to databases: " + user.getDatabaseDir());

        IMind mind = new Mind(user);
        ShutdownHook shutdownHook = new ShutdownHook(mind);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        CanonicalConsole.session(mind, shutdownHook);
    }

    private String readLine(String format, Object... args) throws IOException {
        if (System.console() != null) {
            return System.console().readLine(format, args);
        }
        System.out.print(String.format(format, args));
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
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

    private char[] readPassword(String format, Object... args) throws IOException {
        if (System.console() != null) {
            return System.console().readPassword(format, args);
        }
        return readLine(format, args).toCharArray();
    }
}
