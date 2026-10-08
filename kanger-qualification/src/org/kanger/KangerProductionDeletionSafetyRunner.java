package org.kanger;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.kanger.interfaces.*;
import org.kanger.storage.DB;
import org.kanger.udf.UDF;

/** Regression for orphaned productions and inherited provenance mutation. */
public final class KangerProductionDeletionSafetyRunner {
    private static Mind create() throws Exception {
        String name = "production-delete-" + System.nanoTime();
        IUser user = UserFactory.createUser(name, name);
        new UDF().init(user);
        new DB().init(user);
        return (Mind) new Mind(user).clearWorkspace();
    }
    private static Map<Long, Set<String>> provenance(Mind mind) throws Exception {
        Map<Long, Set<String>> result = new TreeMap<>();
        for (IRule rule : mind.getRules()) if (!rule.isDeleted(mind)) {
            Set<String> causes = new TreeSet<>();
            for (ICause cause : rule.getCauses()) {
                IRule donor = cause.getDonor(mind);
                causes.add(cause.getRule(mind).getId() + ":" + (donor == null ? "missing" : donor.getId()));
            }
            result.put(rule.getId(), causes);
        }
        return result;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void minimal() throws Exception {
        Mind mind = create();
        mind.query("!@x ~parent(x,x);", null, false);
        mind.query("!@x @y father(x,y) -> parent(x,y);", null, false);
        mind.query("!father(John,Tom);", null, false);
        Map<Long, Set<String>> before = provenance(mind);
        try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(mind)) {
            tx.mind().query("-father(John,Tom);", null, false);
            require(provenance(tx.mind()).size() == 2, "orphan productions inside transaction");
            require(before.equals(provenance(mind)), "child changed parent provenance");
            tx.rollback();
        }
        require(before.equals(provenance(mind)), "rollback changed provenance");
        mind.query("-father(John,Tom);", null, false);
        require(provenance(mind).size() == 2, "orphan productions after commit");
        for (Object term : mind.getTerms()) {
            require(!"John".equals(term.toString()) && !"Tom".equals(term.toString()), "orphan name retained");
        }
    }
    private static void family() throws Exception {
        Mind mind = create();
        String source = new String(Files.readAllBytes(Paths.get("natives.k")), "UTF-8")
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        for (String statement : source.split(";")) if (!statement.trim().isEmpty())
            require(Boolean.TRUE.equals(mind.query(statement.trim() + ";", null, false)), "family load failed");
        Map<Long, Set<String>> before = provenance(mind);
        long last = Collections.max(before.keySet());
        String delete = "-$x rule(x), x:14.." + last + ";";
        try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(mind)) {
            tx.mind().query(delete, null, false);
            require(provenance(tx.mind()).size() == 14, "family productions survived deletion: " + provenance(tx.mind()));
            tx.rollback();
        }
        require(before.equals(provenance(mind)), "family rollback changed provenance");
        mind.query(delete, null, false);
        require(provenance(mind).size() == 14, "family orphan productions after commit");
        mind.query("!father(John,Tom);", null, false);
        require(Boolean.TRUE.equals(mind.query("?parent(John,Tom);", null, false)), "reinsertion lost inference");
    }
    private static void independentSupport() throws Exception {
        Mind mind = create();
        mind.query("!@x source(x) -> derived(x);", null, false);
        mind.query("!@x backup(x) -> derived(x);", null, false);
        mind.query("!source(John);", null, false);
        mind.query("!backup(John);", null, false);
        mind.query("-source(John);", null, false);
        require(Boolean.TRUE.equals(mind.query("?derived(John);", null, false)),
                "independent support was removed");
        mind.query("-backup(John);", null, false);
        require(mind.query("?derived(John);", null, false) == null,
                "last support left a stale answer");
    }
    private static void persistentRollback() throws Exception {
        Mind mind = (Mind) create().useStorage("production-deletion");
        mind.query("!@x ~parent(x,x);", null, false);
        mind.query("!@x @y father(x,y) -> parent(x,y);", null, false);
        mind.query("!father(John,Tom);", null, false);
        Map<Long, Set<String>> before = provenance(mind);
        try (TechnicalMindTransaction tx = TechnicalMindTransaction.begin(mind)) {
            tx.mind().query("-father(John,Tom);", null, false);
            tx.rollback();
        }
        require(before.equals(provenance(mind)), "persistent rollback changed provenance");
        mind = (Mind) mind.closeStorage();
        mind = (Mind) mind.useStorage("production-deletion");
        require(before.equals(provenance(mind)), "reopen changed restored provenance");
        mind.query("-father(John,Tom);", null, false);
        mind = (Mind) mind.closeStorage();
        mind = (Mind) mind.useStorage("production-deletion");
        require(provenance(mind).size() == 2, "persistent orphan productions survived reopen");
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("production-deletion-").toString());
        minimal();
        family();
        independentSupport();
        persistentRollback();
        System.out.println("PRODUCTION_DELETION_OK");
        System.exit(0);
    }
}
