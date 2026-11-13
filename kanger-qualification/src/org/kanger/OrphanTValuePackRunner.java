/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import org.kanger.interfaces.ITerm;
import org.kanger.units.*;

/** Packing hydrated orphan substitutions must clean both registry and current projection. */
public final class OrphanTValuePackRunner {
    private static int checks;
    private static void need(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("orphan-tvalue-").toString());User u=new User();Mind m=new Mind(u);u.setCurrentMind(m);need(m.compile("!@x p(x)->q(x); !kept(1);",null,false),"live variable and term fixture");
        TVariable variable=null;for(Object item:m.getTVars()){variable=(TVariable)item;break;}need(variable!=null,"actual live variable");ITerm one=m.getTerms().add(1.0),unused=m.getTerms().add(999.0);TValue kept=m.getTValues().add(variable,one),orphan=m.getTValues().add(variable,unused);long orphanId=orphan.getId(),unusedId=unused.getId();
        kept.setDeleted(true,m);need(m.getTValues().add(variable,one)==kept&&!kept.isDeleted(m),"logical resurrection preserves existing canonical identity");m.getTValues().set(variable,orphan);
        ((Term)unused).setDeleted(true,m);m.getTerms().pack();need(m.getTerms().get(unusedId)==null,"referenced term has actually been physically removed");orphan.setPersistentReferences(unusedId,variable.getId());need(orphan.getValue(m)==null,"cold-style hydration really resolves no term");
        m.getTValues().pack();need(m.getTValues().get(orphanId)==null,"orphan registry row physically removed");need(!m.getTValues().getCurrent().containsKey(variable),"stale current projection removed with orphan");need(m.getTValues().find(variable,one)==kept&&kept.getValue(m)!=null,"active canonical substitution survives cleanup");need(m.getTValues().getRoot(variable)==kept,"variable lookup no longer reaches removed substitution");
        m.getTValues().pack();need(m.getTValues().find(variable,one)==kept,"repeated cleanup preserves live substitution identity");System.out.println("ORPHAN_TVALUE_PACK_PASS checks="+checks);
    }
}
