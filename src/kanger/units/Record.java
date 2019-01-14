package kanger.units;

import kanger.User;
import kanger.enums.Enums;
import kanger.interfaces.Identifiable;
import kanger.primitives.Cause;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import java.util.Set;

public class Record implements Comparable<Record>, Externalizable, Identifiable<Domain> {
    private long id = -1;
    private Domain domain = null;
    private int tag = -1;
    private Set<Cause> causes = new HashSet<>();

    private Record next = null;
    private User user = null;

    private transient long domainId = -1;

    public Record() {
    }

    public Record(Domain domain) {
        this.domain = domain;
        this.user = domain.getUser();
    }

    public Record(User user) {
        this.user = user;
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        id = dis.readLong();
        domainId = dis.readLong();
        tag = dis.readInt();
        int count = dis.readInt();
        while (count-- > 0) {
            Cause c = (Cause) dis.readObject();
            causes.add(c);
        }
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(domain.getId());
        dos.writeInt(tag);
        dos.writeInt(causes.size());
        for (Cause c : causes) {
            dos.writeObject(c);
        }
    }

    public void linkExternal() {
        domain = user.getMind().getDomains().get(domainId);
        for (Cause c : causes) {
            c.linkExternal(user);
        }
    }

    public Domain getDomain() {
        return domain;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public Record getNext() {
        return next;
    }

    public void setNext(Record next) {
        this.next = next;
    }

    public int getTag() {
        return tag;
    }

    public void setTag(int tag) {
        this.tag = tag;
    }

    //    public boolean isQuery() {
//        return query;
//    }
//
//    public void setQuery() {
//        this.query = true;
//    }
//

    public Set<Cause> getCauses() {
        return causes;
    }

    @Override
    public String toString() {
        String prefix = "";
        if (tag != -1 && (user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0) {
            prefix = id + ":";
        }
        return prefix + domain.toString();
    }

    @Override
    public int getHash() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(domain.isAntc());
        buffer.append(domain.getPredicateId());
        buffer.append(domain.getArguments().hashCode());
        return buffer.toString().hashCode();
    }

    @Override
    public boolean equalsTo(Domain x) {
        //TODO: ОПТИМИЗАЦИЯ, Хранить ранг в домене
        int range = user.getMind().getPredicates().get(domain.getPredicateId()).getRange();
        int rangeX = user.getMind().getPredicates().get(x.getPredicateId()).getRange();
        if (x.isAntc() == domain.isAntc()
                && x.getPredicateId() == domain.getPredicateId()
                && rangeX == range) {
            int i = 0;
            for (; i < range; ++i) {
                if (!x.get(i).isEmpty()
                        && !domain.getArguments().get(i).isEmpty()
                        && x.get(i).getValue().getId() != domain.getArguments().get(i).getValue().getId()) {
                    break;
                }

                TValue a = x.get(i).isTSet() ? x.get(i).getT().getCurrent() : x.get(i).getV();
                TValue b = domain.getArguments().get(i).isTSet() ? domain.getArguments().get(i).getT().getCurrent() : domain.getArguments().get(i).getV();
                if (a != null && b != null && a.getTVar().getId() != b.getTVar().getId()) {
                    break;
                }
            }
            return i == range;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return ("" + id).hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o != null && o instanceof Record && id == ((Record) o).getId();
    }


    @Override
    public int compareTo(Record o) {
        if (tag != o.getTag()) {
            return tag - o.getTag();
        } else {
            return (int) (domain.getId() - o.getDomain().getId());
        }
    }
}
