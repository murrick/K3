package org.kanger.primitives;

import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IUser;
import org.kanger.storage.ByteBuffer;
import org.kanger.units.Domain;

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
    private transient IUser user = null;


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

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putInt(index)
                .putLong(srcId)
                .putLong(dstId)
                .append(arguments.pack());
        return packet.createMarked();
    }

    public Cause apply(ByteBuffer packet) throws OutOfBufferException {
        index = packet.getInt();
        srcId = packet.getLong();
        dstId = packet.getLong();
        try {
            packet.mark();
            arguments = new ArgList().apply(packet);
            arguments.setUser(user);
        } finally {
            packet.release();
        }
        return this;
    }


    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        try {
            apply(new ByteBuffer(in));
        } catch (OutOfBufferException e) {
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.write(pack().getBuffer());
    }


    public Domain getSrc() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (src == null) {
            src = user.getMind().getDomains().load(srcId);
        }
        return src;
    }

    public Domain getDst() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
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
        int hash = 3;
        hash = 47 * hash + (int) (srcId ^ (srcId >>> 32));
        hash = 47 * hash + (int) (dstId ^ (dstId >>> 32));
//        hash = 47 * hash + index;
        return hash;
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
//                    && index == ((Cause) o).getIndex()
                    && arguments.equalsBase(((Cause) o).getArguments());
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return false;
        }
    }

//    public boolean equalsParams(ArgList a) throws Exception {
//        if (arguments == null && a == null) {
//            return true;
//        } else if (arguments != null && a != null && arguments.size() == a.size()) {
//            for (int i = 0; i < arguments.size(); ++i) {
////                if (arguments.get(i).isEmpty() || a.get(i).isEmpty() || arguments.get(i).getValue().getId() != a.get(i).getValue().getId()) {
//                if (arguments.get(i).getId() == -1 || a.get(i).getId() == -1
//                        || arguments.get(i).getId() != a.get(i).getId()
//                        || arguments.get(i).getType() != a.get(i).getType()) {
//                    return false;
//                }
//            }
//            return true;
//        } else {
//            return false;
//        }
//    }

    @Override
    public int compareTo(Cause o) {
        if (o.getDstId() != dstId) {
            return (int) (o.getDstId() - dstId);
        } else {
            return (int) (o.getSrcId() - srcId);
        }
    }

    public IUser getUser() {
        return user;
    }

    public void setUser(IUser user) {
        this.user = user;
        this.arguments.setUser(user);
    }

    public long getSrcId() {
        return srcId;
    }

    public long getDstId() {
        return dstId;
    }

    public UnitType getUnitType() {
        return UnitType.CAUSE;
    }

    public void setSrc(Domain domain) {
        src = domain;
        srcId = domain.getId();
    }

//    public boolean isStored() throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException {
//        if (src.getRight().isStored()) {
//            return true;
//        } else if (src.getCauses() != null) {
//            for (Cause c : src.getCauses()) {
//                if (c.isStored()) {
//                    return true;
//                }
//            }
//            return false;
//        } else {
//            return false;
//        }
//    }
}
