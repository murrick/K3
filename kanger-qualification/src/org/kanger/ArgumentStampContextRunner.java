/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.kanger.interfaces.ITerm;
import org.kanger.primitives.Argument;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;

/** Stamp restoration must select the explicit materialization context for cached variables. */
public final class ArgumentStampContextRunner {
    static int checks;
    static void require(boolean value,String why){++checks;if(!value)throw new AssertionError(why);}
    static Thread reader(TVariable variable,ArgumentsList arguments,Mind parent,Mind view,List<ITerm> stamp,ITerm expected,
                         CountDownLatch start,AtomicReference<Throwable> failure){
        return new Thread(()->{try{start.await();for(int i=0;i<2000;i++){
            variable.setMind(parent);arguments.applyStamp(view,stamp);
            if(view.getTValues().get(variable).getValue(view)!=expected||arguments.getStamp(view).get(0)!=expected)
                throw new AssertionError("stamp restoration selected another execution view for a cached variable");
        }}catch(Throwable caught){failure.compareAndSet(null,caught);}});
    }
    public static void main(String[] args){try{
        System.setProperty("user.home",Files.createTempDirectory("argument-stamp-context-").toString());
        User user=new User();Mind parent=new Mind(user);user.setCurrentMind(parent);
        Rule owner=new Rule(parent);parent.getRules().register(owner);
        TVariable variable=parent.getTVars().createTVar(owner,parent.getTerms().add("x"));
        ITerm one=parent.getTerms().add(1.0),two=parent.getTerms().add(2.0),nine=parent.getTerms().add(9.0);
        variable.setValue(one);Argument operand=new Argument(variable);ArgumentsList arguments=new ArgumentsList();arguments.add(operand);
        require(operand.getValue(parent)==one,"warm cached parent operand");
        Mind left=Mind.ephemeralChild(parent),right=Mind.ephemeralChild(parent);
        try{
            variable.setMind(left);variable.setValue(nine);variable.setMind(right);variable.setValue(two);variable.setMind(parent);
            require(parent.getTValues().find(variable,nine)==null&&left.getTValues().find(variable,nine)!=null,"canonical nine binding exists only in the correct child view");
            variable.setMind(left);List<ITerm> stamp=arguments.getStamp(left);require(stamp.size()==1&&stamp.get(0)==nine,"capture the proven child stamp");
            variable.setValue(two);variable.setMind(parent);require(variable.getValue()==one,"stale execution view currently selects parent one");
            arguments.applyStamp(left,stamp);
            require(left.getTValues().get(variable).getValue(left)==nine,"restore stamped nine in child instead of leaving unrelated current two");
            require(variable.getMind()==left,"stamp selects the materializing Mind for the cached variable");
            require(operand.getObject(left)==variable,"stamp restoration preserves canonical variable identity");
            require(right.getTValues().get(variable).getValue(right)==two&&parent.getTValues().get(variable).getValue(parent)==one,"stamp does not overwrite sibling or parent current projections");
            List<ITerm> other=Arrays.asList(two);variable.setMind(parent);arguments.applyStamp(right,other);
            require(right.getTValues().get(variable).getValue(right)==two&&left.getTValues().get(variable).getValue(left)==nine,"same shared operand restores different sibling stamps independently");
            CountDownLatch start=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread a=reader(variable,arguments,parent,left,stamp,nine,start,failure),b=reader(variable,arguments,parent,right,other,two,start,failure);a.start();b.start();start.countDown();a.join();b.join();
            require(failure.get()==null,"thread-local sibling stamp restores remain isolated: "+failure.get());
            require(parent.getTValues().get(variable).getValue(parent)==one,"concurrent restores preserve parent projection");
        }finally{variable.setMind(parent);parent.discardEphemeral(right);parent.discardEphemeral(left);}
        require(operand.getValue(parent)==null,"root quiescence clears current projections rather than retaining a released child binding");
        require(parent.getTValues().find(variable,nine)==null,"discarded child nine binding never becomes parent canonical state");
        System.out.println("ARGUMENT_STAMP_CONTEXT_PASS checks="+checks+" concurrentReads=8000");System.exit(0);
    }catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
}
