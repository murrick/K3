package kanger.primitives;

import kanger.User;

public class Record implements Comparable<Record> {
    private Domain domain = null;
    private long id = -1;
    private Record next = null;
    private int tag = -1;
    private boolean query = false;

    private User user = null;

    public Record(Domain domain) {
        this.domain = domain;
        this.user = domain.getUser();
    }

    public Record(User user, boolean antc, Object predicate, Object... params) {
        this.user = user;
        Domain d = new Domain(user);
        d.setAntc(antc);
        if (predicate instanceof Predicate) {
            d.setPredicate((Predicate) predicate);
        } else {
            d.setPredicate(user.getMind().getPredicates().add(predicate.toString(), params.length));
        }
        for (Object p : params) {
            if (p instanceof Term) {
                d.add(new Argument((Term) p));
            } else {
                d.add(new Argument(user.getMind().getTerms().add(p)));
            }
        }
        domain = d;
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

    public boolean isQuery() {
        return query;
    }

    public void setQuery() {
        this.query = true;
    }

    @Override
    public String toString() {
//        String prefix;
//        if (tag != -1 && (user.getMind().getDebugLevel() & Enums.DEBUG_OPTION_STATUS) != 0) {
//            prefix = tag + ":\t";
//        } else {
//            prefix = "";
//        }
        return /*prefix +*/ domain.toString();
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
