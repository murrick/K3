package org.kanger;
import org.kanger.units.*;
/** Real public metadata mutation demonstrates a detected unsupported gap. */
public final class TValueDirtyGapSafetyRunner {
    public static void main(String[] args)throws Exception{
        TValueDirtyJournal.begin();Mind m=new Mind(new User());
        Rule owner=(Rule)m.compileLine("!@x gap_owner(x);",false,null);
        TVariable v=owner.getTree().get(0).get(0).getArguments().getTVariables(m).iterator().next();
        TValue a=m.getTValues().add(v,m.getTerms().add("a"));TValueDirtyJournal.observe(m,"gap-baseline");
        Term b=(Term)m.getTerms().add("b");a.setValue(b);
        // The public metadata setter has deliberately not been instrumented.
        // Verification must reject its stale projection, never repair it.
        TValueDirtyJournal.observe(m,"untracked-public-metadata-write");boolean detected=false;
        try{TValueDirtyJournal.finish();}catch(AssertionError e){detected=e.getMessage().contains("dirty projection mismatch")&&e.getMessage().contains("untracked-public-metadata-write");}
        if(!detected||a.getValueId()!=b.getId()||m.getTValues().get(a.getId())!=a)throw new AssertionError("gap detection or unchanged native state");
        // A failed session must have relinquished its ThreadLocal ownership.
        TValueDirtyJournal.begin();TValueDirtyJournal.finish();
        System.out.println("TVALUE_DIRTY_GAP_OK unsupported_metadata_write=detected native_state=preserved session=closed");
    }
}
