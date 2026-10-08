package org.kanger;
import java.util.*;
import org.kanger.units.*;
/** Frame shape/lifetime controls; no unsupported concurrent mutation is exercised. */
public final class ObservationLayersRunner {
 static int checks;static void require(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
 public static void main(String[] args)throws Exception{
  User u=(User)UserFactory.createUser("observation-frame","observation-frame");Mind m=new Mind(u);TVariable x=MaterializationRoutingRunner.variable(m,"x"),y=MaterializationRoutingRunner.variable(m,"y");Term a=(Term)m.getTerms().add("a");TValue vx=m.getTValues().add(x,a),vy=m.getTValues().add(y,a);m.getTValues().forEach(x,o->true);m.getTValues().forEach(y,o->true);
  String before=ResidentTValueRead.fingerprint(m);ObservationLayers first=new ObservationLayers(m);require(first.extracted()==0,"lazy frame");require(first.values().size()==2,"complete values");require(first.bucket(x.getId()).equals(ResidentTValueRead.bucket(m,x.getId()))&&first.bucket(y.getId()).equals(ResidentTValueRead.bucket(m,y.getId())),"same canonical buckets");require(first.extracted()==1,"root extracted exactly once");first.close();boolean closed=false;try{first.values();}catch(AssertionError e){closed=e.getMessage().contains("closed or foreign");}require(closed,"closed frame rejects reuse");require(before.equals(ResidentTValueRead.fingerprint(m)),"frame reading pure");
  // Mutation happens only after close; a fresh frame must see native routing changes.
  m.getTValues().add(x,a);try(ObservationLayers next=new ObservationLayers(m)){require(next.bucket(x.getId()).size()==2,"fresh frame sees native addition");require(next.extracted()==1,"fresh independent extraction");}
  Mind child=new Mind(m);child.getTValues().forEach(x,o->true);try(ObservationLayers f=new ObservationLayers(child)){require(f.bucket(x.getId()).equals(ResidentTValueRead.bucket(child,x.getId())),"same child-to-parent ordered bucket");require(f.extracted()==2,"child and parent each extracted once");f.bucket(y.getId());require(f.extracted()==2,"second variable reuses ancestry layers");}m.release(child);
  System.out.println("OBSERVATION_LAYERS_OK checks="+checks);
 }
}
