/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kanger.enums.Enums;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification gates for the 3.5.2.2 destructive-operation stop-loss boundary. */
public class DestructiveStopLossReactorTest {

    private static final String[] GENERATION_SUFFIXES = {
            ".index", ".store", ".integrity", ".integrity.delta"
    };

    @Test
    public void destructiveBoundaryPreservesAuthoritativeState() throws Exception {
        String identity = "stop-loss-" + UUID.randomUUID().toString();
        IUser user = UserFactory.createUser(identity, identity);
        try {
            new UDF().init(user);
            new DB().init(user);

            IMind mind = new Mind(user);
            String storageName = "stop-loss" + Enums.FILE_SEPARATOR + "compile";
            mind = mind.useStorage(storageName);
            user.setCurrentMind(mind);
            assertTrue(mind.compile("!a;"), "Initial source was rejected");

            String token = UserFactory.addUser(user);
            DestructiveStopLossReactor reactor = new DestructiveStopLossReactor(
                    new QueryProcessor());

            rootCompileReplacementPreservesRootIdentity(reactor, user, token);
            rejectedCompilePreservesGeneration(
                    reactor, user, token, storageName);
            rootEraseReplacementPreservesRootIdentityAndStorage(
                    reactor, user, token, storageName);
            transactionBlocksDestructiveCommand(reactor, user, token);
            utf8SourceRoundTripIsTruthful(reactor, user, token);
            failedStorageSwitchPreservesCurrentGeneration(
                    reactor, user, token, storageName);
            nestedStorageReindexAndDropUseCanonicalIdentity(
                    reactor, user, token);
        } finally {
            UserFactory.dropUser(user);
        }
    }

    private void rootCompileReplacementPreservesRootIdentity(
            DestructiveStopLossReactor reactor,
            IUser user,
            String token) throws Exception {
        IMind root = user.getCurrentMind();
        assertEquals(0, root.getTransactionLevel(),
                "Root compile qualification did not start at U0");

        JSONObject response = invoke(reactor, "query", new JSONObject()
                .put("token", token)
                .put("compile", URLEncoder.encode("!atomic;", "UTF-8")));

        assertEquals("OK", response.optString("result"), response.toString());
        assertSame(root, user.getCurrentMind(),
                "Accepted root Compile replaced the published root Mind");
        assertEquals(0, user.getCurrentMind().getTransactionLevel(),
                "Accepted root Compile left an explicit transaction open");
        assertTrue(user.getCurrentMind().getSourceCode().contains("atomic"),
                "Accepted root Compile did not publish the replacement source");
    }

    private void rejectedCompilePreservesGeneration(
            DestructiveStopLossReactor reactor,
            IUser user,
            String token,
            String storageName) throws Exception {
        IMind rootBefore = user.getCurrentMind();
        String sourceBefore = rootBefore.getSourceCode();
        Map<String, String> generationBefore = hashGeneration(user, storageName);

        JSONObject response = invoke(reactor, "query", new JSONObject()
                .put("token", token)
                .put("compile", URLEncoder.encode("!conflict; ?conflict;", "UTF-8")));

        assertEquals("error", response.optString("result"), response.toString());
        assertEquals("compile_rejected", response.optString("code"), response.toString());
        assertSame(rootBefore, user.getCurrentMind(),
                "Rejected Compile replaced the published root Mind");
        assertEquals(sourceBefore, user.getCurrentMind().getSourceCode(),
                "Rejected Compile changed the logical workspace");
        assertEquals(generationBefore, hashGeneration(user, storageName),
                "Rejected Compile changed the persistent generation");
    }

    private void rootEraseReplacementPreservesRootIdentityAndStorage(
            DestructiveStopLossReactor reactor,
            IUser user,
            String token,
            String storageName) throws Exception {
        IMind root = user.getCurrentMind();
        assertTrue(root.isStorageUsed(),
                "Root erase qualification requires an attached storage");
        assertEquals(storageName, root.getStorageName(),
                "Root erase qualification started with the wrong storage");
        assertFalse(SourceContextMaterializer.materializeCurrentLevel(root).isEmpty(),
                "Root erase qualification requires non-empty source");

        JSONObject response = invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put("erase", ""));

        assertEquals("OK", response.optString("result"), response.toString());
        assertFalse(response.has("description"),
                "Root erase changed the historical success envelope");
        assertSame(root, user.getCurrentMind(),
                "Root erase replaced the published root Mind");
        assertEquals(0, user.getCurrentMind().getTransactionLevel(),
                "Root erase left an explicit transaction open");
        assertTrue(user.getCurrentMind().isStorageUsed(),
                "Root erase detached the active storage");
        assertEquals(storageName, user.getCurrentMind().getStorageName(),
                "Root erase changed the active storage identity");
        assertEquals("", SourceContextMaterializer.materializeCurrentLevel(root),
                "Root erase left source-representable state behind");
    }

    private void transactionBlocksDestructiveCommand(
            DestructiveStopLossReactor reactor,
            IUser user,
            String token) throws Exception {
        IMind parent = user.getCurrentMind();
        IMind child = new Mind(parent);
        user.setCurrentMind(child);
        String sourceBefore = child.getSourceCode();

        JSONObject compile = invoke(reactor, "query", new JSONObject()
                .put("token", token)
                .put("compile", URLEncoder.encode("!nested;", "UTF-8")));

        assertEquals("error", compile.optString("result"));
        assertEquals("transaction_open", compile.optString("code"));
        assertSame(child, user.getCurrentMind(),
                "Blocked Compile replaced the active transaction");
        assertEquals(sourceBefore, child.getSourceCode(),
                "Blocked Compile changed the transaction workspace");

        JSONObject erase = invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put("erase", ""));

        assertEquals("error", erase.optString("result"));
        assertEquals("transaction_open", erase.optString("code"));
        assertSame(child, user.getCurrentMind(),
                "Blocked erase replaced the active transaction");
        assertEquals(sourceBefore, child.getSourceCode(),
                "Blocked erase changed the transaction workspace");

        parent.release(child);
        user.setCurrentMind(parent);
    }

    private void utf8SourceRoundTripIsTruthful(
            DestructiveStopLossReactor reactor,
            IUser user,
            String token) throws Exception {
        IMind mind = user.getCurrentMind();
        assertTrue(mind.compile("// Привет, KANGER\n!b;"),
                "UTF-8 qualification source was rejected");

        String fileName = "utf8-stop-loss.k";
        JSONObject save = invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put("put", fileName));
        assertEquals("OK", save.optString("result"), save.toString());

        Path source = Paths.get(user.getSourceDir()).resolve(fileName);
        String persisted = new String(Files.readAllBytes(source),
                StandardCharsets.UTF_8);
        assertTrue(persisted.contains("Привет, KANGER"),
                "UTF-8 source was not persisted as UTF-8");

        JSONObject delete = invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put("delete", fileName));
        assertEquals("OK", delete.optString("result"), delete.toString());
        assertFalse(Files.exists(source),
                "Source delete returned success but file remains");

        JSONObject secondDelete = invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put("delete", fileName));
        assertEquals("error", secondDelete.optString("result"),
                "Deleting absent source returned success");
    }

    private void failedStorageSwitchPreservesCurrentGeneration(
            DestructiveStopLossReactor reactor,
            IUser user,
            String token,
            String currentStorage) throws Exception {
        IMind mind = user.getCurrentMind();
        String sourceBefore = mind.getSourceCode();
        Map<String, String> generationBefore = hashGeneration(user, currentStorage);

        String corruptName = "corrupt-switch-target";
        Path corruptBase = Paths.get(user.getDatabaseDir()).resolve(corruptName);
        Files.createDirectories(corruptBase.getParent());
        Files.write(Paths.get(corruptBase.toString() + ".store"),
                new byte[]{0x00, 0x01, 0x02, 0x03});
        try {
            JSONObject response = invoke(reactor, "command", new JSONObject()
                    .put("token", token)
                    .put("use", corruptName));

            assertEquals("error", response.optString("result"), response.toString());
            assertEquals(currentStorage, user.getCurrentMind().getStorageName(),
                    "Failed use changed the selected database");
            assertEquals(sourceBefore, user.getCurrentMind().getSourceCode(),
                    "Failed use changed the logical workspace");
            assertEquals(generationBefore, hashGeneration(user, currentStorage),
                    "Failed use changed the current persistent generation");
        } finally {
            deleteGeneration(user, corruptName);
        }
    }

    private void nestedStorageReindexAndDropUseCanonicalIdentity(
            DestructiveStopLossReactor reactor,
            IUser user,
            String token) throws Exception {
        IMind mind = user.getCurrentMind();
        mind = mind.closeStorage();
        user.setCurrentMind(mind);

        String storageName = "nested" + Enums.FILE_SEPARATOR + "one";
        mind = mind.useStorage(storageName);
        user.setCurrentMind(mind);
        assertTrue(mind.compile("!nested;"),
                "Nested storage source was rejected");
        assertFalse(hashGeneration(user, storageName).isEmpty(),
                "Nested storage generation was not created");

        JSONObject reindex = invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put("reindex", "nested.one"));
        assertEquals("OK", reindex.optString("result"), reindex.toString());
        assertEquals(storageName, user.getCurrentMind().getStorageName(),
                "Nested reindex did not reopen the canonical storage");
        assertTrue(user.getCurrentMind().getSourceCode().contains("nested"),
                "Nested reindex lost the logical source");

        JSONObject drop = invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put("drop", "nested.one"));
        assertEquals("OK", drop.optString("result"), drop.toString());
        assertTrue(hashGeneration(user, storageName).isEmpty(),
                "Nested storage drop left generation files");
    }

    private JSONObject invoke(
            DestructiveStopLossReactor reactor,
            String context,
            JSONObject parameters) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", context)
                .put("parameters", parameters));
        Object response = reactor.run(packet);
        assertTrue(response instanceof JSONObject,
                "API response is not a JSONObject: " + response);
        return (JSONObject) response;
    }

    private Map<String, String> hashGeneration(
            IUser user, String storageName) throws Exception {
        Map<String, String> result = new LinkedHashMap<String, String>();
        Path base = Paths.get(user.getDatabaseDir()).resolve(storageName);
        for (String suffix : GENERATION_SUFFIXES) {
            Path file = Paths.get(base.toString() + suffix);
            if (Files.exists(file)) {
                result.put(suffix, sha256(Files.readAllBytes(file)));
            }
        }
        return result;
    }

    private void deleteGeneration(IUser user, String storageName) throws Exception {
        Path base = Paths.get(user.getDatabaseDir()).resolve(storageName);
        for (String suffix : GENERATION_SUFFIXES) {
            Files.deleteIfExists(Paths.get(base.toString() + suffix));
        }
        Path directory = base.getParent();
        if (directory != null && Files.isDirectory(directory)) {
            String prefix = base.getFileName().toString() + ".wal.";
            try (java.nio.file.DirectoryStream<Path> stream =
                         Files.newDirectoryStream(directory)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)
                            && entry.getFileName().toString().startsWith(prefix)) {
                        Files.deleteIfExists(entry);
                    }
                }
            }
        }
    }

    private String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder();
        for (byte one : digest) {
            result.append(String.format("%02x", one & 0xff));
        }
        return result.toString();
    }
}
