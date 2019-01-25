package kanger.units;

import kanger.User;
import kanger.enums.Enums;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.primitives.Cause;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashSet;
import java.util.Set;

public class Record implements Comparable<Record>, Externalizable, Identifiable<Record> {

    private static final long serialVersionUID = 196402070005L;

    private long id = -1;
    private Domain domain = null;
    private Set<Cause> causes = new HashSet<>();

    //    private Record next = null;
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
        dos.writeInt(causes.size());
        for (Cause c : causes) {
            dos.writeObject(c);
        }
    }

    public void linkExternal(User user) throws RuntimeErrorException {
        this.user = user;
        domain = user.getMind().getDomains().get(domainId);
        if(domain == null) {
            domain = user.getMind().getDomains().load(domainId);
            domain.linkExternal(user);
        }
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

    public Set<Cause> getCauses() {
        return causes;
    }

    @Override
    public String toString() {
        String prefix = "";
        if ((user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0) {
            prefix = id + ":";
        }
        return prefix + domain.toString();
    }

    @Override
    public int getHash() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(domain.isAntc());
        buffer.append(domain.getPredicate().getId());
        buffer.append(domain.getArguments().hashCode());
        return buffer.toString().hashCode();
    }

    @Override
    public boolean equalsTo(Record rec) {
        Domain x = rec.getDomain();
        if (x.isAntc() == domain.isAntc()
                && x.getPredicate().getId() == domain.getPredicate().getId()
                && x.getPredicate().getRange() == domain.getPredicate().getRange()) {
            int i = 0;
            try {
                for (; i < domain.getPredicate().getRange(); ++i) {
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
            } catch (RuntimeErrorException e) {
                e.printStackTrace(System.err);
            }
            return i == domain.getPredicate().getRange();
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
        return (int) (domain.getId() - o.getDomain().getId());
    }
}
