package org.kanger;
import java.nio.file.Files;
import java.lang.reflect.Field;
import java.util.*;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.*;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.*;
import org.kanger.units.*;

/** Compare the unchanged public enumeration oracle with Domain binding. */
public final class LazyBindingVariablesRunner {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private static final class Variable extends TVariable {
        final Fixture f;final int n;
        Variable(Fixture f,int n){this.f=f;this.n=n;setId(n);}
        @Override public boolean equals(Object other){f.trace.add("equals"+n);if(f.mode==10)throw f.failure;return super.equals(other);}
        @Override public TVariable setMind(Mind mind){f.trace.add("bind"+n);if(f.mode==11 && n==1)throw f.failure;return this;}
    }
    private static final class ObservedArgument extends Argument {
        final Fixture f;final int n;int deletions,reads;
        ObservedArgument(Fixture f,int n,IUnit object){super(object);this.f=f;this.n=n;}
        @Override public ArgumentType getType(){f.trace.add("type"+n);return super.getType();}
        @Override public boolean isDeleted(IMind mind){f.trace.add("deleted"+n+":"+(++deletions));return f.mode==5 || (f.mode==6 && deletions==2);}
        @Override public IUnit getObject(IMind mind)throws Exception {
            f.trace.add("object"+n+":"+(++reads));
            if((f.mode==8 && reads==1)||(f.mode==9 && reads==2))throw f.failure;
            if(f.mode==7 && reads==2)return f.v2;
            if(f.mode==17)return null;
            if(f.mode==18 && reads==2)return new Term();
            if(f.mode==19 && reads==1)f.args.add(new Argument(f.v2));
            return super.getObject(mind);
        }
    }
    private static final class CustomArguments extends ArgumentsList {
        final Fixture f;final boolean nested;
        CustomArguments(Fixture f,boolean nested){this.f=f;this.nested=nested;}
        @Override public List<TVariable> getTVariables(IMind mind){
            f.trace.add(nested?"nested.custom":"outer.custom");
            if(f.mode==15)throw f.failure;
            if(f.mode==16 || f.mode==20)return null;
            return new ObservedList(f);
        }
    }
    private static final class ObservedList extends ArrayList<TVariable> {
        final Fixture f;
        ObservedList(Fixture f){this.f=f;add(f.v1);add(f.v2);}
        @Override public Iterator<TVariable> iterator(){f.trace.add("custom.iterator");final Iterator<TVariable> raw=super.iterator();return new Iterator<TVariable>(){
            public boolean hasNext(){f.trace.add("custom.hasNext");return raw.hasNext();}
            public TVariable next(){f.trace.add("custom.next");return raw.next();}
            public void remove(){raw.remove();}
        };}
    }
    private static final class NestedFunction extends Function {
        final Fixture f;final ArgumentsList args;
        NestedFunction(Fixture f,ArgumentsList args){this.f=f;this.args=args;}
        @Override public ArgumentsList getArguments(){f.trace.add("function.arguments");return args;}
    }
    private static final class Fixture {
        final int mode;final List<String> trace=new ArrayList<>();
        final IllegalStateException failure=new IllegalStateException("original");
        final Variable v1=new Variable(this,1),v2=new Variable(this,2);
        ArgumentsList args;Domain domain=new Domain();
        Fixture(int mode)throws Exception {
            this.mode=mode;args=(mode>=14 && mode<=16)?new CustomArguments(this,false):new ArgumentsList();
            if(mode==1)args.add(new ObservedArgument(this,1,new Term()));
            else if(mode==12 || mode==13 || mode==20){
                ArgumentsList inner=(mode==13 || mode==20)?new CustomArguments(this,true):new ArgumentsList();
                if(mode==12){inner.add(new ObservedArgument(this,2,v1));inner.add(new ObservedArgument(this,3,v2));}
                args.add(new ObservedArgument(this,1,new NestedFunction(this,inner)));
                args.add(new ObservedArgument(this,4,v1));
            }else if(mode!=0 && mode!=21 && !(mode>=14 && mode<=16)){
                args.add(new ObservedArgument(this,1,v1));
                if(mode==3 || mode==4 || mode==10 || mode==11)args.add(new ObservedArgument(this,2,mode==4?v1:v2));
            }
            if(mode==21)args=null;
            Field field=Solve.class.getDeclaredField("arguments");field.setAccessible(true);field.set(domain,args);trace.clear();
        }
    }
    private static String run(Fixture f,boolean reference)throws Exception {
        try{
            if(reference){
                Field field=Domain.class.getDeclaredField("mind");field.setAccessible(true);field.set(f.domain,null);
                for(TVariable t:f.args.getTVariables(null))t.setMind(null);
            }else f.domain.setMind(null);
            return "ok";
        }catch(Throwable e){
            if(e instanceof IllegalStateException){check(e==f.failure,"original exception identity");return "original";}
            if(e instanceof NullPointerException || e instanceof ClassCastException || e instanceof ConcurrentModificationException)return e.getClass().getName();
            throw e;
        }
    }
    public static void main(String[] ignored)throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("lazy-binding-").toString());
        for(int mode=0;mode<=21;mode++){
            Fixture reference=new Fixture(mode),candidate=new Fixture(mode);
            String expected=run(reference,true),actual=run(candidate,false);
            check(expected.equals(actual),"outcome scenario "+mode);
            check(reference.trace.equals(candidate.trace),"trace scenario "+mode+" "+reference.trace+" != "+candidate.trace);
        }
        ArgumentsList empty=new ArgumentsList();List<TVariable> a=empty.getTVariables(null),b=empty.getTVariables(null);
        check(a!=b,"fresh public empty snapshot");a.add(null);check(b.isEmpty(),"owned mutable public snapshot");
        Mind root=new Mind(new User()),child=new Mind(root);ITerm first=root.getTerms().add("a"),second=root.getTerms().add("b");
        TVariable v=root.getTVars().createTVar(new Rule(root),first);
        root.getTValues().set(v,new TValue(v,first));child.getTValues().set(v,new TValue(v,second));
        Domain d=new Domain(root);d.add(new Argument(v));d.setMind(root);v.setMind(child);d.setMind(root);
        check(v.getValue()==first,"reselect shared builtin variable");
        root.getDeleted().computeIfAbsent(v.getUnitType(),key->new HashSet<>()).add(v.getId());
        v.setMind(child);d.setMind(root);check(v.getMind()==child,"deleted variable remains unselected");
        System.out.println("LAZY_BINDING_VARIABLES_OK scenarios=22 checks="+checks);
    }
}
