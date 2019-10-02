package kanger.primitives;

import kanger.User;
import kanger.units.Domain;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class Cause implements Externalizable, Comparable<Cause> {
    private Domain src = null;
    private Domain dst = null;
    private ArgList arguments = null;
    private int index = -1;

    private transient long srcId = -1;
    private transient long dstId = -1;
    private transient User user = null;


    public Cause() {
    }

    public Cause(int index, Domain dst, Domain src) {
        this.index = index;
        this.dst = dst;
        this.src = src;
        this.dstId = dst.getId();
        this.srcId = src.getId();
        this.arguments = src.getArguments().convertBase();
    }

    @Override
    public void readExternal(ObjectInput dis) throws IOException, ClassNotFoundException {
        index = dis.readInt();
        srcId = dis.readLong();
        dstId = dis.readLong();
        arguments = (ArgList) dis.readObject();
        arguments.setUser(user);
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeInt(index);
        dos.writeLong(srcId);
        dos.writeLong(dstId);
        dos.writeObject(arguments);
    }


    public Domain getSrc() throws IOException, ClassNotFoundException {
        if (src == null) {
            src = user.getMind().getDomains().load(srcId);
        }
        return src;
    }

    public Domain getDst() throws IOException, ClassNotFoundException {
        if (dst == null) {
            dst = user.getMind().getDomains().load(dstId);
        }
        return dst;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public ArgList getArguments() {
        return arguments;
    }

    @Override
    public int hashCode() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(srcId);
        buffer.append(dstId);
        buffer.append(index);
//        for(Argument a: arguments) {
//            buffer.append(a.getValue().getId());
//        }
        return buffer.toString().hashCode();
    }

//        @Override
//        public int hashCode(){
//            return toString().hashCode();
//        }

    @Override
    public boolean equals(Object o) {
        try {
            return o != null
                    && o instanceof Cause
                    && srcId != -1 && dstId != -1
                    && ((Cause) o).getSrcId() != -1 && ((Cause) o).getDstId() != -1
                    && srcId == ((Cause) o).getSrcId()
                    && dstId == ((Cause) o).getDstId()
                    && index == ((Cause) o).getIndex()
                    && equalsParams(((Cause) o).getArguments());
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return false;
        }
    }

    public boolean equalsParams(ArgList a) throws Exception {
        if (arguments == null && a == null) {
            return true;
        } else if (arguments != null && a != null && arguments.size() == a.size()) {
            for (int i = 0; i < arguments.size(); ++i) {
//                if (arguments.get(i).isEmpty() || a.get(i).isEmpty() || arguments.get(i).getValue().getId() != a.get(i).getValue().getId()) {
                if (arguments.get(i).getId() == -1 || a.get(i).getId() == -1
                        || arguments.get(i).getId() != a.get(i).getId()
                        || arguments.get(i).getType() != a.get(i).getType()) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int compareTo(Cause o) {
        if (o.getDstId() != dstId) {
            return (int) (o.getDstId() - dstId);
        } else {
            return (int) (o.getSrcId() - srcId);
        }
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
        this.arguments.setUser(user);
    }

    public long getSrcId() {
        return srcId;
    }

    public long getDstId() {
        return dstId;
    }
}
