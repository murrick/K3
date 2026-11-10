/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import org.kanger.interfaces.internal.IContextFederation.VersionHistory;
import org.kanger.storage.dumb2.DB;
import org.kanger.interfaces.internal.IContextFederation.Snapshot;
import org.kanger.exception.StorageLifecycleException;
import org.kanger.enums.StorageLifecycleErrorCode;
import java.util.Arrays;
import java.util.HashSet;

/** Candidate contract: checked proof survives rejection and can be replayed without its live view. */
public final class NativeDependentAuthoringCaptureRunner {
    private static int checks;
    private static final String UNIT="!@a $u @b rel(a,u,b);";
    private static Mind memory() throws Exception {
        User user=new User();Mind mind=new Mind(user);user.setCurrentMind(mind);return mind;
    }
    private static void require(boolean condition,String message) {
        checks++;if(!condition)throw new AssertionError(message);
    }
    private static Mind open(DB db,Path directory) throws Exception {
        return open(db,directory,"Witnesses");
    }
    private static Mind open(DB db,Path directory,String name) throws Exception {
        User user=new User();
        user.setDatabaseDir(directory.toString()+File.separator);
        db.init(user);
        Mind root=new Mind(user);
        user.setCurrentMind(root);
        Mind mind=(Mind)root.useStorage(name);
        user.setCurrentMind(mind);
        return mind;
    }
    private static void close(Mind mind) throws Exception {
        User user=(User)mind.getUser();
        user.setCurrentMind(mind.closeStorage());
    }
    private static void persisted(boolean reverse) throws Exception {
        Path directory=Files.createTempDirectory("dependent-authoring-storage-");
        String compatible="!@u ~(rel(1,u,1)&&rel(2,u,1));";
        String conflict="!@a @u ~(rel(a,u,1)&&rel(a,u,2));";
        DB db=new DB();
        Mind mind=open(db,directory);
        java.util.UUID context;
        long published;
        try {
            require(mind.compile(reverse?compatible:UNIT,null,false),"initial storage write");
            long revision=db.getRevision();
            try(CollisionProofCapture capture=CollisionProofCapture.begin()) {
                require(mind.compile(reverse?UNIT:compatible,null,false),"compatible storage write");
                require(db.getRevision()>revision && capture.snapshot().isEmpty(),"compatible write commits without conflict");
            }
            VersionHistory history=db.versionHistory("Witnesses");
            context=history.getContextId();published=history.getCurrentRevision();
        } finally {close(mind);}

        NativeDependentReplayCapsule.Saved saved;
        db=new DB();mind=open(db,directory);
        try {
            require(context.equals(db.federationSnapshot().getSourceContextId()) && db.getRevision()==published,"fresh owner reopens committed revision");
            String before=DmzObservationStateFingerprint.capture(mind);
            try(CollisionProofCapture capture=CollisionProofCapture.begin()) {
                require(!mind.compile(conflict,null,false),"reopened storage rejects real dependent conflict");
                require(capture.dependentSnapshot().size()==1,"storage rejection retains its packet");
                saved=capture.dependentSnapshot().get(0);
            }
            require(db.getRevision()==published && before.equals(DmzObservationStateFingerprint.capture(mind))
                    && !mind.hasPendingTransactions(),"rejection preserves revision and native view");
            VersionHistory history=db.versionHistory("Witnesses");
            require(context.equals(history.getContextId()) && history.getCurrentRevision()==published,
                    "rejection did not publish a disk revision");
            require(mind.compile("!after_refusal(1);",null,false) && db.getRevision()>published,
                    "same storage can commit again after rejection");
        } finally {close(mind);}

        try(NativeDependentReplayCapsule.Reopened replay=NativeDependentReplayCapsule.reopenPair(saved.bytes(),saved.pin,
                new NativeDependentReplayCapsule.Limits(262144,2,2,1000000,10000))) {
            replay.verify();
            java.util.Set<String> origins=new HashSet<>();
            for(NativeDependentMatchFrame.Entry entry:replay.frame.entries())origins.add(entry.origin);
            require(replay.materialization.remaining.isEmpty() && origins.equals(new HashSet<>(Arrays.asList(UNIT,conflict))),
                    "packet reopens after storage closes with the exact original roots");
        }
        db=new DB();mind=open(db,directory);
        try {
            java.util.Set<String> origins=new HashSet<>();
            for(org.kanger.interfaces.IRule row:mind.getRules())
                if(!row.isDeleted(mind) && !row.isGenerated())origins.add(row.getOrigin());
            require(context.equals(db.federationSnapshot().getSourceContextId()) && db.getRevision()>published
                    && origins.equals(new HashSet<>(Arrays.asList(UNIT,compatible,"!after_refusal(1);"))),
                    "disk keeps accepted rows and excludes the rejected declaration");
        } finally {close(mind);}
    }
    private static void seed(Path directory,String name,String rule) throws Exception {
        Mind mind=open(new DB(),directory,name);
        try {require(mind.compile(rule,null,false),"write fixture "+name);}
        finally {close(mind);}
    }

    private static void attachment(boolean reverse) throws Exception {
        Path directory=Files.createTempDirectory("dependent-attachment-");
        String compatible="!@u ~(rel(1,u,1)&&rel(2,u,1));";
        String conflict="!@a @u ~(rel(a,u,1)&&rel(a,u,2));";
        seed(directory,"Producer",UNIT);
        seed(directory,"Compatible",compatible);
        seed(directory,"Conflicting",conflict);

        String source=reverse?"Compatible":"Producer", target=reverse?"Producer":"Compatible";
        DB db=new DB();
        Mind mind=open(db,directory,source);
        long published;
        try {
            VersionHistory pin=db.versionHistory(target);
            db.connectContext(target);
            db.saveConnections(mind);
            Snapshot view=db.federationSnapshot();
            published=db.getRevision();
            require(!view.hasWorkingChanges() && view.getPublishedConnections().size()==1
                    && view.getConnections().get(0).getTargetContextId().equals(pin.getContextId())
                    && view.getConnections().get(0).getPinnedRevision()==pin.getCurrentRevision(),
                    "compatible target is published at its exact pin");
        } finally {close(mind);}
        db=new DB();mind=open(db,directory,source);
        try {
            Snapshot view=db.federationSnapshot();
            require(db.getRevision()==published && view.getConnections().size()==1
                    && view.getConnections().get(0).getLocator().equals(target)
                    && !view.hasWorkingChanges(),"compatible attachment survives fresh reopen");
        } finally {close(mind);}

        source=reverse?"Conflicting":"Producer"; target=reverse?"Producer":"Conflicting";
        db=new DB();mind=open(db,directory,source);
        Snapshot before;
        java.util.List<NativeDependentReplayCapsule.Saved> packets;
        try {
            before=db.federationSnapshot();
            long targetRevision=db.versionHistory(target).getCurrentRevision();
            String state=DmzObservationStateFingerprint.capture(mind);
            try(CollisionProofCapture capture=CollisionProofCapture.begin()) {
                boolean refused=false;
                try {db.connectContext(target);}
                catch(StorageLifecycleException failure) {
                    refused=failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT
                            && !failure.getCollisions().isEmpty()
                            && failure.toString().contains(source+"@R")
                            && failure.toString().contains(target+"@R");
                }
                require(refused,"conflicting attachment is rejected with evidence");
                packets=capture.dependentSnapshot();
                require(packets.size()==2,"both qualification directions capture dependent evidence");
            }
            Snapshot after=db.federationSnapshot();
            require(before.getSourceRevision()==after.getSourceRevision()
                    && before.getSourceContextId().equals(after.getSourceContextId())
                    && pins(before).equals(pins(after))
                    && !after.hasWorkingChanges()
                    && db.versionHistory(source).getCurrentRevision()==before.getSourceRevision()
                    && db.versionHistory(target).getCurrentRevision()==targetRevision
                    && state.equals(DmzObservationStateFingerprint.capture(mind))
                    && !mind.hasPendingTransactions(),"refusal preserves the source, target, and connection vector");
        } finally {close(mind);}
        for(NativeDependentReplayCapsule.Saved packet:packets)
            try(NativeDependentReplayCapsule.Reopened replay=NativeDependentReplayCapsule.reopenPair(packet.bytes(),packet.pin,
                    new NativeDependentReplayCapsule.Limits(262144,2,2,1000000,10000))) {
                replay.verify();
                java.util.Set<String> origins=new HashSet<>();
                for(NativeDependentMatchFrame.Entry entry:replay.frame.entries())origins.add(entry.origin);
                require(replay.materialization.remaining.isEmpty()
                        && origins.equals(new HashSet<>(Arrays.asList(UNIT,conflict))),
                        "independent replay verifies the exact roots of the failed attachment");
            }
        db=new DB();mind=open(db,directory,source);
        try {
            Snapshot reopened=db.federationSnapshot();
            require(reopened.getSourceRevision()==before.getSourceRevision()
                    && pins(before).equals(pins(reopened)) && !reopened.hasWorkingChanges(),
                    "failed attachment leaves no disk connection after reopen");
        } finally {close(mind);}
    }
    private static void revisionSwitch(boolean reverse) throws Exception {
        Path directory=Files.createTempDirectory("dependent-switch-");
        String prohibition="!@a @u ~(rel(a,u,1)&&rel(a,u,2));";
        String initial=reverse?"!@a @b $u rel(a,u,b);":"!@u ~(rel(1,u,1)&&rel(2,u,1));";
        seed(directory,"Source",reverse?prohibition:UNIT);
        seed(directory,"Target",initial);

        DB db=new DB();
        Mind mind=open(db,directory,"Source");
        long originalPin,published;
        try {
            originalPin=db.connectContext("Target").getPinnedRevision();
            published=db.saveConnections(mind);
        } finally {close(mind);}
        db=new DB();mind=open(db,directory,"Target");
        long compatible,conflicting;
        try {
            require(mind.compile("!switch_marker(1);",null,false),"new compatible target revision");
            compatible=db.getRevision();
            require(mind.compile(reverse?UNIT:prohibition,null,false),"new target rule is locally admissible");
            conflicting=db.getRevision();
            require(originalPin<compatible && compatible<conflicting,"three distinct pinned revisions");
        } finally {close(mind);}

        java.util.List<NativeDependentReplayCapsule.Saved> packets;
        db=new DB();mind=open(db,directory,"Source");
        long switchedRevision;
        try {
            Snapshot before=db.federationSnapshot();
            require(before.getSourceRevision()==published && before.getConnections().size()==1
                    && before.getConnections().get(0).getPinnedRevision()==originalPin
                    && !before.hasWorkingChanges(),"target advances without moving the published pin");
            String state=DmzObservationStateFingerprint.capture(mind);
            try(CollisionProofCapture capture=CollisionProofCapture.begin()) {
                boolean refused=false;
                try {db.switchContextRevision("Target",conflicting);}
                catch(StorageLifecycleException failure) {
                    refused=failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT
                            && !failure.getCollisions().isEmpty()
                            && failure.toString().contains("Source@R"+published)
                            && failure.toString().contains("Target@R"+conflicting);
                }
                require(refused,"switch to incompatible revision is rejected with evidence");
                packets=capture.dependentSnapshot();
                require(packets.size()==2,"both switch qualification directions capture replay packets");
            }
            Snapshot after=db.federationSnapshot();
            require(pins(before).equals(pins(after)) && !after.hasWorkingChanges()
                    && after.getSourceRevision()==published && db.versionHistory("Target").getCurrentRevision()==conflicting
                    && state.equals(DmzObservationStateFingerprint.capture(mind))
                    && !mind.hasPendingTransactions(),"failed switch preserves the published pin and live state");
            require(db.switchContextRevision("Target",compatible).getPinnedRevision()==compatible
                    && db.getRevision()==published && db.federationSnapshot().hasWorkingChanges(),
                    "compatible historical revision becomes the working pin before publication");
            switchedRevision=db.saveConnections(mind);
            require(switchedRevision>published && !db.federationSnapshot().hasWorkingChanges(),
                    "publishing the switch advances the source revision");
        } finally {close(mind);}
        for(NativeDependentReplayCapsule.Saved packet:packets)
            try(NativeDependentReplayCapsule.Reopened replay=NativeDependentReplayCapsule.reopenPair(packet.bytes(),packet.pin,
                    new NativeDependentReplayCapsule.Limits(262144,2,2,1000000,10000))) {
                replay.verify();
                java.util.Set<String> origins=new HashSet<>();
                for(NativeDependentMatchFrame.Entry entry:replay.frame.entries())origins.add(entry.origin);
                require(replay.materialization.remaining.isEmpty()
                        && origins.equals(new HashSet<>(Arrays.asList(UNIT,prohibition))),
                        "rejected revision evidence replays after close from the exact roots");
            }
        db=new DB();mind=open(db,directory,"Source");
        try {
            Snapshot reopened=db.federationSnapshot();
            require(reopened.getSourceRevision()==switchedRevision && reopened.getConnections().size()==1
                    && reopened.getConnections().get(0).getPinnedRevision()==compatible
                    && db.versionHistory("Target").getCurrentRevision()==conflicting && !reopened.hasWorkingChanges(),
                    "saved historical pin survives reopen with newer incompatible target");
            db.applyConnectionCommand(mind,"Target","!private_marker(1);");
            long privateRevision=db.saveConnections(mind);
            Snapshot edited=db.federationSnapshot();
            require(edited.getConnections().get(0).getInitialization().equals(Arrays.asList("!private_marker(1);"))
                    && !edited.hasWorkingChanges(),"compatible private initialization publishes at the existing pin");
            String state=DmzObservationStateFingerprint.capture(mind);
            boolean refused=false;
            try {db.applyConnectionCommand(mind,"Target",reverse?UNIT:prohibition);}
            catch(StorageLifecycleException failure) {
                refused=failure.getErrorCode()==StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT
                        && !failure.getCollisions().isEmpty();
            }
            require(refused,"conflicting private initialization is rejected with evidence");
            Snapshot after=db.federationSnapshot();
            require(after.getSourceRevision()==privateRevision && pins(edited).equals(pins(after))
                    && after.getConnections().get(0).getInitialization().equals(edited.getConnections().get(0).getInitialization())
                    && !after.hasWorkingChanges() && state.equals(DmzObservationStateFingerprint.capture(mind))
                    && !mind.hasPendingTransactions(),"private refusal keeps exact working and published topology");
            switchedRevision=privateRevision;
        } finally {close(mind);}
        db=new DB();mind=open(db,directory,"Source");
        try {
            Snapshot reopened=db.federationSnapshot();
            require(reopened.getSourceRevision()==switchedRevision && !reopened.hasWorkingChanges()
                    && reopened.getConnections().get(0).getPinnedRevision()==compatible
                    && reopened.getConnections().get(0).getInitialization().equals(Arrays.asList("!private_marker(1);"))
                    && db.versionHistory("Target").getCurrentRevision()==conflicting,
                    "only the approved private edit survives reopen; target history is unchanged");
        } finally {close(mind);}
    }

    private static java.util.Map<String,String> pins(Snapshot snapshot) {
        java.util.Map<String,String> pins=new java.util.HashMap<>();
        for(org.kanger.interfaces.internal.IContextFederation.Connection connection:snapshot.getConnections())
            pins.put(connection.getLocator(),connection.getTargetContextId()+"@"+connection.getPinnedRevision());
        return pins;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("dependent-authoring-capture-").toString());
        for(boolean reverse:new boolean[]{false,true}) {
            Mind mind=memory();
            String prohibition="!@a @u ~(rel(a,u,1)&&rel(a,u,2));";
            String first=reverse?prohibition:UNIT,second=reverse?UNIT:prohibition;
            require(mind.compile(first+" !@x left(x)->right(x);",null,false),"initial rows accepted");
            String before=DmzObservationStateFingerprint.capture(mind);
            NativeDependentReplayCapsule.Saved saved;
            try(CollisionProofCapture capture=CollisionProofCapture.begin()) {
                require(!mind.compile(second,null,false),"authoring refuses the complete dependent conflict");
                require(capture.dependentSnapshot().size()==1 && capture.symbolicSnapshot().isEmpty(),
                        "one dependent packet, without replacing it by another symbolic shape");
                saved=capture.dependentSnapshot().get(0);
                boolean legacyRefused=false;
                try {capture.snapshot();}
                catch(IllegalStateException refused){legacyRefused=refused.getMessage().contains("Dependent collision");}
                require(legacyRefused,"legacy pair reader cannot hide dependent evidence");
            }
            require(before.equals(DmzObservationStateFingerprint.capture(mind)) && !mind.hasPendingTransactions(),
                    "rejected authoring preserves the original view");
            NativeDependentReplayCapsule.Limits limits=new NativeDependentReplayCapsule.Limits(262144,2,2,1000000,10000);
            boolean wrongFormat=false;
            try(NativeDependentReplayCapsule.Reopened ignored=NativeDependentReplayCapsule.reopen(saved.bytes(),saved.pin,limits)) { }
            catch(java.io.IOException refused){wrongFormat=true;}
            require(wrongFormat,"pair packet is not a whole-inventory capsule");
            try(NativeDependentReplayCapsule.Reopened replay=NativeDependentReplayCapsule.reopenPair(saved.bytes(),saved.pin,limits)) {
                replay.verify();
                NativeDependentMatchFrame.Materialization proof=replay.materialization;
                require(proof.remaining.isEmpty() && proof.support.depth==2 && proof.producers.size()==2
                        && proof.support.sources.size()==2 && replay.sources.size()==2,"complete two-step proof reopens");
                require(proof.producerSources.get(0).equals(proof.producerSources.get(1)),
                        "both applications refer to the same original producer");
                java.util.Set<String> origins=new HashSet<>();
                for(NativeDependentMatchFrame.Entry entry:replay.frame.entries())origins.add(entry.origin);
                require(origins.equals(new HashSet<>(Arrays.asList(first,second))),
                        "packet contains both actual declarations and excludes the unrelated rule");
            }

            Mind compatible=memory();
            String across="!@u ~(rel(1,u,1)&&rel(2,u,1));";
            require(compatible.compile(reverse?across:UNIT,null,false),"first compatible row accepted");
            try(CollisionProofCapture capture=CollisionProofCapture.begin()) {
                require(compatible.compile(reverse?UNIT:across,null,false),"different dependency coordinates are compatible");
                require(capture.dependentSnapshot().isEmpty() && capture.symbolicSnapshot().isEmpty()
                        && capture.snapshot().isEmpty(),"false legacy join leaves no conflict in any journal");
            }
        }
        persisted(false);
        persisted(true);
        attachment(false);
        attachment(true);
        revisionSwitch(false);
        revisionSwitch(true);
        Mind ordinary=memory();
        require(ordinary.compile("!p(1);",null,false),"first ordinary fact accepted");
        try(CollisionProofCapture capture=CollisionProofCapture.begin()) {
            require(!ordinary.compile("!~p(1);",null,false),"ordinary conflict still refused");
            require(!capture.snapshot().isEmpty() && capture.dependentSnapshot().isEmpty(),"ordinary pair keeps its original format");
        }
        System.out.println("NATIVE_DEPENDENT_AUTHORING_CAPTURE_PASS checks="+checks);
    }
}
