package org.kanger.storage.dumb2.descriptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generic little-endian codec driven only by a structural descriptor. */
public final class StructuralValueCodec {

    private StructuralValueCodec() {
    }

    public static byte[] encode(Descriptor descriptor, StructuralValue value) throws IOException {
        if (descriptor == null || value == null) throw new NullPointerException();
        Output output = new Output();
        write(output, descriptor, value, null, null);
        return output.bytes();
    }

    public static StructuralValue decode(Descriptor descriptor, byte[] bytes) throws IOException {
        if (descriptor == null || bytes == null) throw new NullPointerException();
        Input input = new Input(bytes);
        StructuralValue value = read(input, descriptor, null, null);
        if (input.remaining() != 0) throw new IOException("Trailing bytes in DUMB2 structural value");
        return value;
    }

    private static void write(Output out, Descriptor descriptor, StructuralValue value,
                              Descriptor self, Map<String, StructuralValue> scope) throws IOException {
        switch (descriptor.getKind()) {
            case NULL:
                require(value, StructuralValue.Kind.NULL); return;
            case BOOL:
                require(value, StructuralValue.Kind.BOOL); out.u8(value.asBool() ? 1 : 0); return;
            case INT32:
                require(value, StructuralValue.Kind.INT32); out.i32(value.asInt32()); return;
            case INT64:
                require(value, StructuralValue.Kind.INT64); out.i64(value.asInt64()); return;
            case FLOAT64:
                require(value, StructuralValue.Kind.FLOAT64); out.i64(Double.doubleToLongBits(value.asFloat64())); return;
            case UTF8: {
                require(value, StructuralValue.Kind.UTF8);
                byte[] bytes = value.asUtf8().getBytes(StandardCharsets.UTF_8);
                out.length(bytes.length); out.raw(bytes); return;
            }
            case BYTES: {
                require(value, StructuralValue.Kind.BYTES);
                byte[] bytes = value.asBytes(); out.length(bytes.length); out.raw(bytes); return;
            }
            case SELF:
                if (self == null) throw new IOException("SELF outside a named STRUCT");
                write(out, self, value, self, null); return;
            case REF:
                require(value, StructuralValue.Kind.REF);
                if (!descriptor.getTarget().equals(value.getReferenceSchema()))
                    throw new IOException("Reference namespace mismatch: expected " + descriptor.getTarget()
                            + " actual " + value.getReferenceSchema());
                out.i64(value.getReferenceId()); return;
            case LIST:
                require(value, StructuralValue.Kind.LIST);
                out.length(value.asList().size());
                for (StructuralValue one : value.asList()) write(out, descriptor.getElement(), one, self, null);
                return;
            case ENUM:
                require(value, StructuralValue.Kind.ENUM);
                if (!descriptor.getName().equals(value.getEnumName()))
                    throw new IOException("Enum name mismatch: expected " + descriptor.getName()
                            + " actual " + value.getEnumName());
                int code = descriptor.getSymbols().indexOf(value.getEnumSymbol());
                if (code < 0) throw new IOException("Unknown enum symbol " + value.getEnumSymbol());
                out.i32(code); return;
            case STRUCT:
                writeStruct(out, descriptor, value); return;
            case VARIANT:
                if (scope == null) throw new IOException("VARIANT outside STRUCT field scope");
                Descriptor selected = selectCase(descriptor, scope);
                write(out, selected, value, self, null); return;
            case CONDITIONAL:
                if (scope == null || !matches(descriptor.getCondition(), scope))
                    throw new IOException("CONDITIONAL value is not active");
                write(out, descriptor.getElement(), value, self, null); return;
            default:
                throw new IOException("Unsupported descriptor kind " + descriptor.getKind());
        }
    }

    private static void writeStruct(Output out, Descriptor descriptor, StructuralValue value)
            throws IOException {
        require(value, StructuralValue.Kind.STRUCT);
        Map<String, StructuralValue> source = value.asStruct();
        Map<String, StructuralValue> scope = new LinkedHashMap<String, StructuralValue>();
        Descriptor self = descriptor;
        for (Descriptor.Field field : descriptor.getFields()) {
            Descriptor fieldDescriptor = field.getDescriptor();
            boolean active = fieldDescriptor.getKind() != Descriptor.Kind.CONDITIONAL
                    || matches(fieldDescriptor.getCondition(), scope);
            StructuralValue fieldValue = source.get(field.getName());
            if (!active) {
                if (fieldValue != null) throw new IOException("Inactive conditional field present: " + field.getName());
                continue;
            }
            if (fieldValue == null) throw new IOException("Missing struct field " + field.getName());
            write(out, fieldDescriptor, fieldValue, self, scope);
            scope.put(field.getName(), fieldValue);
        }
        for (String name : source.keySet()) {
            boolean known = false;
            for (Descriptor.Field field : descriptor.getFields()) {
                if (field.getName().equals(name)) { known = true; break; }
            }
            if (!known) throw new IOException("Unknown struct field " + name);
        }
    }

    private static StructuralValue read(Input in, Descriptor descriptor, Descriptor self,
                                        Map<String, StructuralValue> scope) throws IOException {
        switch (descriptor.getKind()) {
            case NULL: return StructuralValue.nullValue();
            case BOOL: {
                int value = in.u8();
                if (value != 0 && value != 1) throw new IOException("Invalid BOOL value " + value);
                return StructuralValue.bool(value != 0);
            }
            case INT32: return StructuralValue.int32(in.i32());
            case INT64: return StructuralValue.int64(in.i64());
            case FLOAT64: return StructuralValue.float64(Double.longBitsToDouble(in.i64()));
            case UTF8: return StructuralValue.utf8(new String(in.block(), StandardCharsets.UTF_8));
            case BYTES: return StructuralValue.bytes(in.block());
            case SELF:
                if (self == null) throw new IOException("SELF outside a named STRUCT");
                return read(in, self, self, null);
            case REF: return StructuralValue.ref(descriptor.getTarget(), in.i64());
            case LIST: {
                int count = in.length();
                List<StructuralValue> values = new ArrayList<StructuralValue>(count);
                for (int i = 0; i < count; ++i) values.add(read(in, descriptor.getElement(), self, null));
                return StructuralValue.list(values);
            }
            case ENUM: {
                int code = in.i32();
                if (code < 0 || code >= descriptor.getSymbols().size())
                    throw new IOException("Unknown enum code " + code + " for " + descriptor.getName());
                return StructuralValue.enumeration(descriptor.getName(), descriptor.getSymbols().get(code));
            }
            case STRUCT: return readStruct(in, descriptor);
            case VARIANT:
                if (scope == null) throw new IOException("VARIANT outside STRUCT field scope");
                return read(in, selectCase(descriptor, scope), self, null);
            case CONDITIONAL:
                if (scope == null || !matches(descriptor.getCondition(), scope))
                    throw new IOException("CONDITIONAL value is not active");
                return read(in, descriptor.getElement(), self, null);
            default: throw new IOException("Unsupported descriptor kind " + descriptor.getKind());
        }
    }

    private static StructuralValue readStruct(Input in, Descriptor descriptor) throws IOException {
        LinkedHashMap<String, StructuralValue> values = new LinkedHashMap<String, StructuralValue>();
        Map<String, StructuralValue> scope = new LinkedHashMap<String, StructuralValue>();
        Descriptor self = descriptor;
        for (Descriptor.Field field : descriptor.getFields()) {
            Descriptor fieldDescriptor = field.getDescriptor();
            if (fieldDescriptor.getKind() == Descriptor.Kind.CONDITIONAL
                    && !matches(fieldDescriptor.getCondition(), scope)) continue;
            StructuralValue value = read(in, fieldDescriptor, self, scope);
            values.put(field.getName(), value);
            scope.put(field.getName(), value);
        }
        return StructuralValue.struct(values);
    }

    private static Descriptor selectCase(Descriptor descriptor, Map<String, StructuralValue> scope)
            throws IOException {
        StructuralValue discriminator = scope.get(descriptor.getDiscriminatorField());
        if (discriminator == null || discriminator.getKind() != StructuralValue.Kind.ENUM)
            throw new IOException("Variant discriminator is not a decoded ENUM: "
                    + descriptor.getDiscriminatorField());
        String symbol = discriminator.getEnumSymbol();
        for (Descriptor.VariantCase one : descriptor.getCases())
            if (one.getTag().equals(symbol)) return one.getDescriptor();
        throw new IOException("No variant case for " + symbol);
    }

    private static boolean matches(Descriptor.Condition condition, Map<String, StructuralValue> scope)
            throws IOException {
        StructuralValue value = scope.get(condition.getFieldName());
        if (value == null) throw new IOException("Condition field not decoded: " + condition.getFieldName());
        long actual;
        if (value.getKind() == StructuralValue.Kind.INT32) actual = value.asInt32();
        else if (value.getKind() == StructuralValue.Kind.INT64) actual = value.asInt64();
        else throw new IOException("Condition field is not integer: " + condition.getFieldName());
        switch (condition.getOperator()) {
            case EQUALS_INT64: return actual == condition.getOperand();
            case GREATER_THAN_INT64: return actual > condition.getOperand();
            default: throw new IOException("Unsupported condition operator " + condition.getOperator());
        }
    }

    private static void require(StructuralValue value, StructuralValue.Kind expected) throws IOException {
        if (value.getKind() != expected)
            throw new IOException("Structural value kind mismatch: expected " + expected
                    + " actual " + value.getKind());
    }

    private static final class Output {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        void u8(int value) { bytes.write(value & 0xff); }
        void i32(int value) { u8(value); u8(value >>> 8); u8(value >>> 16); u8(value >>> 24); }
        void i64(long value) { for (int i = 0; i < 8; ++i) u8((int) (value >>> (8 * i))); }
        void length(int value) throws IOException { if (value < 0) throw new IOException("Negative length"); i32(value); }
        void raw(byte[] value) { bytes.write(value, 0, value.length); }
        byte[] bytes() { return bytes.toByteArray(); }
    }

    private static final class Input {
        private final byte[] bytes;
        private int offset;

        Input(byte[] bytes) { this.bytes = bytes; }
        int remaining() { return bytes.length - offset; }
        int u8() throws IOException {
            if (remaining() < 1) throw new IOException("Truncated DUMB2 structural value");
            return bytes[offset++] & 0xff;
        }
        int i32() throws IOException { return u8() | (u8() << 8) | (u8() << 16) | (u8() << 24); }
        long i64() throws IOException {
            long value = 0L;
            for (int i = 0; i < 8; ++i) value |= ((long) u8()) << (8 * i);
            return value;
        }
        int length() throws IOException {
            int value = i32();
            if (value < 0) throw new IOException("Invalid length " + (value & 0xffffffffL));
            return value;
        }
        byte[] block() throws IOException {
            int length = length();
            if (length > remaining()) throw new IOException("Truncated DUMB2 block");
            byte[] result = new byte[length];
            System.arraycopy(bytes, offset, result, 0, length);
            offset += length;
            return result;
        }
    }
}
