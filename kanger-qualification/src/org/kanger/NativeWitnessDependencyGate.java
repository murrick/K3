/* MIT License. Copyright (c) 2026 Dmitry G. Quznetsov. */
package org.kanger;
/** Required mixed-prefix authoring contract; false rejection is not a passing refusal. */
public final class NativeWitnessDependencyGate {
    public static void main(String[] args) throws Exception {
        System.setProperty("user.home",java.nio.file.Files.createTempDirectory("native-dependency-red-").toString());
        String restriction="!@u ~(rel(1,u,1)&&rel(2,u,1));";
        String[] prefixes={"!@a $u @b rel(a,u,b);","!@x $w @y rel(x,w,y);",
                "!$u @a @b rel(a,u,b);","!@a @b $u rel(a,u,b);"};
        int checks=0,missing=0;
        for (int i=0;i<prefixes.length;i++) for (boolean reverse:new boolean[]{false,true}) {
            User user=new User(); Mind mind=new Mind(user); user.setCurrentMind(mind);
            if (!mind.compile(reverse?restriction:prefixes[i],null,false)) throw new AssertionError("Initial row rejected");
            boolean actual=mind.compile(reverse?prefixes[i]:restriction,null,false), expected=i!=2;
            checks++; if(actual!=expected) {
                missing++;
                if (expected) {
                    ContextQualification q=mind.getLastCompileQualification();
                    if (q==null||q.isValid()||q.getCollisions().isEmpty()) throw new AssertionError("False rejection must be reproduced as an actual native conflict, not a resource refusal");
                    System.out.println("ACTUAL_FALSE_CONFLICT witnesses="+q.getCollisions().size());
                }
            }
            System.out.println("DEPENDENCY_CASE prefix="+i+" reverse="+reverse+" expected="+expected+" actual="+actual);
        }
        for(boolean reverse:new boolean[]{false,true}) {
            User user=new User();Mind mind=new Mind(user);user.setCurrentMind(mind);
            if(!mind.compile(reverse?restriction:prefixes[0],null,false))
                throw new AssertionError("Initial witness control rejected");
            if(!mind.compile(reverse?prefixes[0]:restriction,null,false)) {
                // The same semantic defect is already counted in the base gate;
                // the concrete control is only reachable on the compatible path.
                continue;
            }
            if(!mind.compile("!rel(1,7,1);",null,false))
                throw new AssertionError("A single explicit witness is compatible");
            String before=DmzObservationStateFingerprint.capture(mind);
            checks++;
            if(mind.compile("!rel(2,7,1);",null,false)) {
                missing++;
                System.out.println("MISSED_CONCRETE_SHARED_WITNESS reverse="+reverse);
            } else if(mind.getLastCompileQualification().getCollisions().isEmpty()
                    || !before.equals(DmzObservationStateFingerprint.capture(mind))
                    || mind.hasPendingTransactions())throw new AssertionError("Concrete rejection must roll back with evidence");
        }
        System.out.println("NATIVE_WITNESS_DEPENDENCY_GATE checks="+checks+" missing="+missing);
        if (missing!=0) System.exit(1);
    }
}
