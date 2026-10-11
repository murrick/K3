/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger.storage.dumb2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.kanger.Mind;
import org.kanger.User;
import org.kanger.DmzCandidateSession;
import org.kanger.DmzSourceShape;
import org.kanger.interfaces.DmzConditionalResult;
import org.kanger.interfaces.DmzSourcePlan;
import org.kanger.interfaces.IContextResults;

/** Real pinned sources exercise the internal owner and checked candidate dispatch. */
public final class DmzSourceSessionRunner {
    private static int checks;
    private interface Attempt { void run() throws Exception; }
    public static void main(String[] args) {
        try { run(); System.out.println("DMZ_SOURCE_SESSION_PASS checks="+checks); System.exit(0); }
        catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static DmzCandidateSession.Limits limits(int nodes) {
        return new DmzCandidateSession.Limits(10000,10000,10000,10000,10000,10000,10000,4,nodes,1000000);
    }
    private static void run() throws Exception {
        Path directory=Files.createTempDirectory("dmz-source-session-");
        System.setProperty("user.home",directory.toString());
        create(directory,"Q","!@x authority(x) -> derived(x); !authority(1);");
        IContextResults.Revision a=create(directory,"A","!authority(2); !~derived(2); !@x other(x) -> more(x); !other(3);");
        IContextResults.Revision b=create(directory,"B","!~derived(1); !authority(1);");
        IContextResults.Revision c=create(directory,"C","!fresh(9);");
        IContextResults.Revision d=create(directory,"D","!~authority(2);");
        IContextResults.Revision fraction=create(directory,"Fraction","!fraction(1.0);");
        User user=new User(); user.setDatabaseDir(directory.toString()+java.io.File.separator);
        DB data=new DB(); data.init(user);
        Mind q=new Mind(user); user.setCurrentMind(q); q=(Mind)q.useStorage("Q"); user.setCurrentMind(q);
        final Mind physical=q;
        try {
            IContextResults.Revision pin=new IContextResults.Revision(data.getContextId(),data.getRevision());
            DmzSourcePlan.Request request=new DmzSourcePlan.Request(pin,Arrays.asList(
                    new DmzSourcePlan.Selection("A",a),new DmzSourcePlan.Selection("B",b),
                    new DmzSourcePlan.Selection("C",c),new DmzSourcePlan.Selection("D",d),
                    new DmzSourcePlan.Selection("Fraction",fraction)),1000);
            Mind pending=new Mind(q);
            user.setCurrentMind(pending);
            try { reject(() -> data.openDmzCandidateSession(pending,request,10000),"Settled physical Q"); }
            finally { user.setCurrentMind(q); q.release(pending); }
            Mind reserved=new Mind(q);
            try { reject(() -> data.openDmzCandidateSession(physical,request,10000),"Settled physical Q"); }
            finally { q.release(reserved); }
            DmzSourcePreflight.Inputs inputs=DmzSourcePreflight.acquire(q,"Q",request,directory::resolve);
            DmzCandidateSession.SourceRule support=rule(inputs,a,"!authority(2);");
            DmzCandidateSession.SourceRule opposed=rule(inputs,a,"!~derived(2);");
            DmzCandidateSession.SourceRule refusedQ=rule(inputs,b,"!~derived(1);");
            DmzCandidateSession.SourceRule duplicate=rule(inputs,b,"!authority(1);");
            DmzCandidateSession.SourceRule fresh=rule(inputs,c,"!fresh(9);");
            DmzCandidateSession.SourceRule opposingSupport=rule(inputs,d,"!~authority(2);");
            DmzCandidateSession.SourceRule production=rule(inputs,a,"!@x other(x) -> more(x);");
            DmzCandidateSession.SourceRule decimal=rule(inputs,fraction,"!fraction(1.0);");
            String state=org.kanger.DmzSourceMaterializationQualification.fingerprint(q);
            DmzCandidateSession.Result retained;
            DmzSourceSession session=data.openDmzCandidateSession(q,request,10000);
            try {
                require(Boolean.TRUE.equals(session.queryQ("?derived(1);",10000).value),"Q generated answer rebuilt under owned capture");
                try(DmzSourceSession nested=data.openDmzCandidateSession(physical,request,10000)) {
                    reject(() -> session.close(),"Active owning replay journal");
                    reject(() -> session.queryQ("?derived(1);",10000),"Active owning replay journal");
                    require(Boolean.TRUE.equals(nested.queryQ("?derived(1);",10000).value),"out-of-order outer close leaves inner owner intact");
                }
                require(Boolean.TRUE.equals(session.queryQ("?derived(1);",10000).value),"nested owner closure restores outer observation scopes");
                java.util.concurrent.atomic.AtomicInteger denied=new java.util.concurrent.atomic.AtomicInteger();
                Thread foreignThread=new Thread(() -> {
                    try { session.queryQ("?derived(1);",10000); }catch(Exception expected) { denied.incrementAndGet(); }
                    try { session.close(); }catch(Exception expected) { denied.incrementAndGet(); }
                });
                foreignThread.start(); foreignThread.join();
                require(denied.get()==2 && Boolean.TRUE.equals(session.queryQ("?derived(1);",10000).value),"foreign thread cannot query or close the owning session");
                DmzCandidateSession.Result refusal=session.evaluate(refusedQ,Collections.emptyList(),"?derived(1);",limits(64));
                require(refusal.status==DmzCandidateSession.Status.DECLINED_Q && refusal.legacyAnswer==null
                        && refusal.conditional.disposition==DmzConditionalResult.Disposition.DECLINED_BY_Q,"physical Q priority precedes candidate admission");
                DmzCandidateSession.Result reuse=session.evaluate(duplicate,Collections.emptyList(),"?derived(1);",limits(64));
                require(reuse.status==DmzCandidateSession.Status.REUSED_PRIMARY && reuse.conditional==null
                        && Boolean.TRUE.equals(reuse.legacyAnswer.value),"primary reuse keeps explicit legacy contract");
                DmzCandidateSession.Result probe=session.evaluate(fresh,Collections.emptyList(),"?fresh(9);",limits(64));
                require(probe.status==DmzCandidateSession.Status.PROBED_NEW_INPUT && probe.conditional==null
                        && Boolean.TRUE.equals(probe.legacyAnswer.value),"fresh candidate keeps explicit legacy probe contract");
                require(!Boolean.TRUE.equals(session.queryQ("?fresh(9);",10000).value),"candidate probe never persists into the next call");
                retained=session.evaluate(opposed,Collections.singletonList(support),"?derived(1);",limits(64));
                verifyFamily(retained);
                require(retained.candidate.context.equals(a.getContextId()) && retained.candidate.rule==opposed.rule
                        && retained.supports.size()==1 && retained.supports.get(0).rule==support.rule
                        && retained.authorityRevision.getContextId().equals(pin.getContextId())
                        && retained.candidateRevision.getRevision()==a.getRevision()
                        && retained.supportRevisions.get(0).getRevision()==a.getRevision(),"dispatch uses acquired exact source occurrences");
                try { retained.supports.clear(); throw new AssertionError("mutable supports"); }
                catch(UnsupportedOperationException expected) { ++checks; }
                require(!Boolean.TRUE.equals(session.queryQ("?authority(2);",10000).value),"explicit supports do not become authority or persist after evaluation");
                IContextResults.Revision advanced=create(directory,"A","!later(9);");
                require(advanced.getRevision()>a.getRevision(),"foreign HEAD advances while the owned session remains open");
                DmzCandidateSession.Result pinned=session.evaluate(opposed,Collections.singletonList(support),"?derived(1);",limits(64));
                require(pinned.candidateRevision.getRevision()==a.getRevision()
                        && pinned.supportRevisions.get(0).getRevision()==a.getRevision(),"owned source occurrences never float to the new foreign HEAD");
                DmzCandidateSession.Result stopped=session.evaluate(opposed,Collections.singletonList(support),"?derived(1);",limits(1));
                require(stopped.conditional.restartRequired && stopped.conditional.family.outcome==DmzConditionalResult.Stop.NODE_LIMIT,
                        "bounded branch stop remains a detached restart observation");
                verifyFamily(session.evaluate(opposed,Collections.singletonList(support),"?derived(1);",limits(64)));
                reject(() -> session.evaluate(production,Collections.emptyList(),"?derived(1);",limits(64)),"primary grounds");
                reject(() -> session.evaluate(decimal,Collections.emptyList(),"?derived(1);",limits(64)),"primary grounds");
                reject(() -> session.evaluate(new DmzCandidateSession.SourceRule(java.util.UUID.randomUUID(),1),Collections.emptyList(),"?derived(1);",limits(64)),"Selected external");
                reject(() -> session.evaluate(new DmzCandidateSession.SourceRule(pin.getContextId(),1),Collections.emptyList(),"?derived(1);",limits(64)),"Selected external");
                reject(() -> session.evaluate(new DmzCandidateSession.SourceRule(a.getContextId(),Long.MAX_VALUE),Collections.emptyList(),"?derived(1);",limits(64)),"source-local primary");
                long generated;
                try(SnapshotMindRuntime snapshot=SnapshotMindRuntime.open(directory.resolve("A"),new RevisionRef(a.getContextId(),a.getRevision()),"A-cache")) {
                    generated=-1;
                    for(org.kanger.interfaces.IRule row:snapshot.getMind().getRules())
                        if(snapshot.getMind().getRules().isGenerated(row)) { generated=row.getId(); break; }
                }
                require(generated>=0,"fixture contains foreign generated cache");
                final long cacheId=generated;
                reject(() -> session.evaluate(new DmzCandidateSession.SourceRule(a.getContextId(),cacheId),Collections.emptyList(),"?derived(1);",limits(64)),"source-local primary");
                reject(() -> session.evaluate(opposed,Arrays.asList(support,support),"?derived(1);",limits(64)),"Distinct support");
                reject(() -> session.evaluate(opposed,Collections.singletonList(opposed),"?derived(1);",limits(64)),"Distinct support");
                reject(() -> session.evaluate(fresh,Arrays.asList(support,opposingSupport),"?derived(1);",limits(64)),null);
                require(Boolean.TRUE.equals(session.queryQ("?derived(1);",10000).value),"failed support materialization releases child and preserves usable owner");
                reject(() -> session.evaluate(fresh,Collections.emptyList(),"@query",limits(64)),"integer query");
                require(state.equals(org.kanger.DmzSourceMaterializationQualification.fingerprint(q))
                        && user.getCurrentMind()==q && data.getRevision()==pin.getRevision() && !data.hasConnectedContexts(),"all dispatch outcomes preserve physical Q and topology");
            } finally { session.close(); session.close(); }
            verifyFamily(retained);
            reject(() -> session.queryQ("?derived(1);",10000),"owning provider");
            reject(() -> data.openDmzCandidateSession(physical,request,1),null);
            try(DmzSourceSession freshSession=data.openDmzCandidateSession(q,request,10000)) {
                require(Boolean.TRUE.equals(freshSession.queryQ("?derived(1);",10000).value),"failed open restores scopes and permits a fresh owner");
                require(!Boolean.TRUE.equals(freshSession.queryQ("?fresh(9);",10000).value),"fresh owner retains no earlier probe inputs");
            }
            try(DmzSourceSession stale=data.openDmzCandidateSession(q,request,10000)) {
                Mind displaced=new Mind(user); user.setCurrentMind(displaced);
                try { reject(() -> stale.queryQ("?derived(1);",10000),"Physical Q session boundary changed"); }
                finally { user.setCurrentMind(q); }
                require(Boolean.TRUE.equals(stale.queryQ("?derived(1);",10000).value),"restored current-Mind slot restores unchanged source boundary");
                require(Boolean.TRUE.equals(q.query("!changed(7);",null,false)),"intentional physical Q edit for stale-owner control");
                reject(() -> stale.queryQ("?derived(1);",10000),"Physical Q");
            }
            data.connectContext("C");
            try {
                IContextResults.Revision connectedPin=new IContextResults.Revision(data.getContextId(),data.getRevision());
                reject(() -> data.openDmzCandidateSession(physical,new DmzSourcePlan.Request(connectedPin,request.external,1000),10000),"existing Context connections");
                require(data.hasConnectedContexts(),"refused internal bridge preserves existing ordinary connections");
            } finally { data.disconnectContext("C"); }
            IContextResults.Revision changedPin=new IContextResults.Revision(data.getContextId(),data.getRevision());
            try(DmzSourceSession updated=data.openDmzCandidateSession(q,new DmzSourcePlan.Request(changedPin,request.external,1000),10000)) {
                require(Boolean.TRUE.equals(updated.queryQ("?changed(7);",10000).value),"new session reacquires live Q after old boundary becomes stale");
            }
        } finally { user.setCurrentMind(q.closeStorage()); }
    }
    private static void verifyFamily(DmzCandidateSession.Result result) {
        require(result.status==DmzCandidateSession.Status.CONDITIONAL_RESULT && result.legacyAnswer==null
                && result.conditional!=null && result.conditional.family.outcome==DmzConditionalResult.Stop.COMPLETE
                && result.conditional.family.opinions.size()>=2 && !result.conditional.restartRequired,"complete detached candidate family");
        for(DmzConditionalResult.Opinion opinion:result.conditional.family.opinions)
            require(Boolean.TRUE.equals(opinion.answer.value),"every opinion retains original physical Q consequence");
    }
    private static DmzCandidateSession.SourceRule rule(DmzSourcePreflight.Inputs inputs,IContextResults.Revision pin,String origin) {
        for(int i=0;i<inputs.external.size();++i)if(inputs.plan.external.get(i).revision.getContextId().equals(pin.getContextId()))
            for(DmzSourceShape.Declaration declaration:inputs.external.get(i).declarations)
                if(declaration.statement.equals(origin))return new DmzCandidateSession.SourceRule(pin.getContextId(),declaration.sourceRule);
        throw new AssertionError("Missing fixture source declaration: "+origin);
    }
    private static IContextResults.Revision create(Path directory,String name,String statements) throws Exception {
        User user=new User(); user.setDatabaseDir(directory.toString()+java.io.File.separator);
        DB data=new DB(); data.init(user);
        Mind mind=new Mind(user); user.setCurrentMind(mind); mind=(Mind)mind.useStorage(name); user.setCurrentMind(mind);
        try { require(mind.compile(statements,null,false),"persistent fixture "+name); }
        finally { user.setCurrentMind(mind.closeStorage()); }
        try(ContextSnapshot snapshot=ContextSnapshot.open(directory.resolve(name))) {
            return new IContextResults.Revision(snapshot.getContextId(),snapshot.getRevision());
        }
    }
    private static void reject(Attempt operation,String fragment) throws Exception {
        boolean refused=false;
        try { operation.run(); }
        catch(Exception expected) { if(fragment!=null && !expected.toString().contains(fragment))throw expected; refused=true; }
        require(refused,"explicit refusal: "+fragment);
    }
    private static void require(boolean value,String reason) { ++checks; if(!value)throw new AssertionError(reason); }
}
