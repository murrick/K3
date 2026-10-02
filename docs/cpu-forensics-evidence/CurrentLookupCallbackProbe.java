package org.kanger;
import java.nio.file.Files;
import org.kanger.factory.TValueFactory;
import org.kanger.units.*;
/** Probe of archived directCurrentLookup prototype, not integrated code. */
public class CurrentLookupCallbackProbe {
    static final class ObservedVariable extends TVariable {
        int hashCalls;
        @Override public int hashCode() { hashCalls++; return super.hashCode(); }
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home",Files.createTempDirectory("current-lookup-callback-").toString());
        Mind mind=new Mind(new User()); TValueFactory factory=mind.getTValues();
        ObservedVariable variable=new ObservedVariable();variable.setId(101);
        TValue value=new TValue(variable,new Term());
        factory.getCurrent().put(variable,value);
        variable.hashCalls=0;
        if(factory.get(variable)!=value) throw new AssertionError("value mismatch");
        System.out.println("CUSTOM_VARIABLE_HASH_CALLS "+variable.hashCalls);
        int expected=Boolean.getBoolean("kanger.experiment.directCurrentLookup")?1:2;
        if(variable.hashCalls!=expected) throw new AssertionError("unexpected count");
    }
}
