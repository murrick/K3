package org.kanger.storage.dumb2.descriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Context-local append-only registry of persistent type definitions.
 *
 * <p>A published code is immutable. Registering the same symbolic type and
 * descriptor is idempotent; registering a new layout for the same type name
 * allocates a new code, allowing mixed physical layouts to coexist.</p>
 */
public final class TypeRegistry {

    private final Map<Integer, TypeDefinition> byCode =
            new LinkedHashMap<Integer, TypeDefinition>();
    private int nextCode = 1;

    public synchronized TypeDefinition register(String typeName, Descriptor descriptor) {
        requireName(typeName);
        Objects.requireNonNull(descriptor, "descriptor");
        for (TypeDefinition definition : byCode.values()) {
            if (definition.getTypeName().equals(typeName)
                    && definition.getDescriptor().equals(descriptor)) {
                return definition;
            }
        }
        if (nextCode <= 0) {
            throw new IllegalStateException("DUMB2 typeCode space exhausted");
        }
        TypeDefinition definition = new TypeDefinition(nextCode, typeName, descriptor);
        byCode.put(nextCode, definition);
        if (nextCode == Integer.MAX_VALUE) {
            nextCode = 0;
        } else {
            ++nextCode;
        }
        return definition;
    }

    /**
     * Installs a definition read from a manifest. Existing codes may only be
     * repeated with exactly the same immutable definition.
     */
    public synchronized TypeDefinition install(int typeCode,
                                               String typeName,
                                               Descriptor descriptor) {
        TypeDefinition incoming = new TypeDefinition(typeCode, typeName, descriptor);
        TypeDefinition existing = byCode.get(typeCode);
        if (existing != null) {
            if (!existing.equals(incoming)) {
                throw new IllegalStateException(
                        "DUMB2 typeCode " + typeCode + " is already published");
            }
            return existing;
        }
        byCode.put(typeCode, incoming);
        if (typeCode >= nextCode) {
            if (typeCode == Integer.MAX_VALUE) {
                nextCode = 0;
            } else {
                nextCode = typeCode + 1;
            }
        }
        return incoming;
    }

    public synchronized TypeDefinition resolve(int typeCode) {
        TypeDefinition definition = byCode.get(typeCode);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown DUMB2 typeCode " + typeCode);
        }
        return definition;
    }

    public synchronized List<TypeDefinition> definitions() {
        return Collections.unmodifiableList(
                new ArrayList<TypeDefinition>(byCode.values()));
    }

    public synchronized int size() {
        return byCode.size();
    }

    private static void requireName(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) {
            throw new IllegalArgumentException("typeName must not be blank");
        }
    }
}
