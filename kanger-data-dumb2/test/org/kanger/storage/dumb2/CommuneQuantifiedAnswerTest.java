package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kanger.*;
import org.kanger.command.*;
import org.kanger.interfaces.internal.IContextFederation;
import static org.junit.jupiter.api.Assertions.*;

class CommuneQuantifiedAnswerTest {
    @TempDir Path directory;
    User user; DB data; Mind mind;
    void open(String name) throws Exception {
        user = new User(); user.setDatabaseDir(directory + File.separator);
        data = new DB(); data.init(user); mind = new Mind(user); user.setCurrentMind(mind);
        mind = (Mind) mind.useStorage(name); user.setCurrentMind(mind);
    }
    void close() throws Exception { user.setCurrentMind(mind.closeStorage()); }

    private void create(String name, String source) throws Exception {
        open(name);
        try { assertTrue(mind.compile(source)); } finally { close(); }
    }
    private void connect(String name) throws Exception {
        new CanonicalCommandProcessor().execute(new CommandParser().parse(
                "ctx connect " + name + " trust own"), user);
    }
    @Test void structuredAbstractSetStaysLocalAndDoesNotPoisonTheTransaction() throws Exception {
        create("facts", "!father(John,Tom); !mother(Mary,Tom);");
        open("family");
        try {
            connect("facts");
            new CanonicalCommandProcessor().execute(new CommandParser().parse("start"), user);
            mind = (Mind) user.getCurrentMind();
            assertEquals(Boolean.TRUE, mind.query("!@x @y @z father(x,z), mother(y,z) -> family([x,y,z]);"));
            // Unbound structured arguments must remain local, including during the false pass.
            assertDoesNotThrow(() -> mind.query("?$x $y $z family([x,y,z]);"));
            assertEquals(Boolean.TRUE, mind.query("?$x $y $z father(x,z), mother(y,z), family([x,y,z]);"));
            assertEquals(1, mind.getValues().size());
            Map<String,org.kanger.interfaces.ITerm> row = mind.getValues().iterator().next();
            assertEquals("John", row.get("x").toString());
            assertEquals("Mary", row.get("y").toString());
            assertEquals("Tom", row.get("z").toString());
        } finally { close(); }
    }
    @Test void communeEnumeratesUnorderedSetPatternBindings() throws Exception {
        create("natives", "!@x @y @z father(x,z), mother(y,z) -> family([x,y,z]);");
        create("facts", "!father(John,Tom); !mother(Mary,Tom); !father(John,Sarah); !mother(Mary,Sarah);");
        open("family");
        try {
            connect("natives"); connect("facts");
            assertEquals(Boolean.TRUE, mind.query("?$x $y $z family([x,y,z]);"));
            assertEquals(12, mind.getValues().size());
            for (Map<String,org.kanger.interfaces.ITerm> row : mind.getValues()) {
                Set<String> members = new HashSet<>();
                for (org.kanger.interfaces.ITerm value : row.values()) members.add(value.toString());
                assertTrue(members.equals(new HashSet<>(Arrays.asList("John", "Mary", "Tom")))
                        || members.equals(new HashSet<>(Arrays.asList("John", "Mary", "Sarah"))));
            }
            assertEquals(Boolean.TRUE, mind.query("?$x family([John,Mary,x]);"));
            assertEquals(new HashSet<>(Arrays.asList("Tom", "Sarah")),
                    mind.getValues().getValues("x").stream().map(Object::toString)
                            .collect(java.util.stream.Collectors.toSet()));
        } finally { close(); }
    }
    @Test void abstractTruthAndMixedQuantifierCounterexampleMatchCommuneOpinion() throws Exception {
        create("natives", "!@x $y parent(y,x); !@x ~parent(x,x); !@x (male(x) || female(x)) && ~(male(x) && female(x)); !@x @y daughter(x,y) -> female(x), child(x,y); !@x @y son(x,y) -> male(x), child(x,y); !@x @y father(x,y) -> male(x), parent(x,y); !@x @y mother(x,y) -> female(x), parent(x,y); !@x @y child(x,y) -> parent(y,x), (male(x) -> son(x,y)), (female(x) -> daughter(x,y)); !@x @y parent(x,y) -> child(y,x), (male(x) -> father(x,y)), (female(x) -> mother(x,y)); !@x @y ~(parent(x,y), parent(y,x)); !@x @y ($z parent(z,x) && parent(z,y)) && x != y -> sibling(x,y); !@x @y ~(sibling(x,y), parent(x,y)); !@x @y sibling(x,y) -> sibling(y,x); !@x @y ($z parent(x,z), parent(y,z)), x != y -> spouse(x,y) || divorced(x,y);");
        create("facts", "!father(John, Tom); !daughter(Sarah, John); !mother(Mary,Sarah); !child(Tom,Mary); !age(John, 37); !age(Tom, 12); !age(Sarah, 4);");
        int index = 0;
        for (String[] order : Arrays.asList(new String[]{"natives", "facts"},
                new String[]{"facts", "natives"})) {
            open("X" + index++);
            try {
                for (String name : order) connect(name);
                long revision = data.getRevision();
                assertEquals(Boolean.TRUE, mind.query("?$x parent(x,John);", null, false));
                assertTrue(mind.getValues().isEmpty());
                assertTrue(mind.getSolutions().isEmpty());
                assertEquals(IContextFederation.FrontierTruth.TRUE,
                        mind.collectContextOpinions(null).get("trust own").getResult().getResultTruth());
                assertEquals(Boolean.FALSE, mind.query("?$x @y parent(x,y);", null, false));
                assertTrue(mind.getValues().isEmpty());
                IContextFederation.QueryResult opinion = mind.collectContextOpinions(null)
                        .get("trust own").getResult();
                assertEquals(IContextFederation.FrontierTruth.FALSE, opinion.getResultTruth());
                assertTrue(opinion.getValues().isEmpty());
                assertEquals(Boolean.FALSE, mind.query("?@x male(x);", null, false));
                assertEquals(new HashSet<>(Arrays.asList("Mary", "Sarah")),
                        mind.getValues().getValues("x").stream().map(Object::toString)
                                .collect(java.util.stream.Collectors.toSet()));
                assertNull(mind.query("?$x missingRelation(x,John);", null, false));
                assertEquals(revision, data.getRevision());
            } finally { close(); }
        }
    }
}
