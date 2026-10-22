package org.kanger;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
/** Records initial option resolution and invokes the unchanged pinned gate. */
public final class SemanticLaunch {
 public static void main(String[] args)throws Throwable {
  System.out.println("OPTIONS_ENTRY "+OptimizationOptions.snapshot(null));
  System.out.println("GATE_ENTRY "+args[0]);
  try{Class.forName("org.kanger."+args[0]).getMethod("main",String[].class).invoke(null,(Object)Arrays.copyOfRange(args,1,args.length));}
  catch(InvocationTargetException e){throw e.getCause();}
  System.out.println("SEMANTIC_GATE_RETURNED "+args[0]);
 }
}
