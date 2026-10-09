/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import org.kanger.interfaces.IRule;
import org.kanger.units.Rule;
import org.kanger.interfaces.internal.IContextFederation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Conservative diagnostic guard for bounded ordinary rule observations, not a durable revision ID. */
final class DmzObservationStateFingerprint {
    static String capture(Mind mind) throws Exception {
        List<String> rows = new ArrayList<String>();
        for (IRule candidate : mind.getRules()) {
            if (!candidate.isDeleted(mind)) {
                Rule rule = (Rule) candidate;
                DmzSourceCandidates candidates = DmzSourceCandidates.capture(mind, rule.getOrigin());
                rows.add("attribution:" + rule.getId() + ":" + candidates.status + ":" + candidates.unsupported);
                for (DmzSourceCandidates.Source source : candidates.sources)
                    rows.add("source:" + rule.getId() + ":" + source.context + ":" + source.revision
                            + ":" + source.rule + ":" + source.configured);
                rows.add("rule:" + rule.getId() + ":" + mind.getRules().isGenerated(rule)
                        + ":" + digest(rule.pack().getBuffer()) + ":" + rule.toString(mind));
            }
        }
        if (mind.isStorageUsed() && ((User) mind.getUser()).getData() instanceof IContextFederation) {
            IContextFederation.Snapshot snapshot = ((IContextFederation) ((User) mind.getUser()).getData()).federationSnapshot();
            rows.addAll(sourceRows(snapshot));
        }
        return digestRows(rows);
    }
    static String sourceSignature(IContextFederation.Snapshot snapshot) throws Exception {
        return digestRows(sourceRows(snapshot));
    }
    private static List<String> sourceRows(IContextFederation.Snapshot snapshot) {
        List<String> rows = new ArrayList<String>();
        rows.add("context:" + snapshot.getSourceContextId() + ":" + snapshot.getSourceRevision());
        for (IContextFederation.Connection connection : snapshot.getConnections()) {
            StringBuilder row = new StringBuilder("pin:");
            String[] fields = { connection.getTargetContextId().toString(), String.valueOf(connection.getPinnedRevision()),
                String.valueOf(connection.getCurrentRevision()), String.valueOf(connection.getPinPolicy()),
                String.valueOf(connection.getCompatibilityStatus()), connection.getSemanticVersion(),
                String.valueOf(connection.getTrustGroup()) };
            for (String field : fields) row.append(field.length()).append(':').append(field);
            row.append(connection.getInitialization().size()).append(':');
            for (String initialization : connection.getInitialization())
                row.append(initialization.length()).append(':').append(initialization);
            rows.add(row.toString());
        }
        return rows;
    }
    private static String digestRows(List<String> rows) throws Exception {
        Collections.sort(rows);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String row : rows) {
            byte[] bytes = row.getBytes(StandardCharsets.UTF_8);
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
            digest.update(bytes);
        }
        return hex(digest.digest());
    }
    private static String digest(byte[] bytes) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format("%02x", value & 255));
        return result.toString();
    }
}
