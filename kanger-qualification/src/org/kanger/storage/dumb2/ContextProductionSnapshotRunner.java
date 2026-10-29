/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.kanger.*;

/** Real DUMB2 identities, pinned HEAD independence and working effective-view freshness. */
public final class ContextProductionSnapshotRunner {
    private static int checks;
    private interface Attempt { void run() throws Exception; }
    private static void require(boolean value,String why) { ++checks; if (!value) throw new AssertionError(why); }
    private static void reject(Attempt attempt,String why) throws Exception {
        boolean rejected = false; try { attempt.run(); } catch (Exception expected) { rejected = true; } require(rejected,why);
    }
    private static RevisionRef create(Path dir,String name,String text) throws Exception {
        User user = new User(); user.setDatabaseDir(dir.toString()+File.separator); DB db = new DB(); db.init(user);
        Mind root = new Mind(user); user.setCurrentMind(root); Mind mind = (Mind)root.useStorage(name); user.setCurrentMind(mind);
        try { if (!text.isEmpty()) require(mind.compile(text,null,false),"fixture "+name); }
        finally { user.setCurrentMind(mind.closeStorage()); }
        try (ContextSnapshot snapshot = ContextSnapshot.open(dir.resolve(name))) { return new RevisionRef(snapshot.getContextId(),snapshot.getRevision()); }
    }
    public static void main(String[] args) {
        try {
            Path dir = Files.createTempDirectory("production-snapshots-"); System.setProperty("user.home",dir.toString());
            RevisionRef target = create(dir,"A","!@w p(w,1)->q(w,1);"); create(dir,"T","!marker(7);");
            User user = new User(); user.setDatabaseDir(dir.toString()+File.separator); DB db = new DB(); db.init(user);
            Mind root = new Mind(user); user.setCurrentMind(root); Mind x = (Mind)root.useStorage("X"); user.setCurrentMind(x);
            try {
                require(x.compile("!$w p(w,1)&&flag(w,1); !@w p(w,1)->warm(w,1);",null,false),"X existential primary and production fixture");
                db.connectContext("A"); long revision = db.getRevision(); int count = x.getRules().size();
                ContextProductionSnapshotSession.Inventory initial;
                try (ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(db,x,1000)) {
                    initial = lease.inventory();
                    require(initial.x.context.equals(db.getContextId()) && initial.x.revision == revision && initial.x.localX,"X identity comes from owning DB");
                    require(initial.foreign.size() == 1 && initial.foreign.get(0).context.equals(target.getContextId()) && initial.foreign.get(0).revision == target.getRevision(),"every direct foreign source uses exact actual pin");
                    require(!initial.foreign.get(0).localX && initial.foreign.get(0).initialization.isEmpty(),"raw pin is separate from X and unconfigured");
                    require(initial.x.primary.size() == 2 && initial.x.generated.size() >= 3,"primary whole existential/rule distinguished from materialized generated cache");
                    require(initial.x.primary.stream().anyMatch(row -> row.origin.contains("$w") && row.origin.contains("flag")),"entire primary witness conjunction stays one inventory row");
                    require(initial.x.generated.stream().allMatch(row -> row.generated),"generated records remain explicit rather than promoted to primary evidence");
                    require(initial.inspectedRows == initial.x.rows.size()+initial.foreign.get(0).rows.size(),"budget counts the entire family inventory, including generated rows");
                    require(initial.x.fingerprint.equals(lease.inventory().x.fingerprint),"repeated guarded read is stable");
                    ContextProductionSnapshotSession.Binding binding = lease.bind(4000000,10000);
                    require(binding.x.primary.size() == initial.x.primary.size() && binding.foreign.size() == 1,"provider binds every primary source row before releasing any family artifact");
                    require(binding.x.primary.stream().anyMatch(e -> e.kind == ContextProductionNativeBinding.Kind.WITNESS_BUNDLE && e.witnesses() == 1),"real stored existential bundle has native-checked whole replay binding");
                    require(binding.foreign.get(0).primary.stream().allMatch(e -> e.source.context.equals(target.getContextId()) && e.source.revision == target.getRevision() && !e.source.localX),"bound foreign rule sources retain exact provider pin and role");
                    require(binding.x.excludedGenerated.size() == initial.x.generated.size(),"real generated witness caches stay outside primary replay evidence");
                    require(lease.qualify(12,16,4000000,100000).status == ContextProductionFamilyAssembly.Status.FIXED_POINT,"real provider snapshots assemble and qualify without caller-written declaration lists");
                    ContextProductionCandidate.Evaluation safeCandidate = lease.qualifyCandidate("!@w q(w,1)->trial(w,1);",12,16,4000000,100000);
                    require(safeCandidate.status == ContextProductionFamilyAssembly.Status.FIXED_POINT && safeCandidate.artifact != null,"provider qualifies an isolated candidate without prepublishing it");
                    require(safeCandidate.artifact.base.inventory.fingerprint.equals(initial.x.fingerprint),"candidate receipt binds exact owning provider inventory");
                    require(lease.qualifyCandidate("!@w q(w,1)->~flag(w,1);",12,16,4000000,100000).status == ContextProductionFamilyAssembly.Status.COLLISION,"provider proves candidate's delayed foreign collision before publication");
                    require(db.getRevision() == revision && x.getRules().size() == count,"safe and rejected candidate trials preserve physical source");
                    require(db.getRevision() == revision && x.getRules().size() == count,"acquisition does not publish or modify X knowledge");
                    AtomicBoolean refused = new AtomicBoolean(); Thread other = new Thread(() -> { try { lease.requireCurrent(); } catch (Exception expected) { refused.set(true); } });
                    other.start(); other.join(); require(refused.get(),"lease authority cannot move to another thread");
                    RevisionRef newer = create(dir,"A","!later(8);"); require(newer.getRevision() > target.getRevision(),"foreign HEAD advances independently");
                    lease.requireCurrent(); require(lease.inventory().foreign.get(0).revision == target.getRevision(),"new HEAD does not invalidate or replace immutable old pin");
                    require(lease.inventory().foreign.get(0).primary.stream().noneMatch(row -> row.origin.contains("later")),"new foreign declaration does not leak into old pinned view");
                }
                ContextProductionSnapshotSession old = ContextProductionSnapshotSession.open(db,x,1000);
                db.applyConnectionCommand(x,"A","!private(4);");
                require(db.getRevision() == revision,"private initialization changes effective view without publishing X revision");
                reject(old::requireCurrent,"old lease rejects changed topology/initialization at the same base pin and X revision");
                reject(() -> old.qualifyCandidate("!newcandidate(1);",12,16,4000000,100000),"changed private effective view revokes candidate authority"); old.close();
                ContextProductionInventory configured;
                try (ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(db,x,1000)) {
                    configured = lease.inventory().foreign.get(0);
                    require(configured.revision == target.getRevision() && configured.context.equals(target.getContextId()),"initialized layer retains the exact old physical pin");
                    require(configured.primary.stream().anyMatch(row -> row.origin.contains("private(4)")) && configured.initialization.size() == 1,"full effective primary inventory includes private initialization");
                    require(!configured.fingerprint.equals(initial.foreign.get(0).fingerprint),"revision identity alone cannot identify an effective view");
                    ContextProductionSnapshotSession.Binding privateBinding=lease.bind(4000000,10000);
                    require(privateBinding.foreign.get(0).primary.stream().anyMatch(e -> e.statement.contains("private(4)") && e.kind == ContextProductionNativeBinding.Kind.CLAUSES),"initialized scalar fact is bound from its actual native layer rather than silently omitted");
                    org.kanger.ContextProductionCapture.Source privateSource=privateBinding.foreign.get(0).primary.stream().filter(e->e.statement.contains("private(4)")).findFirst().get().source;
                    require(ContextProductionSnapshotPrivateScalarCheck.keptLocal(lease.qualify(12,16,4000000,100000),privateSource),"supported whole private scalar reaches full closure without raw common export");
                }
                db.applyConnectionCommand(x,"A","!private(4);");
                try (ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(db,x,1000)) {
                    ContextProductionInventory intent = lease.inventory().foreign.get(0);
                    require(intent.initialization.size() == 2 && !intent.fingerprint.equals(configured.fingerprint),"even no-op initialization intent belongs to effective-view identity");
                }
                try (SnapshotMindRuntime pin = SnapshotMindRuntime.open(dir.resolve("A"),target,"check-unmodified-pin")) {
                    ContextProductionInventory physical = ContextProductionInventory.inspect(pin.getMind(),target.getContextId(),target.getRevision(),false,Collections.emptyList(),1000);
                    require(physical.primary.stream().noneMatch(row -> row.origin.contains("private") || row.origin.contains("later")),"neither private initialization nor new HEAD mutates the original pinned revision");
                }
                reject(() -> ContextProductionSnapshotSession.open(db,x,1),"total row bound refuses without a prefix inventory");
                ContextProductionSnapshotSession closed = ContextProductionSnapshotSession.open(db,x,1000); closed.close(); reject(closed::inventory,"closed lease carries no live authority");
                reject(() -> closed.bind(4000000,10000),"historical inventory cannot bypass closed provider authority for binding");
                reject(() -> closed.qualify(12,16,4000000,100000),"historical artifact cannot bypass closed provider authority for qualification");
                reject(() -> closed.qualifyCandidate("!newcandidate(1);",12,16,4000000,100000),"closed provider authority cannot qualify a candidate");
                boolean immutable = false; try { initial.x.rows.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
                require(immutable,"detached rows are immutable");
                immutable = false; try { configured.initialization.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
                require(immutable,"captured initialization is immutable");
                require(initial.foreign.get(0).primary.stream().noneMatch(row -> row.origin.contains("private")),"retained detached old inventory never changes retroactively");
                try (ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(db,x,1000)) {
                    require(x.compile("!newlocal(9);",null,false),"later source publication succeeds");
                    reject(lease::requireCurrent,"source revision advance revokes old lease");
                }
                db.connectContext("T","own"); reject(() -> ContextProductionSnapshotSession.open(db,x,1000),"TRUST view cannot be flattened into independent production sources"); db.disconnectContext("T");
                try (ContextProductionSnapshotSession lease = ContextProductionSnapshotSession.open(db,x,1000)) {
                    db.disconnectContext("A"); reject(lease::requireCurrent,"direct source removal revokes the full-family lease");
                }
                require(db.getRevision() >= revision && Boolean.TRUE.equals(x.query("?",null,false)),"read-only snapshot qualification leaves settled X consistent");
            } finally { user.setCurrentMind(x.closeStorage()); }
            create(dir,"Empty","");
            User emptyUser = new User(); emptyUser.setDatabaseDir(dir.toString()+File.separator); DB emptyDb = new DB(); emptyDb.init(emptyUser);
            Mind emptyRoot = new Mind(emptyUser); emptyUser.setCurrentMind(emptyRoot); Mind seedX = (Mind)emptyRoot.useStorage("SeedOnly"); emptyUser.setCurrentMind(seedX);
            try {
                require(seedX.compile("!$w seed(w,1);",null,false),"real seed-only X fixture"); emptyDb.connectContext("Empty");
                long before = emptyDb.getRevision(); int rows = seedX.getRules().size();
                ContextProductionSnapshotSession absent = ContextProductionSnapshotSession.open(emptyDb,seedX,1000);
                try {
                    require(absent.inventory().foreign.get(0).primary.isEmpty(),"real pinned foreign has an explicit empty primary inventory");
                    require(absent.qualify(12,16,4000000,100000).status == ContextProductionFamilyAssembly.Status.FIXED_POINT,"real empty foreign and seed-only X qualify without synthetic sources");
                    require(emptyDb.getRevision() == before && seedX.getRules().size() == rows,"empty-target qualification does not modify source knowledge");
                    emptyDb.applyConnectionCommand(seedX,"Empty","!private(4);");
                    reject(absent::requireCurrent,"private effective initialization revokes old empty inventory authority");
                } finally { absent.close(); }
                try (ContextProductionSnapshotSession changed = ContextProductionSnapshotSession.open(emptyDb,seedX,1000)) {
                    require(!changed.inventory().foreign.get(0).primary.isEmpty(),"new effective view exposes the added primary fact");
                    ContextProductionSnapshotSession.Binding bound=changed.bind(4000000,10000);
                    org.kanger.ContextProductionCapture.Source initializedSource=bound.foreign.get(0).primary.stream().filter(e->e.statement.contains("private(4)")).findFirst().get().source;
                    require(ContextProductionSnapshotPrivateScalarCheck.keptLocal(changed.qualify(12,16,4000000,100000),initializedSource),"formerly empty cut gets actual private-seed closure, never vacuous empty coverage or raw export");
                }
            } finally { emptyUser.setCurrentMind(seedX.closeStorage()); }
            System.out.println("CONTEXT_PRODUCTION_SNAPSHOT_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
