package org.kanger.storage.dumb2.adapter;

import org.junit.jupiter.api.Test;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.dumb2.descriptor.StructuralValue;
import org.kanger.storage.dumb2.descriptor.StructuralValueCodec;
import org.kanger.storage.dumb2.descriptor.TypeDefinition;
import org.kanger.storage.dumb2.descriptor.TypeRegistry;
import org.kanger.units.Comment;
import org.kanger.units.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Qualification for the first simple Unit adapters beyond TValue. */
public class SimpleUnitAdapterTest {

    @Test
    void predicateRoundTripsThroughDescriptorBytes() throws Exception {
        Mind mind = new Mind(new User());
        ITerm name = mind.getTerms().add("likes");

        Predicate source = new Predicate(name, 2);
        source.setMind(mind);
        source.setId(17L);
        source.setMindId(mind.getId());

        KangerAdapterRegistry adapters = new KangerAdapterRegistry();
        TypeDefinition definition = new TypeRegistry().register(
                PredicateAdapter.TYPE_NAME, PredicateAdapter.INSTANCE.getDescriptor());

        StructuralValue projected = adapters.project(source, mind);
        byte[] bytes = StructuralValueCodec.encode(
                definition.getDescriptor(), projected);
        StructuralValue decoded = StructuralValueCodec.decode(
                definition.getDescriptor(), bytes);
        IUnit restored = adapters.materialize(definition, decoded, mind);

        assertSame(PredicateAdapter.INSTANCE, adapters.forRuntime(source));
        assertEquals(source.getId(), restored.getId());
        assertEquals(source.getMindId(), restored.getMindId());
        assertEquals(source.getHash(), restored.getHash());
        assertEquals("likes", ((Predicate) restored).getName(mind));
        assertEquals(2, ((Predicate) restored).getRange());
    }

    @Test
    void commentRoundTripsThroughDescriptorBytes() throws Exception {
        Mind mind = new Mind(new User());
        Comment source = new Comment(23L, "self describing", mind);
        source.setMindId(mind.getId());

        KangerAdapterRegistry adapters = new KangerAdapterRegistry();
        TypeDefinition definition = new TypeRegistry().register(
                CommentAdapter.TYPE_NAME, CommentAdapter.INSTANCE.getDescriptor());

        StructuralValue projected = adapters.project(source, mind);
        byte[] bytes = StructuralValueCodec.encode(
                definition.getDescriptor(), projected);
        StructuralValue decoded = StructuralValueCodec.decode(
                definition.getDescriptor(), bytes);
        Comment restored = (Comment) adapters.materialize(
                definition, decoded, mind);

        assertSame(CommentAdapter.INSTANCE, adapters.forRuntime(source));
        assertEquals(source.getId(), restored.getId());
        assertEquals(source.getMindId(), restored.getMindId());
        assertEquals(source.getComment(), restored.getComment());
    }
}
