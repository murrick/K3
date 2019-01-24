package kanger.primitives;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.units.Domain;

import java.io.*;

public class Cause implements Externalizable, Comparable<Cause> {
    private Domain src = null;
    private Domain dst = null;
    private ArgList arguments = null;
    private int index = -1;

    private transient long srcId = -1;
    private transient long dstId = -1;

    public Cause() {

    }

    public Cause(int index, Domain dst, Domain src) {
        this.index = index;
        this.dst = dst;
        this.src = src;
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
        dos.writeLong(src.getId());
        dos.writeLong(dst.getId());
        dos.writeObject(arguments);
    }

    public void linkExternal(User user) throws RuntimeErrorException {
        src = user.getMind().getDomains().get(srcId);
        dst = user.getMind().getDomains().get(dstId);
        arguments.linkExternal(user);
    }

    public Domain getSrc() {
        return src;
    }

    public Domain getDst() {
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

    public void setArguments(ArgList arguments) {
        this.arguments = arguments;
    }

    @Override
    public int hashCode() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(src == null ? srcId : src.getId());
        buffer.append(dst == null ? dstId : dst.getId());
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
        try {
            return o != null
                    && o instanceof Cause
                    && src != null && dst != null
                    && ((Cause) o).getSrc() != null && ((Cause) o).getDst() != null
                    && src.getId() == ((Cause) o).getSrc().getId()
                    && dst.getId() == ((Cause) o).getDst().getId()
                    && index == ((Cause) o).getIndex()
                    && equalsParams(((Cause) o).getArguments());
        } catch (RuntimeErrorException e) {
            e.printStackTrace(System.err);
            return false;
        }
    }

    public boolean equalsParams(ArgList a) throws RuntimeErrorException {
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
        if (o.getDst().getId() != dst.getId()) {
            return (int) (o.getDst().getId() - dst.getId());
        } else {
            return (int) (o.getSrc().getId() - src.getId());
        }
    }
}
