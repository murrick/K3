/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.*;
import org.kanger.command.*;
import java.nio.file.Path;
import java.io.File;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContextPublicationTest {
    @TempDir Path directory;
    User user; DB data;
    final CommandParser parser=new CommandParser();
    final CanonicalCommandProcessor processor=new CanonicalCommandProcessor();
    Mind mind() { return (Mind) user.getCurrentMind(); }
    void open() throws Exception {
        user=new User(); user.setDatabaseDir(directory.toString()+File.separator);
        data=new DB(); data.init(user); Mind root=new Mind(user); user.setCurrentMind(root);
        user.setCurrentMind(root.useStorage("X"));
    }
    void close() throws Exception { user.setCurrentMind(mind().closeStorage()); }
    void command(String line) throws Exception { assertTrue(processor.execute(parser.parse(line),user).isSuccess()); }
    void publish(String description) throws Exception {
        CommandInvocation invocation=parser.parse("ctx publish \""+description+"\"");
        Map<String,Object> args=new LinkedHashMap<>(invocation.getArguments()); args.put("confirmed",true);
        assertTrue(processor.execute(CommandInvocation.command(invocation.getIntent(),args,invocation.getRaw()),user).isSuccess());
    }
    String description() throws Exception { return data.versionHistory(null).getRevisions().get(0).getDescription(); }

    @Test void automaticDescriptionsAndFreshQueriesDoNotPublish() throws Exception {
        open();
        assertTrue(mind().query("!p(John);",null,false));
        assertEquals("Accepted: !p(John);",description());
        long revision=data.getRevision();
        assertNull(mind().query("?p(Unknown);",null,false));
        assertEquals(revision,data.getRevision());
        assertTrue(mind().query("?p(John);",null,false));
        assertEquals(revision,data.getRevision());
        close();
    }
    @Test void nestedCommitCarriesLatestDescriptionAndRollbackDropsOnlyChild() throws Exception {
        open(); command("transaction start"); command("transaction start");
        assertTrue(mind().query("!nested;",null,false));
        command("commit \"From U2\"");
        assertEquals(0,data.getRevision());
        assertEquals("From U2",mind().getProposedRevisionDescription());
        command("transaction start"); mind().proposeRevisionDescription("Discard me");
        command("transaction rollback"); assertEquals("From U2",mind().getProposedRevisionDescription());
        command("commit"); assertEquals(1,data.getRevision()); assertEquals("From U2",description());
        close();
    }
    @Test void publicationRequiresConsentAndPublishesFullNestedStackOnce() throws Exception {
        open(); assertTrue(mind().query("!baseline;",null,false)); long original=data.getRevision();
        command("transaction start"); assertTrue(mind().query("!first;",null,false));
        command("transaction start"); assertTrue(mind().query("!second;",null,false));
        Mind before=mind();
        assertFalse(processor.execute(parser.parse("ctx publish \"Release\""),user).isSuccess());
        assertSame(before,mind()); assertEquals(2,mind().getTransactionLevel()); assertEquals(original,data.getRevision());
        publish("Release"); assertEquals(0,mind().getTransactionLevel());
        assertEquals(original+1,data.getRevision()); assertEquals("Release",description());
        assertTrue(mind().query("?first;",null,false)); assertTrue(mind().query("?second;",null,false));
        close(); open(); assertTrue(mind().query("?second;",null,false)); close();
    }
    @Test void emptyAndUnchangedExplicitPublicationsCreateNamedVariants() throws Exception {
        open(); publish("Empty"); assertEquals(1,data.getRevision());
        publish("Again"); assertEquals(2,data.getRevision()); assertEquals("Again",description()); close();
    }
    @Test void rejectedQualificationRetainsLiveLevelsAndPins() throws Exception {
        User targetUser=new User(); targetUser.setDatabaseDir(directory.toString()+File.separator);
        DB targetData=new DB(); targetData.init(targetUser);
        Mind target=new Mind(targetUser); targetUser.setCurrentMind(target);
        target=(Mind)target.useStorage("A"); targetUser.setCurrentMind(target);
        assertTrue(target.query("!fact;",null,false)); targetUser.setCurrentMind(target.closeStorage());
        open(); command("ctx connect A");
        command("transaction start"); assertTrue(mind().query("!~fact;",null,false));
        command("transaction start"); assertTrue(mind().query("!local;",null,false));
        Mind before=mind(); long revision=data.getRevision();
        assertThrows(Exception.class,()->publish("Rejected"));
        assertSame(before,mind()); assertEquals(2,mind().getTransactionLevel());
        assertEquals(revision,data.getRevision()); assertEquals(1,data.federationSnapshot().getConnections().size());
        command("ctx disconnect A"); publish("Restricted domain");
        assertEquals(revision+1,data.getRevision()); assertTrue(mind().query("?local;",null,false)); close();
    }
    @Test void nativeDeletionAndRestoreSurviveNestedPublication() throws Exception {
        open(); assertTrue(mind().query("!p(John);",null,false));
        command("transaction start"); assertTrue(mind().query("-p(John);",null,false));
        command("transaction start"); assertTrue(mind().query("!p(Mary);",null,false));
        publish("Deleted John"); assertNull(mind().query("?p(John);",null,false));
        assertTrue(mind().query("?p(Mary);",null,false));
        assertTrue(mind().query("+p(John);",null,false));
        assertEquals("Deleted John",description());
        assertTrue(mind().query("-p(Mary);",null,false));
        assertEquals("Deleted: -p(Mary);",description()); close();
    }
    @Test void squashKeepsLatestProposalAndRollbackBoundary() throws Exception {
        open(); command("transaction start"); mind().proposeRevisionDescription("Earlier");
        command("transaction start"); assertTrue(mind().query("!local;",null,false));
        mind().proposeRevisionDescription("Latest"); command("transaction squash");
        assertEquals("Latest",mind().getProposedRevisionDescription()); assertEquals(1,mind().getTransactionLevel());
        command("transaction rollback"); assertNull(mind().getProposedRevisionDescription());
        assertEquals(0,data.getRevision()); close();
    }
    @Test void invalidDescriptionPreservesOpenStack() throws Exception {
        open(); command("transaction start"); Mind before=mind();
        assertThrows(Exception.class,()->data.publishContext(before,"Bad\nline"));
        assertSame(before,mind()); assertEquals(0,data.getRevision());
        command("transaction rollback"); close();
    }
}
