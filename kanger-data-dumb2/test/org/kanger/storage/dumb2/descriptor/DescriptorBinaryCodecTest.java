package org.kanger.storage.dumb2.descriptor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Binary-contract qualification for descriptor definitions stored by a future manifest. */
public class DescriptorBinaryCodecTest {

    @Test
    void persistentKindTagsAreExplicitAndNotEnumOrdinals() throws Exception {
        assertArrayEquals(new byte[]{
                        0x4B, 0x33, 0x44, 0x53,
                        0, 0, 0, 1,
                        3},
                DescriptorBinaryCodec.encode(Descriptor.INT64));
    }

    @Test
    void complexDescriptorRoundTripsCanonically() throws Exception {
        Descriptor kind = Descriptor.enumeration("ArgumentType",
                Arrays.asList("EMPTY", "TERM", "TVARIABLE"));
        Descriptor none = Descriptor.struct("None", Collections.<Descriptor.Field>emptyList());
        Descriptor value = Descriptor.variant("type", Arrays.asList(
                Descriptor.variantCase("EMPTY", none),
                Descriptor.variantCase("TERM", Descriptor.ref("dictionary")),
                Descriptor.variantCase("TVARIABLE", Descriptor.ref("tvariables"))));
        Descriptor source = Descriptor.struct("Argument-v1", Arrays.asList(
                Descriptor.field("type", kind),
                Descriptor.field("value", value),
                Descriptor.field("varOrder", Descriptor.INT32),
                Descriptor.field("legacy",
                        Descriptor.conditional(Descriptor.equalsInt64("varOrder", -1L),
                                Descriptor.list(Descriptor.INT64)))));

        byte[] encoded = DescriptorBinaryCodec.encode(source);
        Descriptor decoded = DescriptorBinaryCodec.decode(encoded);

        assertEquals(source, decoded);
        assertArrayEquals(encoded, DescriptorBinaryCodec.encode(decoded));
    }

    @Test
    void unsupportedVersionIsRejected() throws Exception {
        byte[] encoded = DescriptorBinaryCodec.encode(Descriptor.BOOL);
        encoded[7] = 2;

        assertThrows(IOException.class, () -> DescriptorBinaryCodec.decode(encoded));
    }

    @Test
    void unknownPersistentTagIsRejected() throws Exception {
        byte[] encoded = DescriptorBinaryCodec.encode(Descriptor.BOOL);
        encoded[8] = 99;

        assertThrows(IOException.class, () -> DescriptorBinaryCodec.decode(encoded));
    }

    @Test
    void trailingBytesAreRejected() throws Exception {
        byte[] encoded = DescriptorBinaryCodec.encode(Descriptor.BOOL);
        byte[] damaged = Arrays.copyOf(encoded, encoded.length + 1);

        assertThrows(IOException.class, () -> DescriptorBinaryCodec.decode(damaged));
    }
}
