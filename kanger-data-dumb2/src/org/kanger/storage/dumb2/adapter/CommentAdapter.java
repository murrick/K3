package org.kanger.storage.dumb2.adapter;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.storage.dumb2.descriptor.Descriptor;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.units.Comment;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit semantic projection for a persistent KANGER Comment. */
public final class CommentAdapter implements KangerUnitAdapter<Comment> {

    public static final String TYPE_NAME = "COMMENT";
    public static final String LAYOUT_NAME = "Comment-v1";
    public static final CommentAdapter INSTANCE = new CommentAdapter();

    private static final Descriptor DESCRIPTOR = Descriptor.struct(
            LAYOUT_NAME, Arrays.asList(
                    Descriptor.field("id", Descriptor.INT64),
                    Descriptor.field("mindId", Descriptor.INT64),
                    Descriptor.field("deleted", Descriptor.BOOL),
                    Descriptor.field("text", Descriptor.UTF8)));

    private CommentAdapter() {
    }

    @Override
    public UnitType getRuntimeType() {
        return UnitType.COMMENT;
    }

    @Override
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    public Descriptor getDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public StructuralValue project(Comment value, Mind mind) {
        if (value == null || mind == null) throw new NullPointerException();
        Map<String, StructuralValue> fields =
                new LinkedHashMap<String, StructuralValue>();
        fields.put("id", StructuralValue.int64(value.getId()));
        fields.put("mindId", StructuralValue.int64(value.getMindId()));
        fields.put("deleted", StructuralValue.bool(value.isDeleted(mind)));
        fields.put("text", StructuralValue.utf8(value.getComment()));
        return StructuralValue.struct(fields);
    }

    @Override
    public Comment materialize(StructuralValue value, Mind mind) throws Exception {
        if (value == null || mind == null) throw new NullPointerException();
        Map<String, StructuralValue> fields = requireStruct(value);
        long id = require(fields, "id", StructuralValue.Kind.INT64).asInt64();
        long mindId =
                require(fields, "mindId", StructuralValue.Kind.INT64).asInt64();
        boolean deleted =
                require(fields, "deleted", StructuralValue.Kind.BOOL).asBool();
        String text =
                require(fields, "text", StructuralValue.Kind.UTF8).asUtf8();

        Comment result = new Comment(id, text, mind);
        result.setMindId(mindId);
        if (deleted) result.setDeleted(true, mind);
        return result;
    }

    private static Map<String, StructuralValue> requireStruct(StructuralValue value)
            throws IOException {
        if (value.getKind() != StructuralValue.Kind.STRUCT)
            throw new IOException("COMMENT structural value must be STRUCT");
        return value.asStruct();
    }

    private static StructuralValue require(Map<String, StructuralValue> fields,
                                           String name,
                                           StructuralValue.Kind kind)
            throws IOException {
        StructuralValue value = fields.get(name);
        if (value == null || value.getKind() != kind)
            throw new IOException("Invalid or missing COMMENT field " + name);
        return value;
    }
}
