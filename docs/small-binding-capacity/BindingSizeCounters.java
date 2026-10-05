package org.kanger;
import java.util.*;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.TVariable;
/** Diagnostic-only wrapper: original virtual enumeration runs exactly once. */
public final class BindingSizeCounters {
    private static Thread target;
    private static long[] sizes;
    private static long calls,custom,failures,overflow;
    public static void begin(){target=Thread.currentThread();sizes=new long[128];calls=custom=failures=overflow=0;}
    public static List<TVariable> enumerate(ArgumentsList args,Mind mind)throws Exception {
        boolean count=Thread.currentThread()==target;
        if(count)calls++;
        List<TVariable> list;
        try {list=args.getTVariables(mind);}catch(Exception|Error e){if(count)failures++;throw e;}
        if(count){
            if(args.getClass()!=ArgumentsList.class||list==null||list.getClass()!=ArrayList.class)custom++;
            else {int length=list.size();if(length<sizes.length)sizes[length]++;else overflow++;}
        }
        return list;
    }
    public static void finish(int sample){target=null;long sum=0;for(long n:sizes)sum+=n;if(sum+custom+failures+overflow!=calls)throw new AssertionError("counter reconciliation");System.out.println("BINDING_SIZE sample="+sample+" calls="+calls+" custom="+custom+" failures="+failures+" overflow="+overflow+" histogram="+Arrays.toString(sizes));}
}
