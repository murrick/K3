package org.kanger;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerMaintenanceUiContractTest {

    @Test
    void browserPollsMaintenanceAndShowsInformationalBannerOnly()
            throws Exception {
        String index = read(Paths.get("..", "html", "index.html"));
        String maintenance = read(Paths.get("..", "html", "maintenance.js"));

        assertTrue(index.contains("maintenance.js"));
        assertTrue(maintenance.contains("POLL_INTERVAL_MS = 10000"));
        assertTrue(maintenance.contains("API_HOST + '/maintenance'"));
        assertTrue(maintenance.contains("method: 'GET'"));
        assertTrue(maintenance.contains("Server maintenance is scheduled for"));
        assertTrue(maintenance.contains("deadline_epoch_millis"));

        String lower = maintenance.toLowerCase();
        assertFalse(lower.contains("save your"));
        assertFalse(lower.contains("commit your"));
        assertFalse(lower.contains("disconnect"));
        assertFalse(maintenance.contains("innerHTML"));
        assertFalse(maintenance.contains("eval("));
        assertFalse(maintenance.contains("new Function"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
