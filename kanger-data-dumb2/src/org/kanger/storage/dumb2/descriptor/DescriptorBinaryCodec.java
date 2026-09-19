package org.kanger.storage.dumb2.descriptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Canonical binary codec for DUMB 2.0 structural descriptor definitions. */
public final class DescriptorBinaryCodec {

    private static final int MAGIC = 0x4B334453; // K3DS
    private static final int VERSION = 1;

    // Explicit persistent tags. They are deliberately unrelated to enum ordinal().
    private static final int T_BOOL = 1;
    private static final int T_INT32 = 2;
    private static final int T_INT64 = 3;
    private static final int T_FLOAT64 = 4;
    private static final int T_UTF8 = 5;
    private static final int T_BYTES = 6;
    private static final int T_SELF = 7;
    private static final int T_REF = 8;
    private static final int T_LIST = 9;
    private static final int T_STRUCT = 10;
    private static final int T_ENUM = 11;
    private static final int T_VARIANT = 12;
    private static final int T_CONDITIONAL = 13;

    private static final int C_EQUALS_INT64 = 1;
    private static final int C_GREATER_THAN_INT64 = 2;

    private DescriptorBinaryCodec() {
    }

    public static byte[] encode(Descriptor descriptor) throws IOException {
        if (descriptor == null) {
            throw new NullPointerException("descriptor");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        writeDescriptor(output, descriptor);
        output.flush();
        return bytes.toByteArray();
    }

    public static Descriptor decode(byte[] bytes) throws IOException {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
        try {
            int magic = input.readInt();
            int version = input.readInt();
            if (magic != MAGIC || version != VERSION) {
                throw new IOException("Unsupported DUMB2 descriptor format");
            }
            Descriptor descriptor = readDescriptor(input);
            if (input.available() != 0) {
                throw new IOException("Trailing bytes in DUMB2 descriptor");
            }
            return descriptor;
        } catch (EOFException failure) {
            throw new IOException("Truncated DUMB2 descriptor", failure);
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid DUMB2 descriptor", failure);
        }
    }

    private static void writeDescriptor(DataOutputStream output, Descriptor descriptor)
            throws IOException {
        switch (descriptor.getKind()) {
            case BOOL:
                output.writeByte(T_BOOL);
                return;
            case INT32:
                output.writeByte(T_INT32);
                return;
            case INT64:
                output.writeByte(T_INT64);
                return;
            case FLOAT64:
                output.writeByte(T_FLOAT64);
                return;
            case UTF8:
                output.writeByte(T_UTF8);
                return;
            case BYTES:
                output.writeByte(T_BYTES);
                return;
            case SELF:
                output.writeByte(T_SELF);
                return;
            case REF:
                output.writeByte(T_REF);
                writeString(output, descriptor.getTarget());
                return;
            case LIST:
                output.writeByte(T_LIST);
                writeDescriptor(output, descriptor.getElement());
                return;
            case STRUCT:
                output.writeByte(T_STRUCT);
                writeString(output, descriptor.getName());
                output.writeInt(descriptor.getFields().size());
                for (Descriptor.Field field : descriptor.getFields()) {
                    writeString(output, field.getName());
                    writeDescriptor(output, field.getDescriptor());
                }
                return;
            case ENUM:
                output.writeByte(T_ENUM);
                writeString(output, descriptor.getName());
                output.writeInt(descriptor.getSymbols().size());
                for (String symbol : descriptor.getSymbols()) {
                    writeString(output, symbol);
                }
                return;
            case VARIANT:
                output.writeByte(T_VARIANT);
                writeString(output, descriptor.getDiscriminatorField());
                output.writeInt(descriptor.getCases().size());
                for (Descriptor.VariantCase one : descriptor.getCases()) {
                    writeString(output, one.getTag());
                    writeDescriptor(output, one.getDescriptor());
                }
                return;
            case CONDITIONAL:
                output.writeByte(T_CONDITIONAL);
                writeCondition(output, descriptor.getCondition());
                writeDescriptor(output, descriptor.getElement());
                return;
            default:
                throw new IOException("Unsupported descriptor kind " + descriptor.getKind());
        }
    }

    private static Descriptor readDescriptor(DataInputStream input) throws IOException {
        int tag = input.readUnsignedByte();
        switch (tag) {
            case T_BOOL:
                return Descriptor.BOOL;
            case T_INT32:
                return Descriptor.INT32;
            case T_INT64:
                return Descriptor.INT64;
            case T_FLOAT64:
                return Descriptor.FLOAT64;
            case T_UTF8:
                return Descriptor.UTF8;
            case T_BYTES:
                return Descriptor.BYTES;
            case T_SELF:
                return Descriptor.SELF;
            case T_REF:
                return Descriptor.ref(readString(input));
            case T_LIST:
                return Descriptor.list(readDescriptor(input));
            case T_STRUCT: {
                String name = readString(input);
                int count = readCount(input, "struct field");
                List<Descriptor.Field> fields = new ArrayList<Descriptor.Field>(count);
                for (int i = 0; i < count; ++i) {
                    fields.add(Descriptor.field(readString(input), readDescriptor(input)));
                }
                return Descriptor.struct(name, fields);
            }
            case T_ENUM: {
                String name = readString(input);
                int count = readCount(input, "enum symbol");
                List<String> symbols = new ArrayList<String>(count);
                for (int i = 0; i < count; ++i) {
                    symbols.add(readString(input));
                }
                return Descriptor.enumeration(name, symbols);
            }
            case T_VARIANT: {
                String discriminator = readString(input);
                int count = readCount(input, "variant case");
                List<Descriptor.VariantCase> cases = new ArrayList<Descriptor.VariantCase>(count);
                for (int i = 0; i < count; ++i) {
                    cases.add(Descriptor.variantCase(readString(input), readDescriptor(input)));
                }
                return Descriptor.variant(discriminator, cases);
            }
            case T_CONDITIONAL:
                return Descriptor.conditional(readCondition(input), readDescriptor(input));
            default:
                throw new IOException("Unknown DUMB2 descriptor tag " + tag);
        }
    }

    private static void writeCondition(DataOutputStream output, Descriptor.Condition condition)
            throws IOException {
        switch (condition.getOperator()) {
            case EQUALS_INT64:
                output.writeByte(C_EQUALS_INT64);
                break;
            case GREATER_THAN_INT64:
                output.writeByte(C_GREATER_THAN_INT64);
                break;
            default:
                throw new IOException("Unsupported condition operator " + condition.getOperator());
        }
        writeString(output, condition.getFieldName());
        output.writeLong(condition.getOperand());
    }

    private static Descriptor.Condition readCondition(DataInputStream input) throws IOException {
        int tag = input.readUnsignedByte();
        String field = readString(input);
        long operand = input.readLong();
        switch (tag) {
            case C_EQUALS_INT64:
                return Descriptor.equalsInt64(field, operand);
            case C_GREATER_THAN_INT64:
                return Descriptor.greaterThanInt64(field, operand);
            default:
                throw new IOException("Unknown DUMB2 condition tag " + tag);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > input.available()) {
            throw new IOException("Invalid DUMB2 descriptor string length " + length);
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readCount(DataInputStream input, String label) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > input.available()) {
            throw new IOException("Invalid DUMB2 " + label + " count " + count);
        }
        return count;
    }
}
