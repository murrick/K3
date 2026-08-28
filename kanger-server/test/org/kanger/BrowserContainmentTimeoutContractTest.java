/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserContainmentTimeoutContractTest {

    private static final Pattern BROWSER_TIMEOUT = Pattern.compile(
            "REQUEST_TIMEOUT_MS\\s*=\\s*(\\d+)");
    private static final Pattern PROXY_READ_TIMEOUT = Pattern.compile(
            "proxy_read_timeout\\s+(\\d+)s;");

    @Test
    void browserContainmentOutlivesSupportedProxyResponseBudget()
            throws Exception {
        String containment = read(Paths.get("..", "html", "containment.js"));
        String distributionNginx = read(Paths.get("..", "distribution", "payload",
                "nginx", "kanger.conf.template"));
        String internalNginx = read(Paths.get("deploy", "nginx",
                "kanger-server.conf.template"));

        long browserTimeoutMs = captureBrowserTimeout(containment);
        long proxyTimeoutSeconds = Math.max(
                maxProxyReadTimeoutSeconds(distributionNginx),
                maxProxyReadTimeoutSeconds(internalNginx));

        assertTrue(browserTimeoutMs > proxyTimeoutSeconds * 1000L,
                "Browser containment must not abort a semantic request before "
                        + "the supported nginx response budget expires");
        assertTrue(containment.contains(
                        "requestTimeoutMs: REQUEST_TIMEOUT_MS + 2000"),
                "Contained child deadline must remain longer than the parent broker deadline");
    }

    private static long captureBrowserTimeout(String containment) {
        Matcher matcher = BROWSER_TIMEOUT.matcher(containment);
        assertTrue(matcher.find(), "Missing Browser containment timeout");
        return Long.parseLong(matcher.group(1));
    }

    private static long maxProxyReadTimeoutSeconds(String nginx) {
        Matcher matcher = PROXY_READ_TIMEOUT.matcher(nginx);
        long maximum = 0;
        while (matcher.find()) {
            maximum = Math.max(maximum, Long.parseLong(matcher.group(1)));
        }
        assertTrue(maximum > 0, "Missing nginx proxy_read_timeout");
        return maximum;
    }

    private static String read(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "Missing contract file: " + path);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
