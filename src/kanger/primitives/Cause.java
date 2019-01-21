package kanger.primitives;

import kanger.User;
import kanger.units.Domain;

import java.io.*;

public class Cause implements Externalizable, Comparable<Cause> {
    private long srcId = -1;
    private long dstId = -1;
    private ArgList arguments = null;
    private int index = -1;

    public Cause() {

    }

    public Cause(int index, Domain dst, Domain src) {
        this.index = index;
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
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeInt(index);
        dos.writeLong(srcId);
        dos.writeLong(dstId);
        dos.writeObject(arguments);
    }

    public void linkExternal(User user) {
        arguments.linkExternal(user);
    }

    public long getSrcId() {
        return srcId;
    }

    public long getDstId() {
        return dstId;
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

    public void setArguments(ArgList arguments) {
        this.arguments = arguments;
    }

    @Override
    public int hashCode() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(this.srcId);
        buffer.append(this.dstId);
        buffer.append(this.index);
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
        return o != null
                && o instanceof Cause
                && srcId != -1 && dstId != -1
                && ((Cause) o).getSrcId() != -1 && ((Cause) o).getDstId() != -1
                && srcId == ((Cause) o).getSrcId()
                && dstId == ((Cause) o).getDstId()
                && index == ((Cause) o).getIndex()
                && equalsParams(((Cause) o).getArguments());
    }

    public boolean equalsParams(ArgList a) {
        if (arguments == null && a == null) {
            return true;
        } else if (arguments != null && a != null && arguments.size() == a.size()) {
            for (int i = 0; i < arguments.size(); ++i) {
                if (arguments.get(i).isEmpty() || a.get(i).isEmpty() || arguments.get(i).getValue().getId() != a.get(i).getValue().getId()) {
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
}
