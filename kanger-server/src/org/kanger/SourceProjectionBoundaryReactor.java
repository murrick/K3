/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.json.JSONObject;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IUser;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Semantic source view/export boundary for the current explicit Mind level. */
final class SourceProjectionBoundaryReactor implements IReactor<JSONObject> {
    private final IReactor<JSONObject> delegate;

    SourceProjectionBoundaryReactor(IReactor<JSONObject> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate reactor is required");
        }
        this.delegate = delegate;
    }

    @Override
    public Object run(JSONObject packet) throws Exception {
        JSONObject parameters = SessionSerializingReactor.parameters(packet);
        String context = CanonicalCommandIngressReactor.context(packet);
        boolean sourceView = "query".equalsIgnoreCase(context)
                && parameters.has("source") && !parameters.isNull("source");
        boolean sourcePut = "command".equalsIgnoreCase(context)
                && parameters.has("put") && !parameters.isNull("put");
        if (!sourceView && !sourcePut) {
            return delegate.run(packet);
        }

        IUser user = user(parameters);
        if (user == null) {
            return error("authentication_required", "User not logged in");
        }
        if (user.getCurrentMind() == null) {
            user.setCurrentMind(new Mind(user));
        }
        IMind mind = user.getCurrentMind();
        JSONObject result = sourceView ? source(mind) : put(parameters, user, mind);
        result.put("transaction", mind.getTransactionLevel());
        result.put("empty", mind.isEmptyLevel());
        return result;
    }

    private JSONObject source(IMind mind) throws Exception {
        return ok().put("source", SourceContextMaterializer.materializeCurrentLevel(mind));
    }

    private JSONObject put(JSONObject parameters, IUser user, IMind mind) throws Exception {
        String fileName = parameters.optString("put", "").trim();
        if (fileName.isEmpty()) {
            return error("source_name_required", "You have to select name for file.");
        }
        if (!ApiInputPolicy.isSafeLeafName(fileName)) {
            return error("source_name_invalid", "Invalid filesystem identifier in parameter put");
        }

        Path base = Paths.get(user.getSourceDir()).toAbsolutePath().normalize();
        Path target = base.resolve(fileName).normalize();
        if (!target.startsWith(base)) {
            return error("source_name_invalid", "Invalid filesystem identifier in parameter put");
        }
        Files.createDirectories(base);
        boolean existed = Files.exists(target);
        Path temporary = Files.createTempFile(base, target.getFileName().toString() + ".", ".tmp");
        boolean published = false;
        try {
            byte[] data = SourceContextMaterializer.materializeCurrentLevel(mind)
                    .getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            published = true;
            if (!existed) {
                Watchdog.log(user, "New source file created: " + fileName);
            }
            return ok().put("description", "Source file " + fileName + " saved.");
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private IUser user(JSONObject parameters) {
        String token = parameters.optString("token", "");
        if (token.isEmpty()) {
            return null;
        }
        try {
            return UserFactory.getUser(token);
        } catch (Exception unavailable) {
            return null;
        }
    }

    private JSONObject ok() {
        return new JSONObject().put("result", "OK");
    }

    private JSONObject error(String code, String description) {
        return new JSONObject().put("result", "error")
                .put("code", code).put("description", description);
    }
}
