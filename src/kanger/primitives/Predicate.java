package kanger.primitives;

import java.io.*;
import java.util.*;
import kanger.*;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 *
 * Описание предиката. Голова предиката ссылается на список решений для него.
 * Список решений состоит из строк со значениями параметров. Флажок cuted служит
 * для отмены какого-либо решения. Собственно говоря предикат со списком решений
 * является единицей базы данных.
 */
public class Predicate {

    private String name = "";               // Имя предиката
    private int range = 0;                  // К-во параметров
    private long id = -1;                   // Идентификатор
    private Predicate next = null;          // Следующий предикат

    private User user = null;

    public Predicate(User user) {
        this.user = user;
    }

    public Predicate(DataInputStream dis, User user) throws IOException {
        id = dis.readLong();
        name = dis.readUTF();
        range = dis.readInt();
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = range;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Predicate getNext() {
        return next;
    }

    public void setNext(Predicate next) {
        this.next = next;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(id);
        dos.writeUTF(name);
        dos.writeInt(range);
    }

    public Set<Domain> getSolves() {
        Set<Domain> set = new HashSet<>();
//        for(long id : mind.getProducedDomains().keySet()) {
//            Domain d = mind.getDomains().get(id);
//            if(d.getPredicate().getId() == getId()) {
//                for(List<Long> args : mind.getProducedDomains().get(id)) {
//                    Solution s = new Solution(mind, d.isAntc(), d.getPredicate(), d.getArguments());
//                    set.add(s);
//                }
//            }
//        }

        for (Domain d = user.getMind().getDatabase().getRoot(); d != null; d = d.getNext()) {
            if (getId() == d.getPredicate().getId()) {
                set.add(d);
            }
        }
        return set;
    }

    public Domain containsSolve(Domain d) {
        for (Domain x : getSolves()) {
            if (x.isStored() && d.equalsBase(x)) {
                return x;
            }
        }
        return null;
    }

//    public boolean checkSolves() {
//        for(Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
//            if(getId() == d.getPredicate().getId()) {
//                for(Domain q = mind.getDomains().getRoot(); q != null; q = d.getNext()) {
//                    if(getId() == q.getPredicate().getId()) {
//                        for(int i=0; i<d.getPredicate().getRange(); ++i) {
//                            if(!d.getArguments().get(i).isEmpty()
//                                && !q.getArguments().get(i).isEmpty()
//                            && d.getArguments().get(i).getValue().getId() == q.getArguments().get(i).getValue().getId()) {
//                                return true;
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        return false;
//    }

//    public Set<Right> getRights() {
//        Set<Right> set = new HashSet<>();
//        for(Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
//            if(d.getPredicate().getId() == id) {
//                set.add(d.getRight());
//            }
//        }
//        return set;
//    }

    @Override
    public String toString() {
        return name + "(" + range + ")";
    }

//    @Override
//    public boolean equals(Object t) {
//        return !(t == null || !(t instanceof Predicate)) && ((Predicate) t).id == id;
//    }

//    public List<TVariable> getTVariables(boolean full) {
//        List<TVariable> list = new ArrayList<>();
//        for(Domain d = mind.getDomains().getRoot(); d != null; d = d.getNext()) {
//            if(d.getPredicate().id == id) {
//                for(TVariable t : d.getTVariables(full)) {
//                    if(!list.contains(t)) {
//                        list.add(t);
//                    }
//                }
//            }
//        }
//        return list;
//    }
}
