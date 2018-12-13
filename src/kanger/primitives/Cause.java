package kanger.primitives;

import java.util.List;

public class Cause implements Comparable<Cause> {
    private Domain src = null;
    private Domain dst = null;
    private ArgList arguments = null;
    private int index = -1;

    public Cause(int index, Domain dst, Domain src) {
        this.index = index;
        this.dst = dst;
        this.src = src;
        this.arguments = src.getArguments().convert();
    }

    public Domain getSrc() {
        return src;
    }

    public void setSrc(Domain src) {
        this.src = src;
    }

    public Domain getDst() {
        return dst;
    }

    public void setDst(Domain dst) {
        this.dst = dst;
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
        buffer.append(this.src.getId());
        buffer.append(this.dst.getId());
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
                && src != null && dst != null
                && ((Cause) o).getSrc() != null && ((Cause) o).getDst() != null
                && src.getId() == ((Cause) o).getSrc().getId()
                && dst.getId() == ((Cause) o).getDst().getId()
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
        if (o.getDst().getId() != dst.getId()) {
            return (int) (o.getDst().getId() - dst.getId());
        } else {
            return (int) (o.getSrc().getId() - src.getId());
        }
    }
}
