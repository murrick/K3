/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.*;
import org.kanger.ContextProductionInputs.Declaration;
import org.kanger.ContextWitnessBundle.*;
import org.kanger.ContextWitnessResultTransport.*;
import org.kanger.enums.QueryPass;

/** Observed witness elimination keeps proof dependencies without a fictitious value origin. */
public final class ContextWitnessGroundResultRunner {
    static int checks;static final int WORK=4000000;
    static Source source(int context,int id) {return new Source(new UUID(0,context),1,id,context==0,"base");}
    static Source x(int id) {return source(0,id);}static Source a(int id) {return source(1,id);}static Source b(int id) {return source(2,id);}static Source c(int id) {return source(3,id);}
    static void require(boolean yes,String why) {++checks;if(!yes) throw new AssertionError(why);}
    static Item item(Packet packet,String predicate) {return packet.items.stream().filter(i -> i.atom.predicate.equals(predicate)).findFirst().orElseThrow(() -> new AssertionError("Missing "+predicate));}
    static boolean proof(Item item,int depth,Source... sources) {Set<Source> set=new LinkedHashSet<>(Arrays.asList(sources));return item.proofs.stream().anyMatch(p -> p.depth==depth && p.sources.equals(set));}
    static Packet initial() throws Exception {
        Bundle left=ContextWitnessBundle.capture(new Declaration(x(0),"!$w seed(w,1);"),10000),other=ContextWitnessBundle.capture(new Declaration(x(20),"!$v other(v,1);"),10000);
        try(Session session=ContextWitnessBundle.restoreObserved(Arrays.asList(left,other),10000,100000)) {
            session.capture.replay(session.mind,a(1),"!@x seed(x,1)->q(x,1);");session.capture.replay(session.mind,a(4),"!@x seed(x,1)->q(x,1);");
            session.mind.setQueryPass(QueryPass.ACCEPT);session.mind.link(null,false);Result exported=ContextWitnessResultTransport.export(session,16,WORK);
            require(exported.exported,"initial witnessed proof alternatives exported");return exported.packet;
        }
    }
    static Result next(ContextWitnessSeedReplay round) {return ContextWitnessResultTransport.exportNext(round,16,WORK);}
    static void incomplete(Result result,String why) {require(!result.exported && result.packet==null,why+": "+result.reason);}
    @SuppressWarnings("unchecked") static <T> List<T> journal(ContextWitnessSeedReplay round,String field) throws Exception {
        java.lang.reflect.Field capture=ContextWitnessSeedReplay.class.getDeclaredField("capture");capture.setAccessible(true);
        java.lang.reflect.Field list=ContextProductionCapture.class.getDeclaredField(field);list.setAccessible(true);return (List<T>)list.get(capture.get(round));
    }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("witness-ground-result-").toString());Packet first=initial();
            for(boolean unary:new boolean[]{false,true}) for(boolean negative:new boolean[]{false,true}) {
                String ready=(negative?"~":"")+"ready"+(unary?"(1)":"");Packet second;
                try(ContextWitnessSeedReplay round=ContextWitnessSeedReplay.open(first,10000,100000)) {
                    round.rule(b(2),"!@x q(x,1)->"+ready+";");
                    incomplete(ContextWitnessResultTransport.exportNext(round,1,WORK),"erasure cannot reset carried q depth1");
                    Result result=next(round);require(result.exported,"observed ground witness result exported: "+result.reason);second=result.packet;Item ground=item(second,"ready");
                    require(ground.atom.positive!=negative && ground.atom.arguments.size()==(unary?1:0) && ground.atom.arguments.stream().allMatch(s -> s.origin==null),"ground sign/arity/values retained with no witness-origin alias");
                    require(proof(ground,2,x(0),a(1),b(2)) && proof(ground,2,x(0),a(4),b(2)) && ground.proofs.size()==2,"both complete old Source alternatives retained at cumulative depth2");
                    require(!proof(ground,1,x(0),b(2)) && !proof(ground,2,x(0),a(1),a(4),b(2)),"no source reset or conjunction of independent alternatives");
                    List<ContextWitnessProjection.Materialization> occurrences=journal(round,"witnessMaterializations");
                    require(occurrences.stream().anyMatch(m -> m.matched.predicate.equals("ready") && m.stored.predicate.equals("ready") && m.matched.arguments.stream().allMatch(s -> s.lineage.isEmpty())),"actual ordinary materialization paired with witnessed application");
                    incomplete(ContextWitnessResultTransport.exportNext(round,16,1),"work exhaustion cannot expose a ground result prefix");
                }
                try(ContextWitnessSeedReplay round=ContextWitnessSeedReplay.open(second,10000,100000)) {
                    round.rule(c(3),"!@y "+ready+"&&other(y,1)->out(y,1);");Result result=next(round);
                    require(result.exported,"ground result can be used with a different independent witness: "+result.reason);Item out=item(result.packet,"out");
                    require(proof(out,3,x(0),a(1),b(2),x(20),c(3)) && proof(out,3,x(0),a(4),b(2),x(20),c(3)),"later join retains erased witness dependencies and new witness source");
                    require(out.atom.arguments.get(0).origin.equals(item(first,"other").atom.arguments.get(0).origin),"ground dependency cannot rename the new witness origin");
                }
            }
            try(ContextWitnessSeedReplay round=ContextWitnessSeedReplay.open(first,10000,100000)) {
                round.rule(b(2),"!@z q(z,1)&&other(z,1)->ready;");incomplete(next(round),"ground head cannot fuse two independent witnesses into one premise binding");
            }
            for(boolean removeApplication:new boolean[]{false,true}) try(ContextWitnessSeedReplay round=ContextWitnessSeedReplay.open(first,10000,100000)) {
                round.rule(b(2),"!@x q(x,1)->ready;");require(next(round).exported,"fault control starts with observed fully checked result");
                if(removeApplication) {List<ContextWitnessProjection> observations=journal(round,"witnessProjections");require(observations.removeIf(p -> p.conclusion.predicate.equals("ready")),"remove actual application observation");}
                else {List<ContextWitnessProjection.Materialization> observations=journal(round,"witnessMaterializations");require(observations.removeIf(m -> m.stored.predicate.equals("ready")),"remove actual stored occurrence");}
                incomplete(next(round),"neither application alone nor stored occurrence alone can authorize transport");
            }
            System.out.println("CONTEXT_WITNESS_GROUND_RESULT_PASS checks="+checks);System.exit(0);
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
}
