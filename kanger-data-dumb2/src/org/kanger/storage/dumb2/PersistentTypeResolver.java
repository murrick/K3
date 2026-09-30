package org.kanger.storage.dumb2;

import org.kanger.storage.dumb2.descriptor.TypeDefinition;

/**
 * Minimal descriptor lookup needed to decode a published DUMB2 record.
 *
 * <p>The mutable ContextStore and immutable read snapshots both implement this
 * boundary. Persistent decoding therefore depends on Context metadata, not on
 * write ownership or a live storage lock.</p>
 */
interface PersistentTypeResolver {

    TypeDefinition resolveType(int typeCode);
}
