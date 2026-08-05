/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
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

/** Qualification corpus for the 3.5.2.2 destructive-operation stop-loss boundary. */
public final class DestructiveStopLossQualification {

    private static final String[] GENERATION_SUFFIXES = {
            ".index", ".store", ".integrity", ".integrity.delta"
    };

    private DestructiveStopLossQualification() {
    }

    public static boolean test() {
        IUser user = null;
        try {
            String identity = "stop-loss-" + UUID.randomUUID().toString();
            user = UserFactory.createUser(identity, identity);
            new UDF().init(user);
            new DB().init(user);

            IMind mind = new Mind(user);
            String storageName = "stop-loss" + Enums.FILE_SEPARATOR + "compile";
            mind = mind.useStorage(storageName);
            user.setCurrentMind(mind);
            require(mind.compile("!a;"), "Initial source was rejected");

            String token = UserFactory.addUser(user);
            DestructiveStopLossReactor reactor = new DestructiveStopLossReactor(
                    new QueryProcessor());

            verifyRejectedCompilePreservesGeneration(
                    reactor, user, token, storageName);
            verifyTransactionBlocksDestructiveCommand(reactor, user, token);
            verifyUtf8SourceRoundTrip(reactor, user, token);
            verifyNestedStorageDrop(reactor, user, token);

            System.out.println("Destructive stop-loss qualification: PASS");
            return true;
        } catch (Throwable failure) {
            System.err.println("Destructive stop-loss qualification: FAIL");
            failure.printStackTrace(System.err);
            return false;
        } finally {
            if (user != null) {
                try {
                    UserFactory.dropUser(user);
                } catch (Throwable cleanupFailure) {
                    cleanupFailure.printStackTrace(System.err);
                }
            }
        }
    }

    private static void verifyRejectedCompilePreservesGeneration(
            DestructiveStopLossReactor reactor,
            IUser user,
            String token,
            String storageName) throws Exception {
        IMind mind = user.getCurrentMind();
        String sourceBefore = mind.getSourceCode();
        Map<String, String> generationBefore = hashGeneration(user, storageName);

        JSONObject response = invoke(reactor, "query", new JSONObject()
                .put("token", token)
                .put("compile", URLEncoder.encode("!broken(", "UTF-8")));

        require("error".equals(response.optString("result")),
                "Rejected Compile returned success: " + response);
        require(sourceBefore.equals(user.getCurrentMind().getSourceCode()),
                "Rejected Compile changed the logical workspace");
        require(generationBefore.equals(hashGeneration(user, storageName)),
                "Rejected Compile changed the persistent generation");
    }

    private static void verifyTransactionBlocksDestructiveCommand(
            DestructiveStopLossReactor reactor,
            IUser user,
            String token) throws Exception {
        IMind parent = user.getCurrentMind();
        IMind child = new Mind(parent);
        user.setCurrentMind(child);
        String sourceBefore = child.getSourceCode();

        JSONObject response = invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put("erase", ""));

        require("error".equals(response.optString("result")),
                "Erase inside transaction returned success");
        require("transaction_open".equals(response.optString("code")),
                "Erase inside transaction returned wrong error: " + response);
        require(user.getCurrentMind() == child,
                "Blocked erase replaced the active transaction");
        require(sourceBefore.equals(child.getSourceCode()),
                "Blocked erase changed the transaction workspace");

        parent.release(child);
        user.setCurrentMind(parent);
    }

    private static void verifyUtf8SourceRoundTrip(
            DestructiveStopLossReactor reactor,
            IUser user,
            String token) throws Exception {
        IMind mind = user.getCurrentMind();
        require(mind.compile("// Привет, KANGER\n!b;"),
                "UTF-8 qualification source was rejected");

        String fileName = "utf8-stop-loss.k";
        JSONObject save = invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put("put", fileName));
        require("OK".equals(save.optString("result")),
                "UTF-8 source save failed: " + save);

        Path source = Paths.get(user.getSourceDir()).resolve(fileName);
        String persisted = new String(Files.readAllBytes(source),
                StandardCharsets.UTF_8);
        require(persisted.contains("Привет, KANGER"),
                "UTF-8 source was not persisted as UTF-8");

        JSONObject delete = invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put("delete", fileName));
        require("OK".equals(delete.optString("result")),
                "Source delete failed: " + delete);
        require(!Files.exists(source),
                "Source delete returned success but file remains");

        JSONObject secondDelete = invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put("delete", fileName));
        require("error".equals(secondDelete.optString("result")),
                "Deleting absent source returned success");
    }

    private static void verifyNestedStorageDrop(
            DestructiveStopLossReactor reactor,
            IUser user,
            String token) throws Exception {
        IMind mind = user.getCurrentMind();
        mind = mind.closeStorage();
        user.setCurrentMind(mind);

        String storageName = "nested" + Enums.FILE_SEPARATOR + "one";
        mind = mind.useStorage(storageName);
        user.setCurrentMind(mind);
        require(mind.compile("!nested;"),
                "Nested storage source was rejected");
        require(!hashGeneration(user, storageName).isEmpty(),
                "Nested storage generation was not created");

        JSONObject drop = invoke(reactor, "command", new JSONObject()
                .put("token", token)
                .put("drop", "nested.one"));
        require("OK".equals(drop.optString("result")),
                "Nested storage drop failed: " + drop);
        require(hashGeneration(user, storageName).isEmpty(),
                "Nested storage drop left generation files");
    }

    private static JSONObject invoke(
            DestructiveStopLossReactor reactor,
            String context,
            JSONObject parameters) throws Exception {
        JSONObject packet = new JSONObject().put("body", new JSONObject()
                .put("context", context)
                .put("parameters", parameters));
        Object response = reactor.run(packet);
        require(response instanceof JSONObject,
                "API response is not a JSONObject: " + response);
        return (JSONObject) response;
    }

    private static Map<String, String> hashGeneration(
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

    private static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder();
        for (byte one : digest) {
            result.append(String.format("%02x", one & 0xff));
        }
        return result.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
