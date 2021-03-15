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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class Settings {

    private static Properties settings = new Properties();
    private static String configName;

    static {
        configName = UserFactory.getDir("kanger.conf");
        if (!new File(configName).exists()) {
            configName = UserFactory.getDir(UserFactory.rootDir) + Enums.FILE_SEPARATOR + "kanger.conf";
        }
        loadProperties();
    }

    public static String getProperty(String key, String val) throws Exception {
        if (settings.containsKey(key)) {
            return settings.getProperty(key);
        } else {
            setProperty(key, val);
        }
        return val;
    }

    public static void setProperty(String key, String val) throws Exception {
        if (val != null) {
            settings.setProperty(key, val);
        } else {
            settings.remove(key);
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(configName))) {
            settings.store(bw, new Date().toString());
        }
    }

    public static void loadProperties() {
        if (new File(configName).exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(configName))) {
                settings.load(br);
            } catch (FileNotFoundException e) {
                e.printStackTrace(System.err);
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
        }
    }

    public static List<String> getByPrefix(String prefix) {
        List<String> list = new ArrayList<>();
        for (String key : settings.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                list.add(settings.getProperty(key));
            }
        }
        return list;
    }

    public static boolean isActive() {
        String active = UserFactory.getDir(UserFactory.rootDir) + Enums.FILE_SEPARATOR + "kanger.active";
        return new File(active).exists();
    }

    public static void setActive(boolean on) throws IOException {
        Files.createDirectories(Paths.get(UserFactory.getDir(UserFactory.rootDir)));
        String active = UserFactory.getDir(UserFactory.rootDir) + Enums.FILE_SEPARATOR + "kanger.active";
        File f = new File(active);
        if (on) {
            FileWriter fw = new FileWriter(f);
            fw.write(new Date().toString());
            fw.close();
        } else {
            f.delete();
        }
    }

}
