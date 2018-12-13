package kanger.primitives;

import java.util.ArrayList;
import java.util.List;

public class Cause {
    private Domain src = null;
    private Domain dst = null;
    private List<Argument> arguments = null;
    private int index = -1;

    public Cause(int index, Domain dst, Domain src) {
        this.index = index;
        this.dst = dst;
        this.src = src;
        this.arguments = src.convertArguments();
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


    @Override
    public int hashCode() {
        StringBuffer buffer = new StringBuffer();
        buffer.append(this.src.getId());
        buffer.append(this.dst.getId());
        buffer.append(this.index);
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
                && index == ((Cause) o).getIndex();
    }
}
