/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
import java.util.*;
import org.kanger.ContextProductionCapture.Clause;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.units.*;

/** Whole single-clause ordinary Horn rows; existential dependency roots remain separate positive units. */
final class NativeDependentProductionRows {
    static boolean unit(Rule rule)throws Exception{return rule!=null&&rule.getTree().size()==1&&rule.getTree().get(0).size()==1&&rule.getDomain().isAntc();}
    static Clause ordinary(Rule rule,Mind mind,Runnable work)throws Exception {
        work.run();if(rule==null||rule.isQuery()||rule.isGenerated()||rule.isDeleted(mind)||rule.getTree().size()!=1||rule.getTree().get(0).isEmpty())throw new IllegalStateException("Whole ordinary single Horn clause required");
        int heads=0;
        for(Domain domain:rule.getTree().get(0)) {
            work.run();if(domain.isSystem(mind)||domain.isCalculated(mind))throw new IllegalStateException("Ordinary Horn predicate required");if(domain.isAntc())heads++;
            for(IArgument argument:domain.getArguments()) {
                work.run();if(argument.getType()==ArgumentType.TVARIABLE){if(((TVariable)argument.getObject(mind)).getRuleId()!=rule.getId())throw new IllegalStateException("Different Horn variable owner");}
                else if(argument.getType()!=ArgumentType.TERM||!SemanticTermSnapshot.isOrdinaryValue(argument.getValue(mind)))throw new IllegalStateException("Ordinary Horn slots required");
            }
        }
        if(heads>1)throw new IllegalStateException("Single positive Horn head required");Clause clause=Clause.shapeOf(rule.getTree().get(0),mind);if(clause==null)throw new IllegalStateException("Unsupported Horn clause");return clause;
    }
}
