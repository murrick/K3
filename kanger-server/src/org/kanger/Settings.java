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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.List;

/**
 * Static facade for server configuration and runtime activation state.
 * Configuration reads never persist defaults; only explicit setProperty calls
 * mutate the backing file.
 */
public class Settings {

    private static final String configName;
    private static final ServerSettingsStore store;

    static {
        String direct = UserFactory.getDir("kanger.conf");
        if (new File(direct).exists()) {
            configName = direct;
        } else {
            configName = UserFactory.getDir(UserFactory.rootDir)
                    + Enums.FILE_SEPARATOR + "kanger.conf";
        }
        store = new ServerSettingsStore(Paths.get(configName));
        loadProperties();
    }

    private Settings() {
    }

    public static String getProperty(String key, String val) {
        return store.get(key, val);
    }

    public static void setProperty(String key, String val) throws Exception {
        store.set(key, val);
    }

    public static void loadProperties() {
        try {
            store.reload();
        } catch (IOException ex) {
            System.err.println(new Date());
            ex.printStackTrace(System.err);
        }
    }

    public static List<String> getByPrefix(String prefix) {
        return store.getByPrefix(prefix);
    }

    public static boolean isActive() {
        return Files.exists(activeMarker());
    }

    public static void setActive(boolean on) throws IOException {
        Path marker = activeMarker();
        if (on) {
            Files.createDirectories(marker.getParent());
            Files.write(marker,
                    new Date().toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } else {
            Files.deleteIfExists(marker);
        }
    }

    static String getConfigName() {
        return configName;
    }

    private static Path activeMarker() {
        return Paths.get(UserFactory.getDir(UserFactory.rootDir), "kanger.active")
                .toAbsolutePath()
                .normalize();
    }
}
