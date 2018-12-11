package kanger.primitives;

public class Cause {
    private Domain d;
    private Right r;

    public Cause(Right r, Domain d) {
        this.d = d;
        this.r = r;
    }

    public Domain getD() {
        return d;
    }

    public void setD(Domain d) {
        this.d = d;
    }

    public Right getR() {
        return r;
    }

    public void setR(Right r) {
        this.r = r;
    }

    @Override
    public int hashCode() {
        return ("" + d.getId() + "-" + r.getId()).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null
                && obj instanceof Cause
                && ((Cause) obj).d.getId() == d.getId()
                && ((Cause) obj).r.getId() == r.getId();
    }


}
