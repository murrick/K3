/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import org.kanger.ContextDependentGroundCacheInspection.*;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

/** Local cache implication coverage must never manufacture primary assumptions from generated rows. */
public final class ContextDependentGroundCacheInspectionRunner {
    private static int checks;
    private static void require(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static Mind memory()throws Exception{User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);return m;}
    private static Rule raw(Mind m,String text)throws Exception{return (Rule)m.compileLine(text,false,new LinkedList<ITerm>());}
    private static ContextProductionInventory inventory(Mind m)throws Exception{return ContextProductionInventory.inspect(m,new UUID(0,6151),1,true,Collections.emptyList(),1000);}
    private static Limits limits(){return new Limits(1000,1000,16,4000000,4000000);}
    private static Result inspect(Mind m)throws Exception{return ContextDependentGroundCacheInspection.inspect(m,inventory(m),limits());}
    private static void noPrefix(Result result,String why){require(result.status==Status.INCOMPLETE&&result.covered.isEmpty()&&result.unusedUnsupportedPrimary.isEmpty()&&result.facts==0&&result.rounds==0,why+": "+result.reason);}
    private static Mind nativeMind(String text)throws Exception{Mind m=memory();require(m.compile(text,null,false),"native fixture");return m;}
    private static void covered(Mind m,String why)throws Exception{
        ContextProductionInventory inventory=inventory(m);require(!inventory.generated.isEmpty(),why+": actual native cache exists");
        String before=DmzObservationStateFingerprint.capture(m);int terms=m.getTerms().size();Result result=inspect(m);
        require(result.status==Status.VERIFIED&&result.covered.size()==inventory.generated.size(),why+": "+result.reason);
        Set<Long> primary=new HashSet<>();for(ContextProductionInventory.Row row:inventory.primary)primary.add(row.nativeRule);
        for(Covered row:result.covered){
            require(inventory.generated.stream().anyMatch(g->g.nativeRule==row.nativeRule),"ledger covers an actual generated row");
            require(!row.proof.sources.isEmpty()&&row.proof.sources.stream().allMatch(s->primary.contains(s.rule)&&s.view.equals("inventory:"+inventory.fingerprint)),"every proof source is an actual primary, never a cache assumption");
        }
        require(terms==m.getTerms().size()&&before.equals(DmzObservationStateFingerprint.capture(m)),"implication inspection is read-only");
        require(!inventory.dependentPrimaryView(),"implication coverage does not silently authorize model projection or admission");
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",Files.createTempDirectory("dependent-ground-cache-").toString());
        Mind chain=nativeMind("!seed(1); !@x seed(x)->middle(x); !@x middle(x)->tail(x);");covered(chain,"two-link native cache");
        Mind join=nativeMind("!left(1); !right(1); !@x left(x)&&right(x)->hit(x);");covered(join,"whole binary donor join");
        Mind negative=nativeMind("!right(1); !~p(1); !@x left(x)&&right(x)->p(x);");covered(negative,"signed negative residual");
        Mind wide=nativeMind("!seed(1,2,3,4,5,6,7); !@a @b @c @d @e @f @g seed(a,b,c,d,e,f,g)->hit(g,f,e,d,c,b,a);");covered(wide,"seven variable permutation");
        Mind typed=nativeMind("!seed(\"1\",2); !@x @y seed(x,y)->hit(y,x);");covered(typed,"typed fixed values and permutation");
        Mind mixed=nativeMind("!@a $u @b rel(a,u,b); !seed(1); !@x seed(x)->hit(x);");covered(mixed,"unrelated dependent root is not reinterpreted as a universal proof seed");
        require(!inspect(mixed).unusedUnsupportedPrimary.isEmpty(),"unsupported existential original remains explicitly outside this local proof subset");
        Mind forged=memory();raw(forged,"!@x seed(x)->hit(x);");Rule fake=raw(forged,"!hit(1);");fake.setGenerated(true);noPrefix(inspect(forged),"generated presence without any primary derivation is not proof");
        Mind cyclic=memory();raw(cyclic,"!@x a(x)->b(x);");raw(cyclic,"!@x b(x)->a(x);");raw(cyclic,"!a(1);").setGenerated(true);raw(cyclic,"!b(1);").setGenerated(true);noPrefix(inspect(cyclic),"mutually supporting cache rows cannot seed a proof cycle");
        Mind wrongType=memory();raw(wrongType,"!seed(1);");raw(wrongType,"!@x seed(x)->hit(x);");raw(wrongType,"!hit(\"1\");").setGenerated(true);noPrefix(inspect(wrongType),"numeric and string values are not interchangeable");
        Mind symbolic=memory();raw(symbolic,"!$u p(u);").setGenerated(true);noPrefix(inspect(symbolic),"symbolic generated witness is outside ground scope");
        ContextProductionInventory snapshot=inventory(chain);
        noPrefix(ContextDependentGroundCacheInspection.inspect(chain,snapshot,new Limits(1000,1000,1,4000000,4000000)),"coverage round cutoff exposes no partial proof list");
        noPrefix(ContextDependentGroundCacheInspection.inspect(chain,snapshot,new Limits(1000,1000,16,4000000,1)),"independent audit budget cannot be bypassed");
        raw(chain,"!changed(1);");boolean stale=false;try{ContextDependentGroundCacheInspection.inspect(chain,snapshot,limits());}catch(IllegalStateException expected){stale=true;}require(stale,"changed live inventory invalidates the proof basis");
        boolean interrupted=false;Thread.currentThread().interrupt();try{inspect(typed);}catch(InterruptedException expected){interrupted=Thread.currentThread().isInterrupted();}finally{Thread.interrupted();}require(interrupted,"interruption preserves the flag and produces no result");
        System.out.println("CONTEXT_DEPENDENT_GROUND_CACHE_INSPECTION_PASS checks="+checks);
    }
}
