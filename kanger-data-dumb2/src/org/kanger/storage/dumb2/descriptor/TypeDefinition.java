package org.kanger.storage.dumb2.descriptor;

import java.util.Objects;

/** Immutable Context-local association of persistent typeCode, symbolic type and layout. */
public final class TypeDefinition {

    private final int typeCode;
    private final String typeName;
    private final Descriptor descriptor;

    TypeDefinition(int typeCode, String typeName, Descriptor descriptor) {
        if (typeCode <= 0) {
            throw new IllegalArgumentException("typeCode must be positive");
        }
        if (typeName == null || typeName.trim().isEmpty()) {
            throw new IllegalArgumentException("typeName must not be blank");
        }
        this.typeCode = typeCode;
        this.typeName = typeName;
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    public int getTypeCode() { return typeCode; }
    public String getTypeName() { return typeName; }
    public Descriptor getDescriptor() { return descriptor; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TypeDefinition)) return false;
        TypeDefinition that = (TypeDefinition) other;
        return typeCode == that.typeCode
                && typeName.equals(that.typeName)
                && descriptor.equals(that.descriptor);
    }

    @Override public int hashCode() {
        return Objects.hash(typeCode, typeName, descriptor);
    }
}
