package org.kanger.storage.dumb2.descriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable neutral value tree consumed by the descriptor-driven DUMB 2.0 codec. */
public final class StructuralValue {

    public enum Kind { BOOL, INT32, INT64, FLOAT64, UTF8, BYTES, REF, LIST, STRUCT, ENUM }

    private final Kind kind;
    private final Object value;
    private final String name;

    private StructuralValue(Kind kind, Object value, String name) {
        this.kind = kind;
        this.value = value;
        this.name = name;
    }

    public static StructuralValue bool(boolean value) { return new StructuralValue(Kind.BOOL, value, null); }
    public static StructuralValue int32(int value) { return new StructuralValue(Kind.INT32, value, null); }
    public static StructuralValue int64(long value) { return new StructuralValue(Kind.INT64, value, null); }
    public static StructuralValue float64(double value) { return new StructuralValue(Kind.FLOAT64, value, null); }
    public static StructuralValue utf8(String value) { return new StructuralValue(Kind.UTF8, Objects.requireNonNull(value, "value"), null); }
    public static StructuralValue bytes(byte[] value) { return new StructuralValue(Kind.BYTES, Arrays.copyOf(Objects.requireNonNull(value, "value"), value.length), null); }
    public static StructuralValue ref(String schema, long id) { return new StructuralValue(Kind.REF, id, requireName(schema, "schema")); }
    public static StructuralValue enumeration(String enumName, String symbol) { return new StructuralValue(Kind.ENUM, requireName(symbol, "symbol"), requireName(enumName, "enum name")); }

    public static StructuralValue list(List<StructuralValue> values) {
        if (values == null) throw new NullPointerException("values");
        List<StructuralValue> copy = new ArrayList<StructuralValue>(values.size());
        for (StructuralValue value : values) copy.add(Objects.requireNonNull(value, "list value"));
        return new StructuralValue(Kind.LIST, Collections.unmodifiableList(copy), null);
    }

    public static StructuralValue struct(Map<String, StructuralValue> fields) {
        if (fields == null) throw new NullPointerException("fields");
        LinkedHashMap<String, StructuralValue> copy = new LinkedHashMap<String, StructuralValue>();
        for (Map.Entry<String, StructuralValue> entry : fields.entrySet()) {
            String name = requireName(entry.getKey(), "field name");
            copy.put(name, Objects.requireNonNull(entry.getValue(), "field value"));
        }
        return new StructuralValue(Kind.STRUCT, Collections.unmodifiableMap(copy), null);
    }

    public Kind getKind() { return kind; }
    public boolean asBool() { return (Boolean) value; }
    public int asInt32() { return (Integer) value; }
    public long asInt64() { return (Long) value; }
    public double asFloat64() { return (Double) value; }
    public String asUtf8() { return (String) value; }
    public byte[] asBytes() { return Arrays.copyOf((byte[]) value, ((byte[]) value).length); }
    public long getReferenceId() { return (Long) value; }
    public String getReferenceSchema() { return name; }
    @SuppressWarnings("unchecked") public List<StructuralValue> asList() { return (List<StructuralValue>) value; }
    @SuppressWarnings("unchecked") public Map<String, StructuralValue> asStruct() { return (Map<String, StructuralValue>) value; }
    public String getEnumName() { return name; }
    public String getEnumSymbol() { return (String) value; }

    private static String requireName(String value, String label) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StructuralValue)) return false;
        StructuralValue that = (StructuralValue) other;
        if (kind != that.kind || !Objects.equals(name, that.name)) return false;
        if (kind == Kind.BYTES) return Arrays.equals((byte[]) value, (byte[]) that.value);
        return Objects.equals(value, that.value);
    }

    @Override public int hashCode() {
        return 31 * Objects.hash(kind, name)
                + (kind == Kind.BYTES ? Arrays.hashCode((byte[]) value) : Objects.hashCode(value));
    }
}
