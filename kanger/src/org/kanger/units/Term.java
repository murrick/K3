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
import org.kanger.compiler.PTree;
import org.kanger.enums.DataType;
import org.kanger.enums.Enums;
import org.kanger.enums.Tools;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.ArgumentsList;
import org.kanger.storage.ByteBuffer;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 20.05.15.
 * <p>
 * Элемент словаря
 */
public class Term implements IUnit<Term>, ITerm {

    public static final double FLT_EPSILON = 0.00000000001;

    private static final long serialVersionUID = 196402070008L;


    private long id = -1;                // Идентификатор
    private long mindId = -1;                                   // id транзакции
    private DataType type = DataType.VOID;
    private Object value = null;
    private int hash = 0;
    private int index = 0;              // Индекс c-переменной
    private ITerm name = null;             // Оригинальное имя c-переменной
    private IRule rule = null;          // Ссылка на правило
    private ITerm parent = null;
    //    private final Set<Long> childs = new HashSet<>();      // Список дочерних c-переменных

    //    private Term next = null;      // Следующая запись
    private transient final Set<Long> slaves = new HashSet<>();      // Список подчиненных t-переменных
    private transient Mind mind = null;

//    private transient boolean deleted = false;

    private transient long nameId = -1;
    private transient long ruleId = -1;
    private transient long parentId = -1;

    public Term() {
    }

    public Term(Mind mind) {
        this.mind = mind;
    }

    public Term(Object str, Mind mind) throws Exception {
        this.mind = mind;
        construct(str);
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(isDeleted(mind) ? 1 : 0)
                .putInt(type.ordinal())
                .putInt(hash);
        switch (type) {
            case DATE:
                packet.putLong(((Date) value).getTime());
                break;
            case NUMERIC:
                packet.putDouble((double) value);
                break;
            case INTERVAL:
            case SET:
                if (value instanceof Collection) {
                    packet.putByte(1)
                            .putInt(((Collection) value).size());
                    for (Term t : (Collection<Term>) value) {
                        packet.append(t.pack());
                    }
                } else {
                    packet.putByte(0)
                            .putString((String) value);
                }
                break;
            case PERIOD:
            case STRING:
                packet.putString((String) value);
                break;
            case BLOB:
                packet.putBytes((byte[]) value);
                break;
            case TERM:
                packet.append(((Term) value).pack());
                break;
        }
        packet.putInt(index);
        if (index > 0) {
            packet.putLong(nameId);
            packet.putLong(ruleId);
            packet.putLong(parentId);
            packet.putWord(slaves.size());
            for (long sid : slaves) {
                packet.putLong(sid);
            }
        }
        return packet.createMarked();
    }

    public Term apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        mindId = packet.getLong();
        if (packet.getByte() != 0) {
            setDeleted(true, mind);
        }
        type = DataType.values()[packet.getInt()];
        hash = packet.getInt();
        switch (type) {
            case DATE:
                value = new Date(packet.getLong());
                break;
            case NUMERIC:
                value = packet.getDouble();
                break;
            case SET:
            case INTERVAL:
                if (packet.getByte() != 0) {
                    value = new ArrayList<Term>();
                    int cnt = packet.getInt();
                    for (int i = 0; i < cnt; ++i) {
                        try {
                            packet.mark();
                            Term t = new Term().apply(packet);
                            ((List<Term>) value).add(t);
                        } finally {
                            packet.release();
                        }
                    }
                } else {
                    value = packet.getString();
                }
                break;
            case PERIOD:
            case STRING:
                value = packet.getString();
                break;
            case BLOB:
                value = packet.getBytes();
                break;
            case TERM:
                try {
                    packet.mark();
                    value = new Term().apply(packet);
                } finally {
                    packet.release();
                }
                break;
        }

        index = packet.getInt();
        if (index > 0) {
            nameId = packet.getLong();
            ruleId = packet.getLong();
            parentId = packet.getLong();
            slaves.clear();
            int cnt = packet.getWord();
            while (cnt-- > 0) {
                long id = packet.getLong();
                slaves.add(id);
            }

        }
        return this;
    }

    private void construct(Object o) throws Exception {
        value = null;
        if (o instanceof Number) {
            type = DataType.NUMERIC;
            value = ((Number) o).doubleValue();
        } else if (o instanceof Date) {
            type = DataType.DATE;
            value = o;
        } else if (o instanceof byte[]) {
            type = DataType.BLOB;
            value = o;
        } else if (o instanceof PTree) {
            if ("..".equals(((PTree) o).getName())) {
                List<ITerm> list = new ArrayList<>();
                list.add(mind.getTerms().add(((PTree) o).getLeft().getName()));
                list.add(mind.getTerms().add(((PTree) o).getRight().getName()));
                type = DataType.INTERVAL;
                value = list;
            }
        } else if (o instanceof ITerm[]) {
            List<ITerm> list = new ArrayList<>();
            list.add(((ITerm[]) o)[0]);
            list.add(((ITerm[]) o)[1]);
            type = DataType.INTERVAL;
            value = list;
        } else if (o instanceof Object[]) {
            List<ITerm> list = new ArrayList<>();
            for (Object a : (Object[]) o) {
                list.add(mind.getTerms().add(a));
            }
            type = DataType.SET;
            value = list;
        } else if (o instanceof ArgumentsList) {
            List<ITerm> list = new ArrayList<>();
            for (IArgument a : (ArgumentsList) o) {
                list.add(a.getValue(mind));
            }
            type = DataType.SET;
            value = list;
        } else if (!(o instanceof String)) {
            o = o.toString();
        }

        if (value == null) {
            if (o instanceof String) {
                String token = ((String) o).trim();
                if (token.isEmpty()) {
                    token = (String) o;
                }
                try {
                    Date d;
                    if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("\'") && token.endsWith("\'"))) {
                        token = token.substring(1);
                        token = token.substring(0, token.length() - 1);
                        if (Tools.isInterval(token)) {
                            type = DataType.INTERVAL;
                            value = constructInterval(token);
                        } else if (Tools.isPeriod(token)) {
                            type = DataType.PERIOD;
                            value = token;
                        } else if ((d = Tools.parseDate(token)) != null) {
                            type = DataType.DATE;
                            value = d;
                        } else {
                            type = DataType.STRING;
                            value = token;
                        }
                    } else if (Tools.isBlob(token)) {
                        type = DataType.BLOB;
                        value = constructBlob(token);
                    } else if (Tools.isInterval(token)) {
                        type = DataType.INTERVAL;
                        value = constructInterval(token);
                    } else if (Tools.isPeriod(token)) {
                        type = DataType.PERIOD;
                        value = token;
                    } else if ((d = Tools.parseDate(token)) != null) {
                        type = DataType.DATE;
                        value = d;
                    } else if (Tools.isFloat(token)) {
                        type = DataType.NUMERIC;
                        value = Double.parseDouble(token);
                    } else if (Tools.isInt(token)) {
                        type = DataType.NUMERIC;
                        value = Double.parseDouble(token);
                    } else {
                        type = DataType.STRING;
                        value = token;
                    }
                } catch (NumberFormatException ex) {
                    type = DataType.STRING;
                    value = token;
                }
            } else {
                type = DataType.TERM;
                value = o;
            }
        }
        getHash();
    }

    private byte[] constructBlob(String str) {
        byte[] buffer = new byte[]{};
        if (str.charAt(0) == '#') {
            str = str.substring(1);
            str = str.replace("-", "");
            str = str.replace("\n", "");
            str = str.replace("\\n", "");
            str = str.replace("\r", "");
            str = str.replace(" ", "");
            if (!str.isEmpty()) {
                buffer = new byte[str.length() / 2 + (str.length() % 2 == 0 ? 0 : 1)];
                for (int i = 0; i < buffer.length; ++i) {
                    String sub = str.substring(i * 2);
                    if (sub.length() == 1) {
                        sub = "0" + sub;
                    } else if (sub.length() > 2) {
                        sub = sub.substring(0, 2);
                    }
                    buffer[i] = (byte) (Integer.parseInt(sub, 16) & 0xFF);
                }
            }
        }
        return buffer;
    }

    private Object constructInterval(String ch) throws Exception {
        if (ch.contains("..")) {
            if (ch.startsWith("{") && ch.endsWith("}")) {
                ch = ch.substring(1, ch.length() - 1);
            }
            List<ITerm> list = new ArrayList<>();
            for (String s : ch.split("\\.\\.")) {
                if (!s.trim().isEmpty()) {
                    ITerm t = mind.getTerms().add(s);
                    list.add(t);
                }
            }
            return list;
        } else {
            return ch;
        }
    }

    @Override
    public DataType getType() {
        return type;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    public IRule getRule(Mind mind) throws Exception {
        if (rule == null) {
            rule = mind.getRules().get(ruleId);
        }
        return rule;
    }

    public void setRule(IRule r) {
        this.rule = r;
        this.ruleId = r.getId();
    }

    public boolean isCVariable() {
        return index > 0;
    }

    @Override
    public boolean equalsTo(ITerm term) {
        return equalsTo((Term) term);
    }

    public boolean isXVariable() {
        return parentId > 0;
    }

    public void toCVariable() {
        if (isXVariable()) {
            value = String.format("%c%d", Enums.CVC, index);
            parentId = -1;
            getHash();
        }
    }

    public String formatValue() {
        if (type == DataType.INTERVAL) {
            if (value instanceof Collection && ((Collection) value).size() == 2) {
                return ((Collection) value).toArray()[0].toString()
                        + ".."
                        + ((Collection) value).toArray()[1].toString();
            } else {
                return value.toString();
            }
        } else if (type == DataType.SET) {
            String s = "";
            for (Object a : ((Collection) value)) {
                if (!s.isEmpty()) {
                    s += ",";
                }
                s += a.toString();
            }
            return "[" + s + "]";
        } else if (type == DataType.BLOB) {
            String s = "#";
            for (byte x : ((byte[]) value)) {
                s += String.format("%02X", x & 0xFF);
            }
            return s;
//        } else if (type == DataType.STRING) {
//            if( ((String) value).contains(" ") || ((String) value).contains("\t") || ((String) value).contains("\n") || ((String) value).contains("\r")) {
//                return "\"" + value + "\"";
//            } else
//            if (value.toString().trim().isEmpty()) {
//                return "\"" + value + "\"";
//            } else {
//                return value.toString();
//            }
        } else {
            return value.toString();
        }
    }

    @Override
    public String toString() {
        if (value != null) {
            if (isCVariable()) {
                switch (mind.getDebugLevel() & 0x00FF) {
                    case Enums.DEBUG_LEVEL_DEBUG:
                        return formatValue();
                    default:
                        try {
                            return getName(mind).toString();
                        } catch (Exception e) {
                            e.printStackTrace(System.err);
                            return "";
                        }
                }
            } else if (type == DataType.DATE) {
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z").format((Date) value);
            } else {
                return formatValue();
            }
        } else {
            return "";
        }
    }

    @Override
    public int getHash() {
        if (hash == 0) {
            if (value == null) {
                hash = 0;
            } else {
                hash = 3;
                switch (type) {
                    case PERIOD:
                        long time = Tools.intervalToTime((String) value);
                        hash = 47 * hash + (int) (time ^ (time >>> 32));
                        break;
                    case INTERVAL:
                        hash = 47 * hash + ((Term) ((List<ITerm>) value).get(0)).getHash();
                        hash = 47 * hash + ((Term) ((List<ITerm>) value).get(1)).getHash();
                        break;
                    case TERM:
                        hash = 47 * hash + ((Term) value).getHash();
                        break;
                    case DATE:
                        long date = ((Date) value).getTime();
                        hash = 47 * hash + (int) (date ^ (date >>> 32));
                        break;
                    case SET:
                        for (ITerm t : (List<ITerm>) value) {
                            hash = 47 * hash + ((Term) t).getHash();
                        }
                        break;
                    case BLOB:
                        hash = Arrays.hashCode((byte[]) value);
                        break;
                    case STRING:
                        hash = value.hashCode();
                        break;
                    case NUMERIC:
                        hash = Double.hashCode((Double) value);
                }
            }
        }
        return hash;
    }

    @Override
    public boolean equalsTo(Term to) {
        if (type == to.getType() && getHash() == to.getHash()) {
            switch (type) {
                case BLOB:
                    return Arrays.equals((byte[]) value, (byte[]) to.getValue());
                case SET:
                    if (((List<ITerm>) value).size() == ((List<ITerm>) to.getValue()).size()) {
                        for (ITerm t : (List<ITerm>) value) {
                            if (!((List<ITerm>) to.getValue()).contains(t)) {
                                return false;
                            }
                        }
                        return true;
                    } else {
                        return false;
                    }
                case TERM:
                    return ((Term) value).equalsTo((Term) to.getValue());
                case INTERVAL:
                    return ((Term) ((List<ITerm>) value).get(0)).equalsTo((Term) ((List<ITerm>) to.getValue()).get(0))
                            && ((Term) ((List<ITerm>) value).get(1)).equalsTo((Term) ((List<ITerm>) to.getValue()).get(1));
                case PERIOD:
                    return Tools.intervalToTime((String) value) == Tools.intervalToTime((String) to.getValue());
                default:
                    return value.equals(to.getValue());
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (int) (id ^ (id >>> 32));
        return hash;
//        return ("" + id).hashCode();
    }

    @Override
    public boolean equals(Object t) {
        return t != null && t instanceof Term && ((Term) t).getId() == id;
    }

    @Override
    public Object getValue() {
        return value;
    }

//    public void setValue(Object value) {
//        this.value = value;
//        getHash();
//    }

    public ITerm getName(Mind mind) throws Exception {
        if (name == null) {
            name = mind.getTerms().get(nameId);
        }
        return name;
    }

    public void setName(ITerm name) {
        this.name = name;
        this.nameId = name.getId();
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }


    @Override
    public int compareTo(Object oo) {
        if (oo instanceof Term) {
            Term o = (Term) oo;

            if (o == null || value == null || type != o.getType()) {
                return -2;
            } else if (isCVariable() && o.isCVariable()) {
                return Integer.valueOf(index).compareTo(o.getIndex());
            } else if ((type == DataType.INTERVAL || type == DataType.SET) && value instanceof Collection) {
                if (o.getValue() instanceof Collection) {
                    if (((Collection) value).size() != ((Collection) o.getValue()).size()) {
                        return -2;
                    } else {
                        int c = 0;
                        Object[] a = ((Collection) value).toArray();
                        Object[] b = ((Collection) o.getValue()).toArray();
                        for (int i = 0; i < a.length; ++i) {
                            c = ((Comparable) a[i]).compareTo(b[i]);
                            if (c != 0) {
                                break;
                            }
                        }
                        return c;
                    }
                } else {
                    return -2;
                }
            } else if (value instanceof Number && o.getValue() instanceof Number) {
                double diff = Math.abs(((Number) value).doubleValue() - ((Number) o.getValue()).doubleValue());
                if (diff < FLT_EPSILON) {
                    return 0;
                } else {
                    return ((Comparable) value).compareTo(o.getValue());
                }
            } else if (type == DataType.BLOB) {
                //TODO: Для совместимости с 8 ???
//                return -1; //Arrays.compare((byte[]) value, (byte[]) o.getValue());
                return compareBytes((byte[]) value, (byte[]) o.getValue());
            } else {
                return ((Comparable) value).compareTo(o.getValue());
            }
        } else {
            return Integer.valueOf(index).compareTo(((TVariable) oo).getIndex());
        }
    }

    private int compareBytes(byte[] a, byte[] b) {
        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; ++i) {
            if ((a[i] & 0xFF) != (b[i] & 0xFF)) {
                return (a[i] & 0xFF) - (b[i] & 0xFF);
            }
        }
        if (a.length == b.length) {
            return 0;
        } else {
            return a.length - b.length;
        }
    }

    @Override
    public boolean isEmpty() {
        return value == null;
    }

    public void clear() {
        value = null;
        hash = 0;
    }

    @Override
    public Mind getMind() {
        return mind;
    }

    @Override
    public Term setMind(Mind mind) {
        this.mind = mind;
        return this;
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
        return UnitType.TERM;
    }

    @Override
    public long getMindId() {
        return mindId;
    }

    @Override
    public void setMindId(long mindId) {
        this.mindId = mindId;
    }

//    public Term commit(Mind m) throws Exception {
//        Term term = m.getTerms().add(this);
//        term.setMind(m);
//        return term;
//    }


    public long getRuleId() {
        return ruleId;
    }

    public Set<Long> getSlaves() {
        return slaves;
    }

    //    public Set<Long> getChilds() {
//        return childs;
//    }
//
    public ITerm getParent(Mind mind) throws Exception {
        if (parent == null && parentId > 0) {
            parent = mind.getTerms().get(parentId);
        }
        return parent;
    }

    public void setParent(ITerm parent) {
        this.parent = parent;
        this.parentId = parent.getId();
    }

    public long getParentId() {
        return parentId;
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
        map.put("deleted", isDeleted(mind));
        map.put("type", type.name());
        map.put("value", value);
        map.put("hash", hash);
        map.put("index", index);
        map.put("name_id", nameId);
        map.put("rule_id", ruleId);
        map.put("parent_id", parentId);

        if (nameId != -1) {
            map.put("name", getName((Mind) mind).getValue());
        }
        if (ruleId != -1) {
            map.put("rule", getRule((Mind) mind).getOrigin());
        }
        if (parentId != -1) {
            map.put("parent", getParent((Mind) mind).getValue());
        }

        return map;
    }

    @Override
    public Term applyMap(Map<String, Object> map) throws Exception {
        id = Long.parseLong(map.get("id") + "");
        mindId = Long.parseLong(map.get("mind_id") + "");
        boolean deleted = Boolean.parseBoolean(map.get("deleted") + "");
        if (deleted) {
            setDeleted(true, mind);
        }
        type = DataType.valueOf(map.get("type") + "");
        construct(map.get("value"));
        index = Integer.parseInt(map.get("index") + "");
        nameId = Long.parseLong(map.get("name_id") + "");
        ruleId = Long.parseLong(map.get("rule_id") + "");
        parentId = Long.parseLong(map.get("parent_id") + "");
        name = null;
        rule = null;
        parent = null;

        return this;
    }

}

