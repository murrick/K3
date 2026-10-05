/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.util.HashSet;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.units.Term;

/** Nearest-layer and restoration precedence, including exposed map mutation. */
public final class DeletionVisibilityWitness {
    private static int checks;
    private static void expect(Mind mind, IUnit unit, boolean expected) {
        boolean actual = mind.isUnitDeleted(unit);
        if (actual != expected) throw new AssertionError("check " + checks);
        checks++;
    }
    private static HashSet<Long> ids(IUnit unit) {
        HashSet<Long> result = new HashSet<>(); result.add(unit.getId()); return result;
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home", Files.createTempDirectory("empty-deletion-").toString());
        Mind root = new Mind(new User());
        Term term = (Term) root.getTerms().add("kept");
        Term other = (Term) root.getTerms().add("other");
        UnitType type = term.getUnitType();
        Mind child = new Mind(root), grandchild = new Mind(child);
        try {
            expect(root,term,false); expect(child,term,false); expect(grandchild,term,false);
            root.getDeleted().put(type, ids(term));
            expect(root,term,true); expect(child,term,true); expect(grandchild,term,true);
            expect(grandchild,other,false);
            child.getRestored().put(type, ids(term));
            expect(root,term,true); expect(child,term,false); expect(grandchild,term,false);
            child.getDeleted().put(type, ids(term));
            expect(child,term,false); expect(grandchild,term,false);
            grandchild.getDeleted().put(type,ids(term));
            expect(grandchild,term,true);
            grandchild.getRestored().put(type,ids(term));
            expect(grandchild,term,false);
            grandchild.getRestored().clear(); grandchild.getDeleted().clear();
            child.getRestored().get(type).remove(term.getId());
            expect(grandchild,term,true);
            child.getDeleted().clear();
            expect(grandchild,term,true);
            root.getRestored().put(type,ids(term));
            expect(root,term,false); expect(grandchild,term,false);
            child.getDeleted().put(type,ids(term));
            expect(child,term,true); expect(grandchild,term,true);
            root.getDeleted().clear(); root.getRestored().clear();
            child.getDeleted().clear(); child.getRestored().clear();
            expect(grandchild,term,false);
            // Nonempty maps with empty or null sets must still use normal lookup.
            child.getDeleted().put(type,new HashSet<Long>());
            expect(grandchild,term,false);
            child.getRestored().put(type,null);
            expect(grandchild,term,false);
            root.setUnitDeleted(term,true);
            expect(grandchild,term,true);
            child.setUnitDeleted(term,false);
            expect(root,term,true); expect(child,term,false); expect(grandchild,term,false);
        } finally { child.release(grandchild); root.release(child); }
        System.out.println("EMPTY_DELETION_BOUNDARIES_OK checks=" + checks);
    }
}
