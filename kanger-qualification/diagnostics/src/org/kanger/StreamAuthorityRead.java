package org.kanger;
import java.util.*;
import org.kanger.units.TValue;
import org.kanger.factory.TValueFactory;
import static org.kanger.ResidentTValueRead.*;
/** Fresh full oracle using the unchanged qualified raw reader; no projection-frame reuse. */
public final class StreamAuthorityRead {
    /** Fresh authority serialization only; null denotes no resident routed value. */
    static String encodeRoutes(Mind mind,LinkedHashSet<Long> ids,Map<Long,TValue> byId){
        StringBuilder text=new StringBuilder("[");boolean first=true;
        for(long id:ids){TValue v=byId.get(id);if(v==null)continue;if(!first)text.append(',');first=false;
            text.append(v.getId()).append(':').append(v.getValueId()).append(':').append(v.isDeleted(mind)?1:0);
        }
        return first?null:text.append(']').toString();
    }
    /** Independent full projection algorithm: flatten base-to-leaf routing for
     * every variable at once, then intersect with this context's resident chain.
     * Shares validated raw-state primitives, not incremental dirty state or
     * recursive single-bucket enumeration. Native-control gates check both. */
    public static SortedMap<Long,String> authority(Mind mind)throws Exception{
        validateMind(mind);
        Layer target=layer(mind.getTValues());List<Layer> layers=new ArrayList<>();Set<TValueFactory> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Layer l=target;l!=null;l=l.parent==null?null:layer(l.parent)){if(!seen.add(l.factory))unsupported("factory ancestry cycle");layers.add(l);}
        Map<Long,LinkedHashSet<Long>> merged=new HashMap<>();
        for(int i=layers.size()-1;i>=0;i--)for(Map.Entry<Long,List<Long>> e:layers.get(i).routes.entrySet())merged.computeIfAbsent(e.getKey(),k->new LinkedHashSet<>()).addAll(e.getValue());
        SortedSet<Long> variables=new TreeSet<>();for(TValue value:target.chain)variables.add(value.getTVarId());
        SortedMap<Long,String> result=new TreeMap<>();
        for(long variable:variables){LinkedHashSet<Long> ids=merged.get(variable);if(ids==null)continue;String text=encodeRoutes(mind,ids,target.byId);if(text!=null)result.put(variable,text);}
        return result;
    }
}
