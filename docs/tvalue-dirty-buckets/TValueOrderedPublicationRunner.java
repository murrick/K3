package org.kanger;
import java.util.*;
import org.kanger.interfaces.IRule;
/** Native set_08_03 operands with every explicit publication order. */
public final class TValueOrderedPublicationRunner {
    private static int checks;
    private static void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);checks++;}
    public static void main(String[] args)throws Exception{
        for(int[] order:new int[][]{{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}}){
            Mind parent=new Mind(new User());Mind[] children={new Mind(parent),new Mind(parent),new Mind(parent)};
            for(int actor=0;actor<3;actor++){
                for(int i=0;i<3;i++)require(Boolean.TRUE.equals(children[actor].query("!value(1, "+i+", "+((actor+1)*1000+i)+");")),"native initial value");
            }
            require(Boolean.TRUE.equals(children[0].query("!value(1, 2, 3002);")),"native duplicated operand");
            require(Boolean.TRUE.equals(children[1].query("!~value(1, 2, 1002);")),"native conflicting operand");
            boolean[] accepted=new boolean[3];for(int actor:order)accepted[actor]=parent.commit(children[actor]);
            require(parent.pendingTransactionCount()==0,"all native reservations settled");
            int size=parent.getRules().size();require(size==6||size==7,"native retained branches have six or seven rules");
            require(Boolean.TRUE.equals(parent.query("?$x $y value(1, x, y);")),"native final query");
            List<String> payload=new ArrayList<>();for(IRule rule:parent.getSolutions())payload.add(rule.toString());Collections.sort(payload);
            require(payload.size()==6,"six native solutions");
            System.out.println("ORDERED_PUBLICATION order="+(order[0]+1)+(order[1]+1)+(order[2]+1)+" accepted="+Arrays.toString(accepted).replace(" ","")+" rules="+size+" payload="+String.join("|",payload));
        }
        System.out.println("TVALUE_ORDERED_PUBLICATION_OK checks="+checks);
    }
}
