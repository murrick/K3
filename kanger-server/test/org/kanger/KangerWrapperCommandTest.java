/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */

package org.kanger;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KangerWrapperCommandTest {

    @Test
    void wrapperInheritsParentClasspathAndPreservesJvmOptions() {
        String classPath =
                "server/kanger-server-thin.jar:server/lib/*:server/modules/*";

        assertArrayEquals(
                new String[]{
                        "java",
                        "-Xmx64m",
                        "-Dexample=value",
                        "-cp",
                        classPath,
                        "org.kanger.Kanger"
                },
                Kanger.wrapperCommand(
                        classPath,
                        Arrays.asList("-Xmx64m", "-Dexample=value")));
    }

    @Test
    void wrapperUsesSameContractForSingleJarParentClasspath() {
        String classPath = "kanger-server.jar";

        assertArrayEquals(
                new String[]{
                        "java",
                        "-cp",
                        classPath,
                        "org.kanger.Kanger"
                },
                Kanger.wrapperCommand(classPath, Collections.<String>emptyList()));
    }

    @Test
    void wrapperRejectsEmptyParentClasspath() {
        assertThrows(
                IllegalStateException.class,
                () -> Kanger.wrapperCommand("", Collections.<String>emptyList()));
    }
}
