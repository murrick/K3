package org.kanger;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.kanger.interfaces.*;
import org.kanger.units.*;

/** Ready child operands from native set_08_03, prepared before publication. */
public final class PublicationFixture {
    final Mind parent=new Mind(new User());
    final Mind[] children={new Mind(parent),new Mind(parent),new Mind(parent)};
    final String inputs;
    PublicationFixture()throws Exception{
        for(int actor=0;actor<3;actor++)for(int i=0;i<3;i++)require(Boolean.TRUE.equals(children[actor].query("!value(1, ?, ?);",new Object[]{i,(actor+1)*1000+i})),"native actor value");
        require(Boolean.TRUE.equals(children[0].query("!value(1, 2, 3002);")),"native duplicate operand");
        require(Boolean.TRUE.equals(children[1].query("!~value(1, 2, 1002);")),"native conflict operand");
        List<String> rows=new ArrayList<>();for(int actor=0;actor<3;actor++)rows.add("actor="+(actor+1)+" rules="+rules(children[actor]));inputs=String.join("\n",rows);
        require(parent.getRules().size()==0&&parent.pendingTransactionCount()==3,"prepared children remain unpublished");
    }
    static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static String rules(Mind m){List<String> rows=new ArrayList<>();for(IRule r:m.getRules()){Rule rule=(Rule)r;rows.add(rule.getId()+":"+rule.toString(m)+":deleted="+rule.isDeleted(m)+":stored="+rule.isStored()+":query="+rule.isQuery());}Collections.sort(rows);return String.join("|",rows);}
    String result(boolean[] accepted)throws Exception{
        require(parent.pendingTransactionCount()==0,"all native reservations settled");
        int count=parent.getRules().size();require(count==6||count==7,"native retained rule count");
        String before=rules(parent);Boolean answer=parent.query("?$x $y value(1, x, y);");
        require(Boolean.TRUE.equals(answer)&&parent.getSolutions().size()==6,"six native query solutions");
        List<String> solutions=new ArrayList<>();for(IRule r:parent.getSolutions())solutions.add(r.getId()+":"+r.toString(parent));Collections.sort(solutions);
        List<String> values=new ArrayList<>();for(Map<String,ITerm> row:parent.getValues()){SortedMap<String,String> v=new TreeMap<>();for(Map.Entry<String,ITerm> e:row.entrySet())v.put(e.getKey(),e.getValue().getId()+":"+e.getValue().toString());values.add(v.toString());}Collections.sort(values);
        List<String> hypotheses=new ArrayList<>();for(IHypothesis h:parent.getHypothesis())hypotheses.add(h.toString());Collections.sort(hypotheses);
        List<String> temporary=new ArrayList<>();for(IHypothesis h:parent.getTempHypothesis())temporary.add(h.toString());Collections.sort(temporary);
        return "accepted="+Arrays.toString(accepted).replace(" ","")+"\nrules="+count+"\nbefore="+before+"\nanswer="+answer+"\nsolutions="+String.join("|",solutions)+"\nvalues="+String.join("|",values)+"\nhypotheses="+String.join("|",hypotheses)+"\ntemporary="+String.join("|",temporary)+"\nafter="+rules(parent)+"\nreservations="+parent.pendingTransactionCount();
    }
    static String encode(String text){return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));}
    static String decode(String text){return new String(Base64.getDecoder().decode(text),StandardCharsets.UTF_8);}
    static String digest(String text)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}
    static String order(int[] values){StringBuilder out=new StringBuilder();for(int value:values)out.append(value);return out.toString();}
    static int[] parseOrder(String text){require(text.length()==3,"three actors");int[] result=new int[3];Set<Integer> seen=new HashSet<>();for(int i=0;i<3;i++){result[i]=text.charAt(i)-'0';require(result[i]>=1&&result[i]<=3&&seen.add(result[i]),"complete actor permutation");}return result;}
}
