package org.kanger;
import java.nio.file.Files;
import org.kanger.factory.TValueFactory;
import org.kanger.units.*;
import org.kanger.interfaces.ITerm;
public final class SingleTValueLookupRunner {
    static int checks;
    static void check(boolean b) { if (!b) throw new AssertionError("check " + checks); checks++; }
    static final class CustomMind extends Mind {
        int reads;
        CustomMind() throws Exception { super(new User()); }
        @Override public TValueFactory getTValues() { reads++; return super.getTValues(); }
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("single-lookup-").toString());
        Mind m = new Mind(new User());
        ITerm a=m.getTerms().add("a"), b=m.getTerms().add("b");
        TVariable v=m.getTVars().createTVar(new Rule(m),a);
        check(v.getValue()==null); check(v.getCurrent()==null);
        TValue av=m.getTValues().add(v,a), bv=m.getTValues().add(v,b);
        v.setCurrent(av); check(v.getCurrent()==av); check(v.getValue()==a);
        v.setCurrent(bv); check(v.getCurrent()==bv); check(v.getValue()==b);
        Mind child=new Mind(m);
        try {
            v.setMind(child); v.setCurrent(av); check(v.getValue()==a);
            v.setMind(m); check(v.getValue()==b);
            v.setMind(child); check(v.getCurrent()==av);
        } finally { v.setMind(m); m.release(child); }
        v.setCurrent(null); check(v.getValue()==null); check(v.getCurrent()==null);
        CustomMind custom=new CustomMind();
        ITerm c=custom.getTerms().add("c");
        TVariable cv=custom.getTVars().createTVar(new Rule(custom),c);
        cv.setCurrent(custom.getTValues().add(cv,c));
        custom.reads=0; check(cv.getValue()==c); check(custom.reads==2);
        custom.reads=0; check(cv.getCurrent()!=null); check(custom.reads==2);
        System.out.println("SINGLE_LOOKUP_BOUNDARIES_OK checks="+checks);
    }
}
