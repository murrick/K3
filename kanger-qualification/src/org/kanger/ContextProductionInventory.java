/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.kanger.interfaces.IRule;
import org.kanger.units.Rule;

/** Complete bounded effective Rule-factory inventory, not a declaration replay or proof certificate. */
public final class ContextProductionInventory {
    public static final class Row {
        public final long nativeRule;
        public final boolean generated, deleted, query;
        public final String origin, nativeText, nativeDigest;
        private Row(Mind mind,Rule rule) throws Exception {
            nativeRule = rule.getId(); generated = mind.getRules().isGenerated(rule); deleted = rule.isDeleted(mind); query = rule.isQuery();
            origin = rule.getOrigin(); nativeText = rule.toString(mind); nativeDigest = hex(MessageDigest.getInstance("SHA-256").digest(rule.pack().getBuffer()));
        }
    }
    public final UUID context;
    public final long revision;
    public final boolean localX;
    public final String fingerprint;
    public final List<String> initialization;
    public final List<Row> rows, primary, generated;
    private ContextProductionInventory(UUID context,long revision,boolean localX,List<String> initialization,List<Row> rows,String fingerprint) {
        this.context = context; this.revision = revision; this.localX = localX; this.fingerprint = fingerprint;
        this.initialization = Collections.unmodifiableList(new ArrayList<>(initialization));
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        List<Row> primary = new ArrayList<>(), generated = new ArrayList<>();
        for (Row row : rows) if (!row.deleted) { if (row.generated) generated.add(row); else primary.add(row); }
        this.primary = Collections.unmodifiableList(primary); this.generated = Collections.unmodifiableList(generated);
    }
    /** Identity/configuration must be acquired by the owning storage provider, not inferred from text. */
    public static ContextProductionInventory inspect(Mind mind,UUID context,long revision,boolean localX,List<String> initialization,int maximumRows) throws Exception {
        if (mind == null || context == null || revision < 0 || maximumRows < 0 || maximumRows > 10000)
            throw new IllegalArgumentException("Bounded exact inventory required");
        List<String> commands = new ArrayList<>(initialization); List<Row> rows = new ArrayList<>(); Set<Long> ids = new HashSet<>();
        for (IRule item : mind.getRules()) {
            if (rows.size() == maximumRows) throw new IllegalStateException("Production inventory row limit");
            Row row = new Row(mind,(Rule)item);
            if (!ids.add(row.nativeRule)) throw new IllegalStateException("Duplicate native inventory row");
            if (row.query && !row.deleted) throw new IllegalStateException("Query rule in production inventory");
            rows.add(row);
        }
        rows.sort(Comparator.comparingLong(row -> row.nativeRule)); MessageDigest digest = MessageDigest.getInstance("SHA-256");
        text(digest,context.toString()); text(digest,Long.toString(revision)); text(digest,Boolean.toString(localX));
        text(digest,Integer.toString(commands.size())); for (String command : commands) text(digest,command);
        for (Row row : rows) {
            text(digest,Long.toString(row.nativeRule)); text(digest,Boolean.toString(row.generated)); text(digest,Boolean.toString(row.deleted));
            text(digest,Boolean.toString(row.query)); text(digest,row.origin); text(digest,row.nativeText); text(digest,row.nativeDigest);
        }
        return new ContextProductionInventory(context,revision,localX,commands,rows,hex(digest.digest()));
    }
    private static void text(MessageDigest digest,String value) {
        if (value == null) { digest.update(ByteBuffer.allocate(4).putInt(-1).array()); return; }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
    }
    private static String hex(byte[] bytes) { StringBuilder text = new StringBuilder(); for (byte b : bytes) text.append(String.format(Locale.ROOT,"%02x",b&255)); return text.toString(); }
}
