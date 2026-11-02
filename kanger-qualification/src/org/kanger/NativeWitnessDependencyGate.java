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
        System.out.println("NATIVE_WITNESS_DEPENDENCY_GATE checks="+checks+" missing="+missing);
        if (missing!=0) System.exit(1);
    }
}
