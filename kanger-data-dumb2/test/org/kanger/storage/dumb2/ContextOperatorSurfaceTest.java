package org.kanger.storage.dumb2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.*;
import org.kanger.command.*;
import org.kanger.interfaces.internal.IContextFederation;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContextOperatorSurfaceTest {
    @TempDir Path root;
    private final CommandParser parser=new CommandParser();
    private final CanonicalCommandProcessor processor=new CanonicalCommandProcessor();

    @Test void canonicalRulesModifiersRoundTripAndKeepContextAUnambiguous() throws Exception {
        for(String line:Arrays.asList("ctx rules","ctx ask A rules","ctx ask A rules all","ctx rules produced",
                "ctx ask A rules 12","ctx ask A rules tree 12","ctx ask A rules comment 12","ctx publish")) {
            CommandInvocation command=parser.parse(line);
            assertEquals(line,new CommandFormatter().format(command));
        }
        assertEquals("A",parser.parse("ctx ask A rules").getArgument("locator"));
        assertThrows(Exception.class,()->parser.parse("ctx rules level"));
        assertThrows(Exception.class,()->parser.parse("ctx ask A rules level 0"));
        assertThrows(Exception.class,()->parser.parse("ctx ask A rules comment 1 replacement"));
        assertThrows(Exception.class,()->parser.parse("ctx ask A rules tree"));
    }

    @Test void overlappingRuleIdsRequireAndRespectSourceLocator() throws Exception {
        context("facts","!age(Sarah,4);");
        context("natives","!@x @y daughter(x,y) -> female(x), child(x,y);");
        try(Fixture x=open("X")) {
            x.command("ctx connect facts");x.command("ctx connect natives");
            IContextFederation.RuleBlock facts=x.command("ctx ask facts rules 0").getContextRules().get(0);
            IContextFederation.RuleBlock natives=x.command("ctx ask natives rules 0").getContextRules().get(0);
            assertEquals("facts",facts.locator);assertEquals("natives",natives.locator);
            assertTrue(facts.rules.get(0).statement.contains("age("));
            assertTrue(natives.rules.get(0).statement.contains("daughter("));
            assertFalse(x.command("ctx ask natives rules struct 0").getContextRules().get(0).rules.get(0).tree.isEmpty());
            for(String line:Arrays.asList("ctx rules 0","ctx rules tree 0","ctx rules structure 0","ctx rules comment 0"))
                assertThrows(CommandParseException.class,()->parser.parse(line));
            assertThrows(Exception.class,()->x.data.inspectRules(x.user.getCurrentMind(),null,IContextFederation.RuleSelection.SHOW,0L));
        }
    }

    @Test void reservedContextNamesAndInferenceStructureAreSeparate() throws Exception {
        context("all","!@x p(x) -> q(x);","!p(John);");
        context("tree","!marker(7);");
        try(Fixture x=open("X")) {
            x.command("ctx connect all");x.command("ctx connect tree");
            assertEquals(3,x.command("ctx rules all").getContextRules().size());
            IContextFederation.RuleBlock base=x.command("ctx ask all rules base").getContextRules().get(0);
            assertEquals("all",base.locator);
            assertTrue(base.rules.stream().noneMatch(r->r.statement.contains("@x")));
            assertEquals("tree",x.command("ctx ask tree rules").getContextRules().get(0).locator);
            long produced=x.command("ctx ask all rules produced").getContextRules().get(0).rules.get(0).id;
            IContextFederation.RuleRow inference=x.command("ctx ask all rules tree "+produced).getContextRules().get(0).rules.get(0);
            IContextFederation.RuleRow structure=x.command("ctx ask all rules structure "+produced).getContextRules().get(0).rules.get(0);
            assertTrue(inference.tree.toString().contains("Cause:"));
            assertFalse(structure.tree.toString().contains("Cause:"));
            assertFalse(structure.tree.isEmpty());
        }
    }

    @Test void rulesUseOldExactPinWithGeneratedTreeCommentAndLiveXOverlay() throws Exception {
        context("A","!@x p(x) -> q(x);","!p(John);");
        try(Fixture x=open("X")) {
            x.command("ctx connect A");
            long pin=x.data.federationSnapshot().getConnections().get(0).getPinnedRevision();
            context("A","!p(Mary);");
            List<IContextFederation.RuleBlock> primary=x.command("ctx ask A rules").getContextRules();
            assertEquals(1,primary.size()); assertEquals(pin,primary.get(0).revision.getRevision());
            assertTrue(primary.get(0).rules.stream().noneMatch(r->r.generated||r.statement.contains("Mary")));
            assertFalse(x.command("ctx ask A rules produced").getContextRules().get(0).rules.isEmpty());
            long id=primary.get(0).rules.get(0).id;
            assertFalse(x.command("ctx ask A rules tree "+id).getContextRules().get(0).rules.get(0).tree.isEmpty());
            assertNotNull(x.command("ctx ask A rules comment "+id).getContextRules().get(0).rules.get(0).comment);
            long revision=x.data.getRevision();
            x.command("transaction start");
            Mind tx=(Mind)x.user.getCurrentMind();
            assertTrue(tx.query("!local;"));
            assertEquals(2,x.command("ctx rules").getContextRules().size());
            assertTrue(x.command("ctx ask X rules").getContextRules().get(0).rules.stream().anyMatch(r->r.statement.contains("local")));
            assertThrows(Exception.class,()->x.command("ctx ask Unknown rules"));
            assertFalse(x.command("ctx publish").isSuccess());
            x.command("transaction rollback");
            assertEquals(revision,x.data.getRevision());
            assertEquals(1,x.data.federationSnapshot().getConnections().size());
            assertThrows(UnsupportedOperationException.class,()->primary.get(0).rules.clear());
        }
    }

    @Test void recommendationsReadPinnedMetadataWithoutOpeningMissingNOrAutoConnectingIt() throws Exception {
        context("N","!p(John);"); context("A","!@x p(x) -> q(x);");
        try(Fixture a=open("A")) { a.command("ctx connect N"); a.command("ctx publish"); }
        Files.move(ContextStore.contextPath(root.resolve("N")),root.resolve("unavailable-N.context"));
        try(Fixture x=open("X")) {
            x.command("ctx connect A");
            IContextFederation.Snapshot snapshot=x.data.federationSnapshot();
            assertEquals(1,snapshot.getConnections().size());
            assertEquals(1,snapshot.getDependencyNotices().size());
            assertEquals("NOT_CONNECTED",snapshot.getDependencyNotices().get(0).getStatus());
            assertFalse(x.command("ctx ask A rules").getContextRules().get(0).rules.isEmpty());
            assertNull(((Mind)x.user.getCurrentMind()).query("?q(John);",null,false));
            x.command("ctx publish");
        }
        try(Fixture x=open("X")) { assertEquals(1,x.data.federationSnapshot().getConnections().size()); }
    }

    @Test void saveEmptyXCreatesOneRevisionAndRestoresOnlyExplicitPinsAfterReopen() throws Exception {
        context("A","!fact;");
        long saved;
        try(Fixture x=open("X")) {
            assertEquals(0,x.data.getRevision());
            x.command("ctx connect A");
            assertTrue(x.data.federationSnapshot().hasWorkingChanges());
            x.command("ctx publish"); saved=x.data.getRevision();
            assertEquals(1,saved); assertFalse(x.data.federationSnapshot().hasWorkingChanges());
            x.command("ctx publish"); saved=x.data.getRevision(); assertEquals(2,saved);
            x.command("ctx disconnect A"); assertTrue(x.data.federationSnapshot().hasWorkingChanges());
        }
        try(Fixture x=open("X")) {
            assertEquals(saved,x.data.getRevision());
            assertEquals(1,x.data.federationSnapshot().getConnections().size());
            x.command("ctx disconnect A"); x.command("ctx publish");
        }
        try(Fixture x=open("X")) { assertTrue(x.data.federationSnapshot().getConnections().isEmpty()); }
        try(ContextSnapshot old=ContextSnapshot.open(root.resolve("X"),saved)) {
            assertEquals(1,ConnectionStore.read(root.resolve("X"),new RevisionRef(old.getContextId(),saved)).size());
        }
    }

    @Test void differentRecommendedPinIsAdvisoryAndDisconnectDoesNotCascade() throws Exception {
        context("N","!p(John);"); context("A","!@x p(x) -> q(x);");
        try(Fixture a=open("A")) { a.command("ctx connect N"); a.command("ctx publish"); }
        context("N","!p(Mary);");
        try(Fixture x=open("X")) {
            x.command("ctx connect N"); x.command("ctx connect A");
            assertEquals("DIFFERENT_REVISION",x.data.federationSnapshot().getDependencyNotices().get(0).getStatus());
            assertEquals(2,x.data.federationSnapshot().getConnections().size());
            x.command("ctx disconnect A");
            assertEquals("N",x.data.federationSnapshot().getConnections().get(0).getLocator());
        }
    }

    @Test void nestedRollbackRestoresEachWorkingTopologyWithoutPublishing() throws Exception {
        context("A", "!alpha;"); context("B", "!beta;");
        try (Fixture x = open("X")) {
            x.command("ctx connect A");
            x.command("ctx publish");
            long revision = x.data.getRevision();
            x.command("transaction start");
            x.command("ctx disconnect A");
            x.command("ctx connect B");
            x.command("transaction start");
            x.command("ctx connect A");
            x.command("transaction rollback");
            assertEquals(1, x.data.federationSnapshot().getConnections().size());
            assertEquals("B", x.data.federationSnapshot().getConnections().get(0).getLocator());
            x.command("transaction rollback");
            assertEquals("A", x.data.federationSnapshot().getConnections().get(0).getLocator());
            assertFalse(x.data.federationSnapshot().hasWorkingChanges());
            assertEquals(revision, x.data.getRevision());
        }
    }

    @Test void ordinaryAuthoringKeepsUnsavedExactPinsWithoutSavingTopology() throws Exception {
        context("A", "!alpha;");
        try (Fixture x = open("X")) {
            x.command("ctx connect A");
            long pin = x.data.federationSnapshot().getConnections().get(0).getPinnedRevision();
            context("A", "!newAlpha;");
            assertTrue(((Mind) x.user.getCurrentMind()).query("!local;", null, false));
            assertEquals(pin, x.data.federationSnapshot().getConnections().get(0).getPinnedRevision());
            assertTrue(x.data.federationSnapshot().hasWorkingChanges());
            assertTrue(x.data.federationSnapshot().getPublishedConnections().isEmpty());
        }
        try (Fixture x = open("X")) {
            assertTrue(x.data.federationSnapshot().getConnections().isEmpty());
        }
    }

    @Test void squashKeepsWorkingConnectionsButRollbackRestoresOriginalTopology() throws Exception {
        context("A", "!alpha;"); context("B", "!beta;");
        try (Fixture x = open("X")) {
            x.command("ctx connect A"); x.command("ctx publish");
            x.command("transaction start");
            x.command("ctx disconnect A"); x.command("ctx connect B");
            x.command("transaction start"); x.command("ctx connect A");
            x.command("transaction squash");
            assertEquals(2, x.data.federationSnapshot().getConnections().size());
            x.command("transaction rollback");
            assertEquals(1, x.data.federationSnapshot().getConnections().size());
            assertEquals("A", x.data.federationSnapshot().getConnections().get(0).getLocator());
        }
    }

    private void context(String name,String...statements) throws Exception {
        try(Fixture f=open(name)) { for(String s:statements) assertTrue(((Mind)f.user.getCurrentMind()).query(s,null,false)); }
    }
    private Fixture open(String name) throws Exception {
        User user=new User(); user.setDatabaseDir(root.toString()+File.separator);
        DB data=new DB(); data.init(user); Mind mind=new Mind(user); user.setCurrentMind(mind);
        user.setCurrentMind(mind.useStorage(name)); return new Fixture(user,data);
    }
    private final class Fixture implements AutoCloseable {
        final User user; final DB data;
        Fixture(User user,DB data) { this.user=user; this.data=data; }
        CanonicalCommandProcessor.Result command(String line) throws Exception { return processor.execute(parser.parse(line),user); }
        public void close() throws Exception { user.setCurrentMind(user.getCurrentMind().closeStorage()); }
    }
}
