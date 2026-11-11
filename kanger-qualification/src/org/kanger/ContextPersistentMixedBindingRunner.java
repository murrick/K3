/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.kanger.storage.dumb2.DB;

/** Factory-backed capture must distinguish unsupported dependencies from lost ownership. */
public final class ContextPersistentMixedBindingRunner {
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("persistent-mixed-binding-");
        System.setProperty("user.home",directory.toString());
        int checks=0;
        for(int pass=0;pass<2;pass++) {
            User user=new User();
            user.setDatabaseDir(directory.toString()+java.io.File.separator);
            DB db=new DB();db.init(user);
            Mind root=new Mind(user);user.setCurrentMind(root);
            Mind mind=(Mind)root.useStorage("Mixed");user.setCurrentMind(mind);
            try {
                if(pass==0 && !mind.compile("!@a $u @b rel(a,u,b);",null,false))
                    throw new AssertionError("Initial source rejected");
                org.kanger.interfaces.internal.IContextFederation.Snapshot view=db.federationSnapshot();
                ContextProductionInventory inventory=ContextProductionInventory.inspect(mind,view.getSourceContextId(),
                        view.getSourceRevision(),true,Collections.emptyList(),1000);
                String state=DmzObservationStateFingerprint.capture(mind);
                ContextProductionNativeBinding.Result result=ContextProductionNativeBinding.bind(mind,inventory,1000000,10000);
                if(result.bound || result.snapshot!=null
                        || !"Mixed quantifier prefixes require dependency-aware qualification".equals(result.reason))
                    throw new AssertionError("Precise supported-boundary refusal required: "+result.reason);
                checks++;
                if(!state.equals(DmzObservationStateFingerprint.capture(mind)) || db.getRevision()!=view.getSourceRevision()
                        || mind.hasPendingTransactions())throw new AssertionError("Binding refusal changed stored source");
                checks++;
            } finally {user.setCurrentMind(mind.closeStorage());}
        }
        System.out.println("CONTEXT_PERSISTENT_MIXED_BINDING_PASS checks="+checks);
    }
}
