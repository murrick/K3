/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger;

import org.junit.jupiter.api.Test;
import org.kanger.command.CommandFormatter;
import org.kanger.command.CommandIntent;
import org.kanger.command.CommandInvocation;
import org.kanger.command.CommandParser;
import org.kanger.interfaces.IUser;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalTimeZoneCommandTest {

    @Test
    void parsesAndFormatsBothTimeZoneForms() throws Exception {
        CommandParser parser = new CommandParser();
        CommandFormatter formatter = new CommandFormatter();

        CommandInvocation show = parser.parse("timezone");
        assertEquals(CommandIntent.TIMEZONE, show.getIntent());
        assertEquals("", show.getArgument("zoneId"));
        assertEquals("timezone", formatter.format(show));

        CommandInvocation set = parser.parse("timezone Asia/Tokyo");
        assertEquals(CommandIntent.TIMEZONE, set.getIntent());
        assertEquals("Asia/Tokyo", set.getArgument("zoneId"));
        assertEquals("timezone Asia/Tokyo", formatter.format(set));
    }

    @Test
    void showsSessionAndInformationalSystemTimeZone() throws Exception {
        Fixture fixture = fixture("show");
        try {
            fixture.user.setTimeZone("Europe/Brussels");
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();

            CanonicalCommandProcessor.Result result = processor.execute(
                    new CommandParser().parse("timezone"), fixture.user);

            assertTrue(result.isHandled());
            assertTrue(result.isSuccess());
            assertSame(fixture.root, result.getMind());
            assertEquals(
                    "session.timezone=Europe/Brussels"
                            + "\nsystem.timezone.default=" + ZoneId.systemDefault().getId(),
                    result.getDescription());
            assertEquals("Europe/Brussels", fixture.user.getTimeZone());
        } finally {
            fixture.close();
        }
    }

    @Test
    void changesOnlyCurrentUserTimeZoneImmediately() throws Exception {
        Fixture fixture = fixture("set");
        try {
            fixture.user.setTimeZone("Europe/Brussels");
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();

            CanonicalCommandProcessor.Result result = processor.execute(
                    new CommandParser().parse("timezone Asia/Tokyo"), fixture.user);

            assertTrue(result.isHandled());
            assertTrue(result.isSuccess());
            assertSame(fixture.root, result.getMind());
            assertSame(fixture.root, fixture.user.getCurrentMind());
            assertEquals("Asia/Tokyo", fixture.user.getTimeZone());
            assertEquals(
                    "session.timezone=Asia/Tokyo"
                            + "\nsystem.timezone.default=" + ZoneId.systemDefault().getId(),
                    result.getDescription());
        } finally {
            fixture.close();
        }
    }

    @Test
    void invalidZoneLeavesCurrentUserTimeZoneUnchanged() throws Exception {
        Fixture fixture = fixture("invalid");
        try {
            fixture.user.setTimeZone("Europe/Brussels");
            CanonicalCommandProcessor processor = new CanonicalCommandProcessor();

            assertThrows(DateTimeException.class,
                    () -> processor.execute(
                            new CommandParser().parse("timezone Not/A_Time_Zone"),
                            fixture.user));

            assertEquals("Europe/Brussels", fixture.user.getTimeZone());
            assertSame(fixture.root, fixture.user.getCurrentMind());
        } finally {
            fixture.close();
        }
    }

    private Fixture fixture(String purpose) throws Exception {
        String identity = "canonical-timezone-command-" + purpose + "-" + UUID.randomUUID();
        IUser user = UserFactory.createUser(identity, identity);
        new UDF().init(user);
        new DB().init(user);
        Mind root = new Mind(user);
        user.setCurrentMind(root);
        return new Fixture(user, root);
    }

    private static final class Fixture {
        private final IUser user;
        private final Mind root;

        private Fixture(IUser user, Mind root) {
            this.user = user;
            this.root = root;
        }

        private void close() throws Exception {
            UserFactory.dropUser(user);
        }
    }
}
