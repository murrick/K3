package org.kanger.units;

import org.kanger.Mind;
import org.kanger.compiler.PTree;
import org.kanger.enums.DataType;
import org.kanger.enums.Enums;
import org.kanger.enums.Tools;
import org.kanger.enums.UnitType;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.IUnit;
import org.kanger.primitives.ArgList;
import org.kanger.primitives.Argument;
import org.kanger.storage.ByteBuffer;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Элемент словаря
 */
public class Term implements Comparable<Object>, IUnit<Term> {

    public static final double FLT_EPSILON = 0.00000000001;

    private static final long serialVersionUID = 196402070008L;

    private long id = -1;                // Идентификатор
    private long mindId = -1;                                   // id транзакции
    private DataType type = DataType.VOID;
    private Object value = null;

    private int index = 0;              // Индекс c-переменной
    private Term name = null;             // Оригинальное имя c-переменной
    private Right right = null;          // Ссылка на правило

    //    private Term next = null;      // Следующая запись
    private Mind mind = null;

    private transient boolean deleted = false;
    private transient long nameId = -1;
    private transient long rightId = -1;

    public Term() {
    }

    public Term(Mind mind) {
        this.mind = mind;
    }

    public Term(Object str, Mind mind) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        this.mind = mind;
        construct(str);
    }

    public ByteBuffer pack() {
        ByteBuffer packet = new ByteBuffer()
                .putLong(id)
                .putLong(mindId)
                .putByte(deleted ? 1 : 0)
                .putInt(type.ordinal());
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
            packet.putLong(rightId);
        }
        return packet.createMarked();
    }

    public Term apply(ByteBuffer packet) throws OutOfBufferException {
        id = packet.getLong();
        mindId = packet.getLong();
        deleted = packet.getByte() != 0;
        type = DataType.values()[packet.getInt()];
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
            rightId = packet.getLong();
        }
        return this;
    }

    private void construct(Object o) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
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
                List<Term> list = new ArrayList<>();
                list.add(mind.getTerms().add(((PTree) o).getLeft().getName()));
                list.add(mind.getTerms().add(((PTree) o).getRight().getName()));
                type = DataType.INTERVAL;
                value = list;
            }
        } else if (o instanceof Term[]) {
            List<Term> list = new ArrayList<>();
            list.add(((Term[]) o)[0]);
            list.add(((Term[]) o)[1]);
            type = DataType.INTERVAL;
            value = list;
        } else if (o instanceof ArgList) {
            List<Term> list = new ArrayList<>();
            for (Argument a : (ArgList) o) {
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
                try {
                    Date d;
                    if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("\'") && token.endsWith("\'"))) {
                        token = token.substring(1);
                        token = token.substring(0, token.length() - 1);
                        if (Tools.isInterval(token)) {
                            type = DataType.INTERVAL;
                            value = conatructInterval(token);
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
                        value = conatructInterval(token);
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

    private Object conatructInterval(String ch) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (ch.contains("..")) {
            if (ch.startsWith("{") && ch.endsWith("}")) {
                ch = ch.substring(1, ch.length() - 1);
            }
            List<Term> list = new ArrayList<>();
            for (String s : ch.split("\\.\\.")) {
                if (!s.trim().isEmpty()) {
                    Term t = mind.getTerms().add(s);
                    list.add(t);
                }
            }
            return list;
        } else {
            return ch;
        }
    }

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

    public Right getRight() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (right == null) {
            right = mind.getRights().load(rightId);
        }
        return right;
    }

    public void setRight(Right r) {
        this.right = r;
        this.rightId = r.getId();
    }

    public boolean isCVariable() {
        return index > 0;
    }

    public String formatValue() {
        if (type == DataType.INTERVAL) {
            if (value instanceof Collection && ((Collection) value).size() == 2) {
                return "("
                        + ((Collection) value).toArray()[0].toString()
                        + ".."
                        + ((Collection) value).toArray()[1].toString() + ")";
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
        } else {
            return value.toString();
        }
    }

    public String toString() {
        if (value != null) {
            if (isCVariable()) {
                switch (mind.getDebugLevel() & 0x00FF) {
                    case Enums.DEBUG_LEVEL_DEBUG:
                        return formatValue();
                    default:
                        try {
                            return getName().toString();
                        } catch (IOException | ClassNotFoundException | OutOfBufferException | RuntimeErrorException e) {
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
        int hash = 3;
        hash = 47 * hash + type.ordinal();
        hash = 47 * hash + Objects.hashCode(value);
        return hash;
    }

    @Override
    public boolean equalsTo(Term to) {
        return compareTo(to) == 0;
    }

    @Override
    public int hashCode() {
        return ("" + id).hashCode();
    }

    @Override
    public boolean equals(Object t) {
        return t != null && t instanceof Term && ((Term) t).getId() == id;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public Term getName() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (name == null) {
            name = mind.getTerms().load(nameId);
        }
        return name;
    }

    public void setName(Term name) {
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
                return Arrays.compare((byte[]) value, (byte[]) o.getValue());
            } else {
                return ((Comparable) value).compareTo(o.getValue());
            }
        } else {
            return Integer.valueOf(index).compareTo(((TVariable) oo).getIndex());
        }
    }

    public boolean isEmpty() {
        return value == null;
    }

    public void clear() {
        value = null;
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
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public void setDeleted() {
        deleted = true;
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

}

