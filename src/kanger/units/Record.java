package kanger.units;

import kanger.User;
import kanger.enums.Enums;
import kanger.interfaces.Identifiable;
import kanger.primitives.Cause;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class Record implements Comparable<Record>, Externalizable, Identifiable {
    private long id = -1;
    private Domain domain = null;
    private int tag = -1;
    private Set<Cause> causes = new HashSet<>();

    private Record next = null;
    private User user = null;

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
        domain = (Domain) dis.readObject();
        tag = dis.readInt();
        int count = dis.readInt();
        while(count-- > 0) {
            Cause c = (Cause) dis.readObject();
            causes.add(c);
        }
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeObject(domain);
        dos.writeInt(tag);
        dos.writeInt(causes.size());
        for(Cause c : causes) {
            dos.writeObject(c);
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
    public int hashCode() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(domain.isAntc());
        buffer.append(domain.getPredicate().getId());
        buffer.append(domain.getArguments().hashCode());
        return buffer.toString().hashCode();
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
