package kanger.primitives;

import kanger.User;
import kanger.enums.Enums;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Record implements Comparable<Record> {
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

    public Record readCompiledData(DataInputStream dis) throws IOException {
        this.user = user;
        id = dis.readLong();
        domain = user.getMind().getDomains().get(dis.readLong());
        tag = dis.readInt();
        int count = dis.readInt();
        while(count-- > 0) {
            Cause c = new Cause(dis, user);
            causes.add(c);
        }
        return this;
    }

    public void writeCompiledData(DataOutputStream dos, User user) throws IOException {
        dos.writeLong(id);
        dos.writeLong(domain.getId());
        dos.writeInt(tag);
        dos.writeInt(causes.size());
        for(Cause c : causes) {
            c.writeCompiledData(dos, user);
        }
    }

    public Domain getDomain() {
        return domain;
    }

    public long getId() {
        return id;
    }

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
