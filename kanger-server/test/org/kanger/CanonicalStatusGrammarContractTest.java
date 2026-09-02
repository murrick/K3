/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.command.CommandIntent;
import org.kanger.command.CommandInvocation;
import org.kanger.command.CommandParseException;
import org.kanger.command.CommandParser;
import org.kanger.command.CommandRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanonicalStatusGrammarContractTest {

    private final CommandParser parser = new CommandParser();

    @Test
    void bareStatusSelectsWholeCanonicalSnapshot() throws Exception {
        CommandInvocation invocation = parser.parse("status");

        assertEquals(CommandIntent.STATUS, invocation.getIntent());
        assertFalse(invocation.getArguments().containsKey("section"));
        assertFalse(invocation.getArguments().containsKey("subsection"));
    }

    @Test
    void sectionsAndCoreSubsectionsNormalizeToCanonicalNames() throws Exception {
        assertSelection("status core", "core", null);
        assertSelection("status core objects", "core", "objects");
        assertSelection("status core transaction", "core", "transaction");
        assertSelection("status core levels", "core", "levels");
        assertSelection("status storage", "storage", null);
        assertSelection("status session", "session", null);
        assertSelection("status runtime", "runtime", null);
    }

    @Test
    void selectorPrefixesUseCanonicalFamilyLocalResolution() throws Exception {
        assertSelection("stat c o", "core", "objects");
        assertSelection("stat c t", "core", "transaction");
        assertSelection("stat c l", "core", "levels");
        assertSelection("stat se", "session", null);
        assertSelection("stat r", "runtime", null);
    }

    @Test
    void invalidSelectorShapesAreRejectedExplicitly() {
        CommandParseException unknownSection = assertThrows(
                CommandParseException.class,
                () -> parser.parse("status objects"));
        assertEquals(CommandParseException.Reason.UNKNOWN_KEYWORD,
                unknownSection.getReason());

        CommandParseException invalidSubsection = assertThrows(
                CommandParseException.class,
                () -> parser.parse("status core storage"));
        assertEquals(CommandParseException.Reason.UNKNOWN_KEYWORD,
                invalidSubsection.getReason());

        CommandParseException extra = assertThrows(
                CommandParseException.class,
                () -> parser.parse("status runtime extra"));
        assertEquals(CommandParseException.Reason.EXTRA_ARGUMENT,
                extra.getReason());
    }

    @Test
    void statusIsRegisteredInCanonicalHelpMetadata() {
        CommandRegistry.Definition definition =
                CommandRegistry.definition(CommandIntent.STATUS);

        assertNotNull(definition);
        assertEquals("STATUS", definition.getHelpSection());
        assertEquals(
                "status [core [objects|transaction|levels]|storage|session|runtime]",
                definition.getSyntax());
    }

    private void assertSelection(String command,
                                 String section,
                                 String subsection) throws Exception {
        CommandInvocation invocation = parser.parse(command);
        assertEquals(CommandIntent.STATUS, invocation.getIntent());
        assertEquals(section, invocation.getArguments().get("section"));
        if (subsection == null) {
            assertFalse(invocation.getArguments().containsKey("subsection"));
        } else {
            assertEquals(subsection, invocation.getArguments().get("subsection"));
        }
    }
}
