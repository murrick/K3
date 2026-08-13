/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RuleLevelParserContractTest {

    @Test
    void bareRuleLevelSelectsAggregateWithoutSyntheticLevel() throws Exception {
        CommandInvocation invocation = new CommandParser().parse("rule level");

        assertEquals(CommandIntent.RULE_LEVEL, invocation.getIntent());
        assertFalse(invocation.getArguments().containsKey("level"));
    }

    @Test
    void numberedRuleLevelKeepsQualifiedPointArgument() throws Exception {
        CommandInvocation invocation = new CommandParser().parse("rule level 2");

        assertEquals(CommandIntent.RULE_LEVEL, invocation.getIntent());
        assertEquals(2L, ((Number) invocation.getArgument("level")).longValue());
    }

    @Test
    void registryPublishesOptionalLevelSyntax() {
        assertEquals("rule level [<n>]",
                CommandRegistry.definition(CommandIntent.RULE_LEVEL).getSyntax());
    }
}
