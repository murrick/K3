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

package org.kanger.units;

import org.kanger.Mind;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.ByteBuffer;

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 13.12.16.
 */
public class TSolve implements Comparable<TSolve>, IUnit<TSolve> {

    private static final long serialVersionUID = 196402070009L;

    private long id = -1;                               // Идентификатор значения переменной
    private long mindId = -1;                           // id транзакции
    private List<TValue> solve = new ArrayList<>();     // Список подстановок

    private List<Long> solveIds = new ArrayList<>();
    private Mind mind = null;

    public TSolve() {
    }

    public TSolve(List<TValue> list, Mind mind) {
        this.solve.addAll(list);
        for (TValue v : solve) {
            solveIds.add(v.getId());
        }
        this.mind = mind;
    }

    public TSolve(Domain d, Mind mind) throws Exception {
        this.solve.addAll(d.getArguments().getTValues(mind, true));
        for (TValue v : solve) {
            solveIds.add(v.getId());
        }
        this.mind = mind;
    }

    public TSolve(TValue vv, Mind mind) {
        solve.add(vv);
        solveIds.add(vv.getId());
        this.mind = mind;
    }

    public void add(TValue v) {
        if (!solveIds.contains(v.getId())) {
            solve.add(v);
            solveIds.add(v.getId());
        }
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(mind) ? 1 : 0);
        packet.putInt(solve.size());
        for (TValue v : solve) {
            packet.putLong(v.getId());
        }
        return packet.createMarked();
    }

    public TSolve apply(ByteBuffer packet) throws OutOfBufferException {
        solveIds.clear();
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        int count = packet.getInt();
        while (count-- > 0) {
            solveIds.add(packet.getLong());
        }
        return this;
    }

    public List<TValue> getSolve() throws Exception {
        if (solve.isEmpty() && !solveIds.isEmpty()) {
            for (long id : solveIds) {
                TValue v = mind.getTValues().get(id);
                solve.add(v);
            }
        }
        return solve;
    }

    public TValue getValue(TVariable t, Mind mind) throws Exception {
        for (TValue v : solve) {
            if (t.getId() == v.getTVar(mind).getId()) {
                return v;
            }
        }
        return null;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public String toString() {
        try {
            String str = "";
            for (TValue v : getSolve()) {
                if (!str.isEmpty()) {
                    str += ", ";
                }
                str += v.toString();
            }
            return str;
        } catch (Exception e) {
            System.err.println(new Date());
            e.printStackTrace(System.err);
            return "";
        }
    }

    @Override
    public int getHash() {
        int hash = 3;
        for (long id : solveIds) {
            hash = 47 * hash + (int) (id ^ (id >>> 32));
        }
        return hash;
    }

    @Override
    public boolean equalsTo(TSolve to) {
        if (solveIds.size() != to.solveIds.size()) {
            return false;
        }
        for (long id : solveIds) {
            if (!to.solveIds.contains(id)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Mind getMind() {
        return mind;
    }

    @Override
    public TSolve setMind(Mind mind) {
        this.mind = mind;
        return this;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (int) (id ^ (id >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj instanceof TSolve && ((TSolve) obj).getId() == id;
    }

    @Override
    public int compareTo(TSolve o) {
        return (int) (id - o.getId());
    }

    @Override
    public boolean isDeleted(IMind mind) {
        return ((Mind) mind).isUnitDeleted(this);
    }

    @Override
    public void setDeleted(boolean on, Mind mind) {
        mind.setUnitDeleted(this, on);
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.TVALUE;
    }

    @Override
    public long getMindId() {
        return mindId;
    }

    @Override
    public void setMindId(long mindId) {
        this.mindId = mindId;
    }

    public boolean containsTVar(TVariable t) {
        for (TValue v : solve) {
            if (v.getTVarId() == t.getId()) {
                return true;
            }
        }
        return false;
    }

    public boolean containsTValue(TValue t) {
        for (TValue v : solve) {
            if (t != null && v.getId() == t.getId()) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return solve.size();
    }

    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public Map<String, Object> createMap(IMind mind) throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("mind_id", mindId);
        List<Map<String, Object>> s = new ArrayList<>();
        for (TValue v : solve) {
            s.add(createMap(mind));
        }
        map.put("solve", s);
        return map;
    }

    @Override
    public TSolve applyMap(Map<String, Object> map) throws Exception {
        // Нечего тут накатывать
        return this;
    }


}
