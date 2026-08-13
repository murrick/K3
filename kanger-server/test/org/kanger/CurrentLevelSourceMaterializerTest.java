/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.factory.CommentFactory;
import org.kanger.interfaces.IRule;
import org.kanger.udf.UDF;
import org.kanger.units.Rule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qualification for current-U_n declarative source materialization. */
public class CurrentLevelSourceMaterializerTest {

    @Test
    public void materializationUsesExactLevelOwnershipWithoutParentAggregation()
            throws Exception {
        User user = new User();
        new UDF().init(user);

        Mind root = new Mind(user);
        assertTrue(root.compile("!root;"));
        Rule rootRule = ownedRule(root, "!root;");
        root.getComments().add(CommentFactory.HEADER_ID, "/* root header */");
        root.getComments().add(rootRule.getId(), "/* root rule */");

        Mind child = new Mind(root);
        assertTrue(child.compile("!child;"));
        Rule childRule = ownedRule(child, "!child;");
        child.getComments().add(CommentFactory.HEADER_ID, "/* child header */");
        child.getComments().add(childRule.getId(), "/* child rule */");
        child.getComments().add(rootRule.getId(), "/* inherited override */");

        String rootSource = CurrentLevelSourceMaterializer.materialize(root);
        assertTrue(rootSource.contains("/* root header */"));
        assertTrue(rootSource.contains("/* root rule */"));
        assertTrue(rootSource.contains("!root;"));
        assertFalse(rootSource.contains("!child;"));

        String childSource = CurrentLevelSourceMaterializer.materialize(child);
        assertTrue(childSource.contains("/* child header */"));
        assertTrue(childSource.contains("/* child rule */"));
        assertTrue(childSource.contains("!child;"));
        assertFalse(childSource.contains("!root;"));
        assertFalse(childSource.contains("/* inherited override */"));

        root.release(child);
    }

    @Test
    public void inheritedDeletionRemainsTransactionStateNotInventedSource()
            throws Exception {
        User user = new User();
        new UDF().init(user);

        Mind root = new Mind(user);
        assertTrue(root.compile("!base;"));

        Mind child = new Mind(root);
        child.query("-base;");

        String childSource = CurrentLevelSourceMaterializer.materialize(child);
        assertFalse(childSource.contains("!base;"));
        assertFalse(childSource.contains("-base;"));

        root.release(child);
    }

    private Rule ownedRule(Mind mind, String origin) throws Exception {
        for (IRule candidate : mind.getRules()) {
            Rule rule = (Rule) candidate;
            if (rule.getMindId() == mind.getId()
                    && !rule.isGenerated()
                    && !rule.isDeleted(mind)
                    && origin.equals(rule.getOrigin())) {
                return rule;
            }
        }
        throw new AssertionError("Owned Rule not found: " + origin);
    }
}
