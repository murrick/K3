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

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimerTask;

public class Watchdog extends TimerTask {

    private static final long ROTATOR_PERIOD = 60000L;

    private static long lastRotatorEvent = 0;

    public static void rotator(String name) throws Exception {
        String logName = Settings.getProperty("server.logs.path", UserFactory.getDir(UserFactory.rootDir)) + Enums.FILE_SEPARATOR + name;
        long size = Long.parseLong(Settings.getProperty("server.logs.max.size", 64000000 + ""));
        long index = Long.parseLong(Settings.getProperty("server.logs.max.count", 3 + ""));

        File f = new File(logName);
        if (f.exists()) {
            if (f.length() >= size) {
                f = new File(logName + "." + index);
                if (f.exists()) {
                    f.delete();
                }
                while (--index >= 0) {
                    f = new File(logName + "." + index);
                    if (f.exists()) {
                        f.renameTo(new File(logName + "." + (index + 1)));
                    }
                }
                new File(logName).createNewFile();
            }
        }
    }

    public static void log(String log) {
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " [DEBUG] " + log);
    }

    public static void err(String log) {
        System.err.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " [ERROR] " + log);
    }

    public static void log(IUser user, String log) throws Exception {
        System.out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " [DEBUG] " +
                user.getProperty("reg.login", "unknown") + ": " +
                log);
    }

    public static void err(IUser user, String log) throws Exception {
        System.err.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " [ERROR] " +
                user.getProperty("reg.login", "unknown") + ": " +
                log);
    }

    @Override
    public void run() {
        if (!Settings.isActive()) {
            Kanger.getHttpServer().stop();
            log("HTTP Server shutdown signal sent");
        }
        if (System.currentTimeMillis() - lastRotatorEvent > ROTATOR_PERIOD) {
            try {
                rotator("kanger-main.log");
                rotator("kanger-errors.log");
            } catch (Exception e) {
                System.err.println(new Date());
                e.printStackTrace(System.err);
            }
            lastRotatorEvent = System.currentTimeMillis();
        }
    }

}
