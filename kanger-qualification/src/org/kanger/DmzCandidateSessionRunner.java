/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.Collections;
import org.kanger.interfaces.IContextResults;
import org.kanger.units.Rule;
import org.kanger.units.Term;

/** Failure cleanup and typed source-cache/origin qualification for the internal owner. */
public final class DmzCandidateSessionRunner {
    private static int checks;
    private interface Attempt { void run() throws Exception; }
    public static void main(String[] args) {
        try { run(); System.out.println("DMZ_CANDIDATE_SESSION_PASS checks="+checks); System.exit(0); }
        catch(Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static Mind source(String facts) throws Exception {
        User user=new User(); Mind source=new Mind(user); user.setCurrentMind(source);
        require(source.compile(facts,null,false),"source declarations accepted"); return source;
    }
    private static DmzCandidateSession.Participant participant(Mind source) throws Exception {
        return new DmzCandidateSession.Participant(new IContextResults.Revision(java.util.UUID.randomUUID(),1),DmzSourceShape.inspect(source,1000));
    }
    private static Rule primary(Mind source,String origin) throws Exception {
        for(org.kanger.interfaces.IRule item:source.getRules())
            if(!source.getRules().isGenerated(item) && origin.equals(item.getOrigin()))return (Rule)item;
        throw new AssertionError("Missing source declaration "+origin);
    }
    private static void run() throws Exception {
        Mind original=source("!@x source(x) -> derived(x); !source(1);");
        DmzCandidateSession.Participant authority=participant(original);
        require(!authority.inputs.cachedGrounds.isEmpty(),"physical source has generated cache observations");
        require(authority.inputs.isCurrent(original),"observation captures exact source boundary");
        String state=DmzObservationStateFingerprint.capture(original);
        try(DmzReplayProvenance outerJournal=DmzReplayProvenance.begin(); TerminalSupportCapture outerCapture=TerminalSupportCapture.begin()) {
            reject(() -> DmzCandidateSession.open(authority,Collections.emptyList(),1),null);
            outerJournal.requireActiveJournal(); outerCapture.requireActive(); ++checks;
            try(DmzCandidateSession session=DmzCandidateSession.open(authority,Collections.emptyList(),10000)) {
                require(Boolean.TRUE.equals(session.queryQ("?derived(1);",10000).value),"cache is supported by reconstructed primary proof");
                reject(() -> session.queryQ("?derived(1);",0),"audit budget");
                require(Boolean.TRUE.equals(session.queryQ("?derived(1);",10000).value),"phase validation refusal leaves owner usable");
            }
            outerJournal.requireActiveJournal(); outerCapture.requireActive(); ++checks;
            primary(original,"!source(1);").setDeleted(true,original);
            DmzCandidateSession.Participant orphan=participant(original);
            require(!orphan.inputs.cachedGrounds.isEmpty(),"orphan cache remains visible while its primary support is absent");
            reject(() -> DmzCandidateSession.open(orphan,Collections.emptyList(),10000),"Physical Q cache has no complete reconstructed support");
            outerJournal.requireActiveJournal(); outerCapture.requireActive(); ++checks;
            primary(original,"!source(1);").setDeleted(false,original);
            try(DmzCandidateSession session=DmzCandidateSession.open(participant(original),Collections.emptyList(),10000)) {
                require(Boolean.TRUE.equals(session.queryQ("?derived(1);",10000).value),"failed physical cache qualification releases every owning scope");
            }
            require(state.equals(DmzObservationStateFingerprint.capture(original)),"reconstruction and cache controls leave original source restored");
        }
        Mind strings=source("!@x source(x) -> derived(x); !source(item);");
        primary(strings,"!source(item);").setDeleted(true,strings);
        DmzCandidateSession.Participant unsupported=participant(strings);
        require(unsupported.inputs.unsupportedGenerated>0,"unsupported physical cache is recorded separately from primary inputs");
        reject(() -> DmzCandidateSession.open(unsupported,Collections.emptyList(),10000),"Unsupported physical Q generated cache");
        Mind wrong=source("!base(1);");
        primary(wrong,"!base(1);").setOrigin(new Term("!other(1);",wrong));
        reject(() -> DmzSourceShape.inspect(wrong,1000),"ground origin disagrees");
        Mind unit=source("!@x source(x) -> derived(x);");
        Rule production=primary(unit,"!@x source(x) -> derived(x);");
        production.setOrigin(new Term("!@y source(y) -> ~derived(y);",unit));
        reject(() -> DmzSourceShape.inspect(unit,1000),"unit origin disagrees");
        production.setOrigin(new Term("!@y source(y) -> derived(y);",unit));
        require("!@y source(y) -> derived(y);".equals(DmzSourceShape.inspect(unit,1000).declarations.get(0).unitStatement),
                "unit origin permits alpha renaming while checking native signs");
        Mind negativeUnit=source("!@x ~source(x) -> ~derived(x);");
        require("!@x ~source(x) -> ~derived(x);".equals(DmzSourceShape.inspect(negativeUnit,1000).declarations.get(0).unitStatement),
                "negative antecedent and consequent agree with native clause polarity");
        reject(() -> new DmzCandidateSession.Limits(10000,10000,10000,10000,10000,10000,10000,17,64,1000000),"Bounded candidate");
        reject(() -> new DmzCandidateSession.Limits(10000,10000,0,10000,10000,10000,10000,4,64,1000000),"Phase allowance");
        reject(() -> DmzCandidateSession.open(authority,Collections.singletonList(authority),10000),"Unique external");
        Mind spaced=source("! spaced( 1 );");
        DmzSourceShape.Declaration input=DmzSourceShape.inspect(spaced,1000).declarations.get(0);
        require("!spaced(1);".equals(input.groundStatement),"ground formatting normalizes without changing its native signed value");
    }
    private static void reject(Attempt attempt,String fragment) throws Exception {
        boolean refused=false;
        try { attempt.run(); }
        catch(Exception expected) { if(fragment!=null && !expected.toString().contains(fragment))throw expected; refused=true; }
        require(refused,"explicit refusal: "+fragment);
    }
    private static void require(boolean value,String reason) { ++checks; if(!value)throw new AssertionError(reason); }
}
