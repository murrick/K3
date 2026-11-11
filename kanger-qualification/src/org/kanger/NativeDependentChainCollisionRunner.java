/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

/** Chain caches must neither conflate different dependent calls nor separate one forced shared witness. */
public final class NativeDependentChainCollisionRunner {
    static int checks;
    private static void require(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static Rule raw(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static final ContextDependentReplayInspection.Policy POLICY=new ContextDependentReplayInspection.Policy(2000000,1000,64,64000000,100000);
    private static void run(boolean full,int mode,boolean reversed,boolean extra)throws Exception{
        String root=full?"!@a @b $u rel(a,u,b);":"!@a $u @b rel(a,u,b);";
        String a="!@y rel(1,y,1)->out(1,y);",b="!@y rel("+(mode==0?2:1)+",y,"+(mode==1?2:1)+")->out(2,y);";
        String constraint="!@u ~(out(1,u)&&out(2,u));";
        boolean expected=mode!=2&&(full||mode==0);
        // Independent original-only packet and all-value model/refutation, before native cache comparison.
        Mind reference=memory();raw(reference,root);raw(reference,a);raw(reference,b);raw(reference,constraint);
        ContextProductionInventory inventory=ContextProductionInventory.inspect(reference,new UUID(0,6181),1,true,Collections.emptyList(),1000);
        ContextDependentProductionReplay.Saved packet=ContextDependentProductionReplay.capture(reference,inventory,
                new ContextDependentProductionInspection.Request(POLICY,1000,Arrays.asList(
                        new ContextDependentProductionInspection.Step(1,Collections.singletonList(ContextDependentProductionInspection.Donor.original(0))),
                        new ContextDependentProductionInspection.Step(2,Collections.singletonList(ContextDependentProductionInspection.Donor.original(0))))));
        boolean refuted=!packet.evidence.originalUnitRefutations.isEmpty()||!packet.evidence.finalUnitRefutations.isEmpty();
        require(refuted!=expected,"original-only checked production proof agrees with dependency semantics");
        ContextDependentModelSearchInspection.Result model=ContextDependentAutomaticInspection.inspect(new ContextDependentCutInspection.Member(reference,inventory),Collections.emptyList(),
                new ContextDependentIterationInspection.Request(POLICY,10000,8),
                new ContextDependentModelSearchInspection.Limits(30,100000,100000,20000,64000000,64000000,64000000,64000000,64000000),
                new ContextDependentRuntimeContinuationInspection.Projection(100000,1000000,64000000,64000000,64000000,64000000));
        require(model.status==(expected?ContextDependentModelSearchInspection.Status.MODELLED_PENDING:ContextDependentModelSearchInspection.Status.REFUTED),"independent all-value model or sourced refutation: "+model.phase+"/"+model.reason);
        Mind nativeMind=memory();require(nativeMind.compile((reversed?b+a+root:root+a+b)+(extra?"!unrelated(9);":""),null,false),"native setup");
        String before=DmzObservationStateFingerprint.capture(nativeMind);List<ContextDependentProductionReplay.Saved> saved;
        try(CollisionProofCapture capture=CollisionProofCapture.begin()){
            boolean actual=nativeMind.compile(constraint,null,false);
            require(actual==expected,"native chained witness comparison full="+full+" mode="+mode+" reversed="+reversed+" extra="+extra+" expected="+expected+" actual="+actual);
            saved=capture.productionSnapshot();
            if(expected)require(saved.isEmpty()&&capture.dependentSnapshot().isEmpty()&&capture.snapshot().isEmpty(),"consistent original theory leaves no fabricated collision evidence");
            else{
                require(!saved.isEmpty(),"chain conflict keeps complete multi-original replay evidence");
                require(before.equals(DmzObservationStateFingerprint.capture(nativeMind)),"rejected chained constraint rolls back complete native state");
                boolean legacy=false;try{capture.snapshot();}catch(IllegalStateException expectedFailure){legacy=true;}require(legacy,"multi-original chain proof cannot be mislabeled as a legacy pair");
            }
        }
        if(!reversed&&!extra){
            Mind ordinary=memory();require(ordinary.compile(root+a+b,null,false),"same setup without diagnostic capture");
            String state=DmzObservationStateFingerprint.capture(ordinary);
            require(ordinary.compile(constraint,null,false)==expected,"ordinary authoring has the same dependency result with capture disabled");
            if(!expected)require(state.equals(DmzObservationStateFingerprint.capture(ordinary)),"ordinary uncaptured rejection preserves native state");
        }
        // Actual rejection/rollback is over; the declaration packet must stand alone.
        for(ContextDependentProductionReplay.Saved proof:saved){
            ContextDependentProductionInspection.Result replay=ContextDependentProductionReplay.reopen(proof.bytes(),proof.pin,POLICY,1000);
            require(!replay.finalUnitRefutations.isEmpty()||!replay.originalUnitRefutations.isEmpty(),"portable chain proof independently reopens after rollback");
            require(replay.originals.size()==4,"all four actual declaration formulas retained; generated cache is never decoded as knowledge");
        }
    }
    private static void restricted()throws Exception{
        Mind target=memory();raw(target,"!@a @b $u rel(a,u,b);");raw(target,"!@y rel(1,y,1)->out(1,y);");raw(target,"!@y rel(1,y,1)->out(2,y);");raw(target,"!@u ~(out(1,u)&&out(2,u));");
        require(NativeDependentChainCollision.find(target)!=null,"unrestricted declaration refutation control");
        User user=new User();new org.kanger.udf.UDF().init(user);Mind q=new Mind(user);user.setCurrentMind(q);
        org.kanger.interfaces.IContextResults.Revision pin=new org.kanger.interfaces.IContextResults.Revision(new UUID(0,6182),1);
        try(DmzReplayProvenance journal=DmzReplayProvenance.begin();TerminalSupportCapture capture=TerminalSupportCapture.begin(q)){
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,pin,0,DmzReplayProvenance.Authority.EXTERNAL,"!@x seed(x)->hit(x);")),"restriction rule fixture");
            require(Boolean.TRUE.equals(DmzReplayProvenance.acceptRule(q,pin,1,DmzReplayProvenance.Authority.EXTERNAL,"!seed(1);")),"restriction fact fixture");
            DmzCurrentGroundProofInventory proofs=DmzCurrentGroundProofInventory.capture(q,10000,false);require(proofs.eligible,"restriction proof inventory");
            DmzProofWitnesses.Witness selected=null;for(DmzObservedProofGraph.Node node:proofs.graph.observed.nodes)if(node.ground.predicate.equals("hit")){selected=proofs.proofs(node.ground,10000).witnesses.get(0);break;}
            require(selected!=null,"real no-good witness available");
            try(DmzTerminalRestriction active=DmzTerminalRestriction.begin(target,selected);CollisionProofCapture evidence=CollisionProofCapture.begin()){
                require(NativeDependentChainCollision.find(target)==null&&evidence.productionSnapshot().isEmpty(),"whole-declaration refutation never masquerades as restricted branch evidence");
            }
            require(NativeDependentChainCollision.find(target)!=null,"closing branch scope restores ordinary declaration comparison");
        }
    }
    private static Mind open(org.kanger.storage.dumb2.DB db,java.nio.file.Path directory,String name)throws Exception{
        User u=new User();u.setDatabaseDir(directory.toString()+java.io.File.separator);db.init(u);Mind root=new Mind(u);u.setCurrentMind(root);Mind m=(Mind)root.useStorage(name);u.setCurrentMind(m);return m;
    }
    private static void close(Mind m)throws Exception{((User)m.getUser()).setCurrentMind(m.closeStorage());}
    static void storage(boolean conflict,boolean reverse)throws Exception{
        java.nio.file.Path directory=Files.createTempDirectory("dependent-chain-storage-");
        String root=conflict?"!@a @b $u rel(a,u,b);":"!@a $u @b rel(a,u,b);";
        String rules="!@y rel(1,y,1)->out(1,y); !@y rel("+(conflict?1:2)+",y,1)->out(2,y);";
        Mind knowledge=open(new org.kanger.storage.dumb2.DB(),directory,"Knowledge");try{require(knowledge.compile(root+rules,null,false),"physical chain declarations");}finally{close(knowledge);}
        Mind prohibition=open(new org.kanger.storage.dumb2.DB(),directory,"Prohibition");try{require(prohibition.compile("!@u ~(out(1,u)&&out(2,u));",null,false),"physical binary prohibition");}finally{close(prohibition);}
        org.kanger.storage.dumb2.DB db=new org.kanger.storage.dumb2.DB();Mind source=open(db,directory,reverse?"Prohibition":"Knowledge");List<ContextDependentProductionReplay.Saved> saved=Collections.emptyList();long published=-1;
        try{
            long before=db.getRevision();String state=DmzObservationStateFingerprint.capture(source);
            try(CollisionProofCapture capture=CollisionProofCapture.begin()){
                boolean accepted=true;try{db.connectContext(reverse?"Knowledge":"Prohibition");}catch(org.kanger.exception.StorageLifecycleException failure){accepted=false;require(failure.getErrorCode()==org.kanger.enums.StorageLifecycleErrorCode.STORAGE_CONTEXT_CONFLICT&&!failure.getCollisions().isEmpty(),"real chain incompatibility is a sourced storage conflict");}
                require(accepted!=conflict,"physical chained compatibility conflict="+conflict+" reverse="+reverse);
                saved=capture.productionSnapshot();
                if(conflict)require(!saved.isEmpty()&&db.getRevision()==before&&db.federationSnapshot().getConnections().isEmpty()&&state.equals(DmzObservationStateFingerprint.capture(source)),"physical refusal retains portable chain evidence and unchanged source state");
                else{published=db.saveConnections(source);require(!db.federationSnapshot().hasWorkingChanges(),"compatible physical native comparison publishes exact connection");}
            }
        }finally{close(source);}
        for(ContextDependentProductionReplay.Saved proof:saved){ContextDependentProductionInspection.Result reopened=ContextDependentProductionReplay.reopen(proof.bytes(),proof.pin,POLICY,1000);require(!reopened.finalUnitRefutations.isEmpty(),"physical chain evidence reopens after storage closes");}
        if(!conflict){db=new org.kanger.storage.dumb2.DB();source=open(db,directory,reverse?"Prohibition":"Knowledge");try{require(db.getRevision()==published&&db.federationSnapshot().getConnections().size()==1&&!db.federationSnapshot().hasWorkingChanges(),"compatible native connection survives cold reopen");}finally{close(source);}}
    }
    private static void unsupported()throws Exception{
        Mind m=memory();require(m.compile("!@a $u @b rel(a,u,b); !@y rel(1,y,1)->out(1,y); !@y rel(2,y,1)->out(2,y); !flag(9)||other(9);",null,false),"unsupported extra-language fixture is independent of the tested chain");
        String before=DmzObservationStateFingerprint.capture(m);boolean incomplete=false;
        try(CollisionProofCapture capture=CollisionProofCapture.begin()){
            try{m.compile("!@u ~(out(1,u)&&out(2,u));",null,false);}catch(org.kanger.exception.StorageLifecycleException failure){incomplete=failure.getErrorCode()==org.kanger.enums.StorageLifecycleErrorCode.STORAGE_CONTEXT_INCOMPLETE;}
            require(incomplete,"no model for unsupported whole declaration language cannot silently authorize or fabricate a conflict");
            require(capture.productionSnapshot().isEmpty()&&before.equals(DmzObservationStateFingerprint.capture(m)),"incomplete comparison emits no refutation and rolls back the attempted constraint");
        }
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("dependent-chain-collision-").toString());
        for(boolean full:new boolean[]{false,true})for(int mode=0;mode<3;mode++)for(boolean reverse:new boolean[]{false,true})run(full,mode,reverse,false);
        run(false,0,false,true);run(true,2,true,true);
        restricted();unsupported();
        System.out.println("NATIVE_DEPENDENT_CHAIN_COLLISION_PASS checks="+checks);
    }
}
