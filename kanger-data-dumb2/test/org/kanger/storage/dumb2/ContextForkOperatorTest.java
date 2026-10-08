package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.*;
import org.kanger.command.*;
import org.kanger.exception.CommandErrorException;
import org.kanger.interfaces.IContextResults;
import java.nio.file.Path;
import java.io.File;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ContextForkOperatorTest {
    @TempDir Path directory;
    User user; DB data; Mind mind;
    void open(String name) throws Exception {
        user = new User(); user.setDatabaseDir(directory + File.separator);
        data = new DB(); data.init(user); mind = new Mind(user); user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name); user.setCurrentMind(mind);
    }
    void close() throws Exception { user.setCurrentMind(mind.closeStorage()); }
    CanonicalCommandProcessor.Result command(String source) throws Exception {
        return new CanonicalCommandProcessor().execute(new CommandParser().parse(source), user);
    }
    @Test void historicalForkHasIndependentIdentityAndLeavesSourceSelected() throws Exception {
        open("X"); UUID source; long selected;
        try {
            mind.query("!p(Mary);", null, false); source = data.getContextId(); selected = data.getRevision();
            mind.query("!p(John);", null, false);
        } finally { close(); }
        open("X@" + selected);
        try {
            assertEquals("Context forked: Y@1", command("ctx fork Y").getDescription());
            assertSame(mind, user.getCurrentMind()); assertEquals(source, data.getContextId());
            assertEquals(selected, data.getRevision());
            assertThrows(CommandErrorException.class, () -> mind.forkContext("Y"));
        } finally { close(); }
        open("Y");
        try {
            assertNotEquals(source, data.getContextId()); assertEquals(1, data.getRevision());
            assertTrue(mind.query("?p(Mary);", null, false)); assertNull(mind.query("?p(John);", null, false));
            mind.query("!p(Tom);", null, false);
            try (ContextSnapshot snapshot = ContextSnapshot.open(directory.resolve("Y"), data.getRevision())) {
                assertEquals(source, snapshot.getOrigin().getContextId());
                assertEquals(selected, snapshot.getOrigin().getRevision());
            }
        } finally { close(); }
        open("X");
        try { assertNull(mind.query("?p(Tom);", null, false)); assertTrue(mind.query("?p(John);", null, false)); }
        finally { close(); }
    }
    @Test void openTransactionsAndUnsavedConnectionsRejectWithoutCreatingTarget() throws Exception {
        open("N"); try { mind.query("!p(John);", null, false); } finally { close(); }
        open("X");
        try {
            mind.query("!anchor(X);", null, false); data.connectContext("N");
            assertThrows(CommandErrorException.class, () -> mind.forkContext("Y")); assertFalse(data.exists("Y"));
            mind = (Mind) data.publishContext(mind, "Pins"); user.setCurrentMind(mind);
            command("transaction start"); Mind child = (Mind) user.getCurrentMind();
            assertThrows(CommandErrorException.class, () -> child.forkContext("Y")); assertFalse(data.exists("Y"));
            command("transaction rollback"); mind = (Mind) user.getCurrentMind();
            IContextResults.Revision fork = mind.forkContext("Y"); assertEquals(1, fork.getRevision());
            for (String invalid : new String[] { "../Z", "Z@7", "", "/Z" })
                assertThrows(CommandErrorException.class, () -> mind.forkContext(invalid));
        } finally { close(); }
    }
    @Test void forkRetainsSavedPrivateConnectionViewAndOriginalTargetIsUnchanged() throws Exception {
        open("N"); try { mind.query("!p(John);", null, false); } finally { close(); }
        open("X");
        try {
            mind.query("!anchor(X);", null, false); data.connectContext("N");
            command("ctx ask N -p(John);"); command("ctx ask N !p(Mary);");
            mind = (Mind) data.publishContext(mind, "Private N"); user.setCurrentMind(mind);
            mind.forkContext("Y");
        } finally { close(); }
        open("Y");
        try {
            assertEquals(1, data.federationSnapshot().getConnections().size());
            assertTrue(mind.query("?p(Mary);", null, false)); assertNull(mind.query("?p(John);", null, false));
        } finally { close(); }
        open("N");
        try { assertTrue(mind.query("?p(John);", null, false)); assertNull(mind.query("?p(Mary);", null, false)); }
        finally { close(); }
    }
    @Test void emptyForkAndQuotedNameRoundTrip() throws Exception {
        open("X");
        try {
            CommandParser parser = new CommandParser();
            CommandInvocation invocation = parser.parse("ctx fork \"new context\"");
            assertEquals(invocation.getArguments(), parser.parse(new CommandFormatter().format(invocation)).getArguments());
            assertEquals(0, mind.forkContext("new context").getRevision());
            assertEquals(0, data.getRevision());
        } finally { close(); }
    }
}
