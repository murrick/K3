/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger.primitives;

import org.kanger.Mind;
import org.kanger.QueryDemandTrace;
import org.kanger.SemanticEffectTelemetry;
import org.kanger.enums.UnitType;
import org.kanger.factory.RuleFactory;
import org.kanger.interfaces.ICause;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.storage.ByteBuffer;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

import java.util.Date;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class Cause implements ICause {

    private Rule rule = null;
    private Solve donor = null;

    private long ruleId = -1;

    public Cause() {
    }

    public Cause(Domain dst, Domain src, Mind mind) throws Exception {
        this.donor = new Solve(src.getPredicate(), src.isAntc(), src.getArguments().convertBase(mind));
        this.rule = (Rule) dst.getRule();
        this.ruleId = dst.getRuleId();
        SemanticEffectTelemetry.recordCause(this);
        QueryDemandTrace.recordCause(dst, src, mind);
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(ruleId)
                .append(((Solve) donor).pack());
        return packet.createMarked();
    }

    public Cause apply(ByteBuffer packet) throws Exception {
        ruleId = packet.getLong();
        try {
            packet.mark();
            donor = new Solve().apply(packet);
        } finally {
            packet.release();
        }
        return this;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (int) (ruleId ^ (ruleId >>> 32));
        hash = 47 * hash + donor.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        try {
            return o != null
                    && o instanceof Cause
                    && ((Cause) o).getRuleId() == ruleId
                    && donor.equals(((Cause) o).getDonor());
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return false;
        }
    }

    public UnitType getUnitType() {
        return UnitType.CAUSE;
    }

    public Solve getDonor() {
        return donor;
    }

    @Override
    public IRule getDonor(IMind mind) throws Exception {
        return ((RuleFactory) mind.getRules()).find(donor);
    }

    @Override
    public IRule getRule(IMind mind) throws Exception {
        if (rule == null) {
            rule = (Rule) mind.getRules().get(ruleId);
        }
        return rule;
    }

    public void setRule(Rule rule) {
        this.rule = rule;
        this.ruleId = rule.getId();
    }

    public long getRuleId() {
        return ruleId;
    }
}
