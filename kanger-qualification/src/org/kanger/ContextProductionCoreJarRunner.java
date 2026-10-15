/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;

import java.util.Collections;

/** Public runtime facade smoke; also compiled and run against kanger-core.jar alone. */
public final class ContextProductionCoreJarRunner {
    private static int checks;
    private static void require(boolean yes,String why) { ++checks; if (!yes) throw new AssertionError(why); }
    public static void main(String[] args) {
        try {
            System.setProperty("user.home",java.nio.file.Files.createTempDirectory("production-core-jar-").toString());
            User user = new User(); Mind x = new Mind(user); user.setCurrentMind(x);
            require(x.compile("!scalar(1);",null,false),"standalone Core native input");
            ContextProductionInventory inventory = ContextProductionInventory.inspect(x,new java.util.UUID(0,1),1,true,Collections.emptyList(),1000);
            ContextProductionNativeBinding.Result bound = ContextProductionNativeBinding.bind(x,inventory,4000000,100000);
            require(bound.bound,"native binding available without qualification module");
            String text = "!@x scalar(x)->ready(x);";
            ContextProductionCandidate.Evaluation candidate = ContextProductionCandidate.qualify(bound.snapshot,Collections.emptyList(),text,1000,12,16,4000000,100000);
            require(candidate.status == ContextProductionFamilyAssembly.Status.FIXED_POINT,"candidate closure works through production public facade: "+candidate.reason);
            require(candidate.artifact.rebased.size() == 1 && candidate.artifact.family.seedAtoms == 1,"runtime includes complete source mapping and actual seed accounting");
            require(candidate.artifact.candidate.view.equals("inventory:"+candidate.artifact.staged.inventory.fingerprint),"runtime candidate authority is native-bound");
            ContextProductionInventory after = ContextProductionInventory.inspect(x,inventory.context,inventory.revision,true,Collections.emptyList(),1000);
            require(after.fingerprint.equals(inventory.fingerprint),"runtime facade never publishes candidate into original X");
            ContextProductionInputs.Declaration declaration = new ContextProductionInputs.Declaration(candidate.artifact.candidate,text);
            require(declaration.source.equals(candidate.artifact.candidate) && declaration.statement.equals(text),"production DTO retains immutable exact source and assertion");
            System.out.println("CONTEXT_PRODUCTION_CORE_JAR_PASS checks="+checks); System.exit(0);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
}
