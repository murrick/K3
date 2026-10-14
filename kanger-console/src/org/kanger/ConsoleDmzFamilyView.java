/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import org.kanger.interfaces.DmzRestrictionFamily;
import org.kanger.interfaces.DmzSourcesQuery;

/** Detached family display; no acquisition, inference or saved single-result replacement. */
final class ConsoleDmzFamilyView {
    private ConsoleDmzFamilyView() { }
    static void show(DmzRestrictionFamily.Result family) {
        if(family==null)throw new IllegalStateException("No saved DMZ family");
        System.out.println("DMZ family: "+(family.exhausted?"EXHAUSTED qualified choices":"PARTIAL")
                +"; observations="+family.observations.size()+"; branches="+family.leaves.size()
                +"; uncovered="+family.uncovered.size()+"; pending groups="+family.pending.size());
        int number=0;
        for(DmzSourcesQuery.Result leaf:family.leaves) {
            System.out.print("  Branch "+(++number)+": choices="+leaf.restrictionProofs.size()+"; native opinions=");
            for(DmzSourcesQuery.Opinion opinion:leaf.opinions)System.out.print(opinion.answer.value==null?"UNKNOWN ":opinion.answer.value?"TRUE ":"FALSE ");
            System.out.println();
        }
        number=0;
        for(DmzRestrictionFamily.Pending pending:family.pending) {
            System.out.println("Pending group "+(++number)+": "+pending.reason+"; first unexplored choice="+pending.nextChoice);
            System.out.println("  Parent selected restrictions:");
            for(DmzSourcesQuery.Proof proof:pending.parent.restrictionProofs)ConsoleDmzView.tree(pending.parent,proof,"    ");
            System.out.println("  Current conflict evidence:");
            for(DmzSourcesQuery.RestrictionConflict pair:pending.parent.restrictionOutcome.availableConflicts) {
                ConsoleDmzView.tree(pending.parent,pair.positive,"    ");ConsoleDmzView.tree(pending.parent,pair.negative,"    ");
            }
            for(int i=pending.nextChoice-1;i<pending.parent.restrictionOutcome.availableChoices.size();++i) {
                System.out.println("  Unexplored historical choice "+(i+1)+":");
                ConsoleDmzView.tree(pending.parent,pending.parent.restrictionOutcome.availableChoices.get(i),"    ");
            }
        }
        for(DmzSourcesQuery.Result stopped:family.uncovered) {
            System.out.println("Uncovered branch: no completed opinion");ConsoleDmzView.explain(stopped);
        }
        System.out.println("Use ctx dmz branch N for saved proofs. Equal opinions may have different restriction histories.");
        System.out.println("Historical family only; qualified exhaustion is not all-theories completeness.");
    }
    static void branch(DmzRestrictionFamily.Result family,long number) {
        if(family==null)throw new IllegalStateException("No saved DMZ family");
        if(number<1 || number>family.leaves.size())throw new IllegalArgumentException("No saved family branch "+number);
        DmzSourcesQuery.Result leaf=family.leaves.get((int)number-1);
        System.out.println("DMZ family branch "+number+" (historical)");ConsoleDmzView.explain(leaf);
        for(int i=1;i<=leaf.viewOpinions.size();++i)ConsoleDmzView.tree(leaf,i);
    }
}
