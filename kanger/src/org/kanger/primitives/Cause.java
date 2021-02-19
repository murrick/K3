package org.kanger.primitives;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ISolve;
import org.kanger.storage.ByteBuffer;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

/**
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public class Cause implements org.kanger.interfaces.ICause {
    //    private Solve result = null;
//    private Solve acceptor = null;
    private ISolve donor = null;
    private IRule rule = null;

//    private Right next = null;

    private transient long ruleId = -1;
//    private transient long nextId = -1;


    public Cause() {
    }

    public Cause(Mind mind, Domain dst, Domain src) throws Exception {
        this.donor = new Solve(dst.getPredicate(), src.isAntc(), src.getArguments().convertBase(mind));
        this.rule = dst.getRule();
//        this.next = mind.getRights().find(src);

        ruleId = dst.getRuleId();
//        nextId = next == null ? -1 : next.getId();

//        if(src.getCauses() != null) {
//            for (Cause c : src.getCauses()) {
//                c.setResult(donor);
//                next.add(c);
//            }
//        } else
//            if(src.getRight().getCauses() != null) {
//            for (Cause c : src.getRight().getCauses()) {
//                c.setResult(donor);
//                next.add(c);
//            }
//        }
    }

//    public Solve getResult() {
//        return result;
//    }
//
//    public void setResult(Solve result) {
//        this.result = result;
//    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(ruleId)
//                .putLong(nextId)
                .append(((Solve) donor).pack());
//                .append(acceptor.pack());
//        if(result != null) {
//            packet.append(result.pack());
//        }
        return packet.createMarked();
    }

    public Cause apply(ByteBuffer packet) throws Exception {
//        index = packet.getInt();
        ruleId = packet.getLong();
//        nextId = packet.getLong();
        try {
            packet.mark();
            donor = new Solve().apply(packet);
        } finally {
            packet.release();
        }
//        try {
//            packet.mark();
//            acceptor = new Solve().apply(packet);
//        } finally {
//            packet.release();
//        }
//        if(!packet.isEod()) {
//            try {
//                packet.mark();
//                result = new Solve().apply(packet);
//            } finally {
//                packet.release();
//            }
//        }
        return this;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (int) (ruleId ^ (ruleId >>> 32));
        hash = 47 * hash + donor.hashCode();
//        hash = 47 * hash + acceptor.hashCode();
//        if(result != null) {
//            hash = 47 * hash + result.hashCode();
//        }
        return hash;
    }

//        @Override
//        public int hashCode(){
//            return toString().hashCode();
//        }

    private boolean equalsId(ArgumentsList args) {
        if (args.size() == donor.getArguments().size()) {
            for (int i = 0; i < args.size(); ++i) {
                if (args.get(i).getId() != donor.getArguments().get(i).getId()) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        try {
            return o != null
                    && o instanceof Cause
                    && ((Cause) o).getRuleId() == ruleId
                    && donor.equals(((Cause) o).getDonor());
//                    && acceptor.equals(((Cause) o).getAcceptor())
//                    && ((result == null && ((Cause) o).getResult() == null) || (result.equals(((Cause) o).getResult())));
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

//    @Override
//    public int compareTo(Cause o) {
//            return (int) (o.getDstId() - dstId);
//        } else {
//            return (int) (o.getSrcId() - srcId);
//        }
//    }

//    public IUser getUser() {
//        return user;
//    }

//    public void setUser(IUser user) {
//        this.user = user;
//        this.arguments.setUser(user);
//    }

    public UnitType getUnitType() {
        return UnitType.CAUSE;
    }

//    public Solve getAcceptor() {
//        return acceptor;
//    }
//
//    public void setAcceptor(Solve acceptor) {
//        this.acceptor = acceptor;
//    }

    @Override
    public ISolve getDonor() {
        return donor;
    }

    public void setDonor(Solve donor) {
        this.donor = donor;
    }

    @Override
    public IRule getRule(IMind mind) throws Exception {
        if (rule == null) {
            rule = mind.getRules().get(ruleId);
        }
        return rule;
    }

    public void setRule(Rule rule) {
        this.rule = rule;
        this.ruleId = rule.getId();
    }

//    public Right getNext() {
//        return next;
//    }
//
//    public void setNext(Right next) {
//        this.next = next;
//    }

    @Override
    public long getRuleId() {
        return ruleId;
    }

    public void setRuleId(long ruleId) {
        this.ruleId = ruleId;
    }

//    public boolean isStored() throws Exception {
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
