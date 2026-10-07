package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.internal.IContextFederation;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalContextUseTest {
    @TempDir Path directory;
    User user; DB data;
    Mind mind() { return (Mind)user.getCurrentMind(); }
    void initialize() throws Exception {
        user=new User(); user.setDatabaseDir(directory.toString()+File.separator);
        data=new DB(); data.init(user); user.setCurrentMind(new Mind(user));
    }
    void use(String name) throws Exception { user.setCurrentMind(mind().useStorage(name)); }
    void close() throws Exception { user.setCurrentMind(mind().closeStorage()); }

    @Test void exactRevisionIsReadOnlyAndNeverCreatesLiteralStorage() throws Exception {
        initialize(); use("X"); assertTrue(mind().query("!p(Mary);",null,false));
        long old=data.getRevision(); assertTrue(mind().query("!p(John);",null,false));
        long current=data.getRevision();
        use("X@"+old);
        assertTrue(data.isReadOnly()); assertEquals(old,data.getRevision());
        assertEquals("X",data.federationSnapshot().getSourceLocator());
        assertTrue(mind().query("?p(Mary);",null,false)); assertNull(mind().query("?p(John);",null,false));
        assertThrows(Exception.class,()->mind().query("!p(Tom);",null,false));
        assertThrows(Exception.class,()->mind().compile("!p(Tom);"));
        assertThrows(Exception.class,()->data.publishContext(mind(),"Forbidden"));
        assertThrows(Exception.class,()->data.disconnectContext("A"));
        assertThrows(Exception.class,()->data.remove(null));
        IContextFederation.RuleBlock block=data.inspectRules(mind(),"X@"+old,IContextFederation.RuleSelection.PRIMARY,null).get(0);
        assertFalse(block.working); assertEquals("X",block.locator);
        assertEquals(old,data.versionHistory(null).getPinnedRevision());
        assertEquals(current,data.versionHistory(null).getCurrentRevision());
        close(); use("X"); assertEquals(current,data.getRevision()); assertTrue(mind().query("?p(John);",null,false));
        assertFalse(Files.exists(directory.resolve("X@"+old))); close();
    }

    @Test void invalidRevisionAndOpenTransactionsPreserveTheActiveStorage() throws Exception {
        initialize(); use("X"); assertTrue(mind().query("!baseline;",null,false));
        Mind original=mind(); long revision=data.getRevision();
        assertThrows(Exception.class,()->use("X@999")); assertSame(original,mind());
        assertThrows(Exception.class,()->use("X@999999999999999999999999"));
        assertEquals("X",data.getStorageName()); assertEquals(revision,data.getRevision());
        new org.kanger.CanonicalCommandProcessor().execute(new org.kanger.command.CommandParser().parse("transaction start"),user); assertTrue(mind().query("!pending;",null,false));
        Mind top=mind(); assertThrows(Exception.class,()->use("X@"+revision));
        assertSame(top,mind()); assertEquals(1,mind().getTransactionLevel());
        assertTrue(mind().query("?pending;",null,false));
        new org.kanger.CanonicalCommandProcessor().execute(new org.kanger.command.CommandParser().parse("transaction rollback"),user); close();
    }

    @Test void historicalFederationUsesBothExactSourceAndPublishedTargetPins() throws Exception {
        initialize(); use("A"); assertTrue(mind().query("!@x p(x) -> q(x);",null,false)); close();
        use("X"); assertTrue(mind().query("!p(Mary);",null,false)); data.connectContext("A");
        user.setCurrentMind(data.publishContext(mind(),"With A")); long old=data.getRevision();
        data.disconnectContext("A"); assertTrue(mind().query("!p(John);",null,false));
        user.setCurrentMind(data.publishContext(mind(),"Without A")); long current=data.getRevision();
        use("X@"+old); assertEquals(1,data.federationSnapshot().getConnections().size());
        assertTrue(mind().query("?q(Mary);",null,false)); assertNull(mind().query("?q(John);",null,false));
        assertEquals(IContextFederation.FrontierTruth.TRUE,data.executeFederatedQuery("?q(Mary);").getResultTruth());
        assertEquals(IContextFederation.FrontierTruth.UNKNOWN,data.executeFederatedQuery("?q(John);").getResultTruth());
        assertEquals(old,data.getRevision()); close(); use("X"); assertEquals(current,data.getRevision());
        assertEquals(0,data.federationSnapshot().getConnections().size()); close();
    }
}
