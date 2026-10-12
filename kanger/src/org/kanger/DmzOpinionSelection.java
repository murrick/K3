/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.interfaces.DmzSourcesQuery;
import org.kanger.interfaces.DmzSourcePlan;
import org.kanger.exception.CommandErrorException;

/** Source filters refer solely to identities captured in the saved result. */
public final class DmzOpinionSelection {
    private DmzOpinionSelection() { }
    public static List<Integer> select(DmzSourcesQuery.Result result,String locator,Long number) throws Exception {
        Set<UUID> identities=new HashSet<UUID>();
        List<DmzSourcePlan.Source> sources=new ArrayList<DmzSourcePlan.Source>();
        sources.add(result.acquisition.authority);sources.addAll(result.acquisition.authorityComponents);sources.addAll(result.acquisition.external);
        for(DmzSourcePlan.Source source:sources)
            if(locator==null || source.locator.equals(locator))identities.add(source.revision.getContextId());
        if(locator!=null && identities.isEmpty())throw new CommandErrorException("No saved source "+locator);
        List<Integer> selected=new ArrayList<Integer>();
        for(int i=0;i<result.viewOpinions.size();i++) {
            DmzSourcesQuery.ViewOpinion opinion=result.viewOpinions.get(i);
            if(locator==null || matches(opinion.proofs,identities))selected.add(i+1);
        }
        if(number!=null) {
            if(number<1 || number>selected.size())throw new CommandErrorException("No saved opinion "+number+(locator==null?"":" from "+locator));
            return Collections.singletonList(selected.get(number.intValue()-1));
        }
        return Collections.unmodifiableList(selected);
    }
    private static boolean matches(List<DmzSourcesQuery.Proof> proofs,Set<UUID> identities) {
        for(DmzSourcesQuery.Proof proof:proofs)
            if(identities.contains(proof.source.revision.getContextId()) || matches(proof.premises,identities))return true;
        return false;
    }
}
