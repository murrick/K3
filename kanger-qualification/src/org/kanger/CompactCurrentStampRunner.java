package org.kanger;
import java.nio.file.Files;
import java.util.*;
import org.kanger.exception.ParametersIncompleteException;
import org.kanger.interfaces.*;
import org.kanger.primitives.*;
import org.kanger.units.*;

/** Independent fresh-fixture reference/result/trace/exception comparison. */
public final class CompactCurrentStampRunner {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static class ObservedTerm extends Term {
        final Fixture f;final int n;boolean empty;
        ObservedTerm(Fixture f,int n){this.f=f;this.n=n;setId(100+n);}
        @Override public boolean isEmpty(){f.trace.add("term.empty"+n);return empty;}
        @Override public long getId(){f.trace.add("term.id"+n);return super.getId();}
    }
    private static class Variable extends TVariable {
        final Fixture f;final int n;boolean empty,deleted;Runnable action;
        Variable(Fixture f,int n){this.f=f;this.n=n;setId(n);}
        @Override public boolean isDeleted(IMind mind){f.trace.add("deleted"+n);return deleted;}
        @Override public boolean isEmpty(){f.trace.add("variable.empty"+n);return empty;}
        @Override public ITerm getValue() throws Exception {
            f.trace.add("value"+n);
            if(f.mode==8 && n==2)throw f.failure;
            if(action!=null){Runnable one=action;action=null;one.run();}
            return f.mode==12 && n==1?null:f.terms[n-1];
        }
        @Override public String toString(){f.trace.add("variable.text"+n);return "v"+n;}
    }
    private static class Target extends ArrayList<ITerm> {
        final Fixture f;
        Target(Fixture f){this.f=f;}
        @Override public int size(){f.trace.add("target.size");if(f.mode==9)throw f.failure;return super.size();}
        @Override public ITerm get(int i){f.trace.add("target.get"+i);if(f.mode==10)throw f.failure;
            if(f.mode==15 && i==0)f.terms[1].setId(999);return super.get(i);}
    }
    private static class CustomStamp extends ArgumentsList {
        final Fixture f;CustomStamp(Fixture f){this.f=f;}
        @Override public List<ITerm> getStamp(Mind mind){f.trace.add("custom.stamp");return Arrays.<ITerm>asList(f.terms);}
    }
    private static class CustomEnumeration extends ArgumentsList {
        final Fixture f;CustomEnumeration(Fixture f){this.f=f;}
        @Override public List<TVariable> getTVariables(IMind mind) throws Exception {f.trace.add("custom.variables");throw f.incomplete;}
    }
    private static final class Fixture {
        final int mode;final List<String> trace=new ArrayList<>();
        final IllegalStateException failure=new IllegalStateException("original");
        final ParametersIncompleteException incomplete=new ParametersIncompleteException("original incomplete");
        final ObservedTerm[] terms={new ObservedTerm(this,1),new ObservedTerm(this,2)};
        ArgumentsList arguments;List<ITerm> target;
        Fixture(int mode){this.mode=mode;
            arguments=mode==11?new CustomStamp(this):mode==13?new CustomEnumeration(this):new ArgumentsList();
            Target list=new Target(this);target=list;
            int count=mode==0?0:mode==1||mode==2?1:2;
            for(int i=0;i<count;i++){
                Variable v=new Variable(this,i+1);
                if(mode==7 && i==0)v.empty=true;
                if(mode==16 && i==0)v.deleted=true;
                if(mode==14 && i==1)v.action=()->terms[0].setId(999);
                if(mode==17 && i==0)v.action=()->arguments.remove(1);
                arguments.add(new Argument(v));
                Term expected=new Term();expected.setId(101+i);list.add(expected);
            }
            if(mode==2||mode==5){Term other=new Term();other.setId(900);list.set(0,other);}
            if(mode==3)list.remove(1);
            if(mode==6)terms[0].empty=true;
            if(mode==18)target=null;
            trace.clear();
        }
    }
    private static boolean oracle(ArgumentsList args,Mind mind,List<ITerm> list)throws Exception {
        try {
            List<ITerm> curr=args.getStamp(mind);
            if(curr.size()==list.size()){
                for(int i=0;i<curr.size();i++){
                    if(curr.get(i).isEmpty()||curr.get(i).getId()!=list.get(i).getId())return false;
                }
                return true;
            }else return false;
        }catch(ParametersIncompleteException e){return false;}
    }
    private static String run(Fixture f,boolean reference)throws Exception {
        try{return Boolean.toString(reference?oracle(f.arguments,null,f.target):f.arguments.equalsStamp(null,f.target));}
        catch(Throwable e){
            if(e instanceof IllegalStateException){check(e==f.failure,"exception identity");return "original-failure";}
            if(e instanceof NullPointerException)return "null-failure";
            throw e;
        }
    }
    public static void main(String[] ignored)throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("compact-stamp-").toString());
        for(int mode=0;mode<=18;mode++){
            Fixture reference=new Fixture(mode),candidate=new Fixture(mode);
            String expected=run(reference,true),actual=run(candidate,false);
            check(expected.equals(actual),"result scenario "+mode);
            check(reference.trace.equals(candidate.trace),"trace scenario "+mode+" "+reference.trace+" != "+candidate.trace);
        }
        Mind mind=new Mind(new User());ITerm a=mind.getTerms().add("a");
        TVariable v=mind.getTVars().createTVar(new Rule(mind),a);v.setCurrent(new TValue(v,a));
        ArgumentsList args=new ArgumentsList();args.add(new Argument(v));
        check(args.equalsStamp(mind,Collections.singletonList(a)),"builtin binding");
        List<ITerm> stamp=args.getStamp(mind);stamp.clear();
        check(args.getStamp(mind).size()==1,"public stamp owned mutable snapshot");
        mind.getDeleted().computeIfAbsent(v.getUnitType(),key->new HashSet<>()).add(v.getId());
        check(args.equalsStamp(mind,Collections.<ITerm>emptyList()),"builtin deletion");
        System.out.println("COMPACT_CURRENT_STAMP_OK scenarios=19 checks="+checks);
    }
}
