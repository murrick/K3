package kanger.primitives;

import kanger.User;
import kanger.enums.DataType;
import kanger.enums.Enums;
import kanger.enums.Tools;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Элемент словаря
 */
public class Term implements Comparable<Object> {

    public static final double FLT_EPSILON = 0.00000000001;

    private DataType type = DataType.VOID;
    private Object value = null;
    private long id = -1;                // Идентификатор
    private Right right = null;          // Ссылка на правило
    private Term next = null;      // Следующая запись

    private String name = "";             // Оригинальное имя c-переменной
    private int index = 0;              // Индекс c-переменной

    private User user = null;

    public Term(User user) {
        this.user = user;
    }

    public Term(Object str, User user) {
        this.user = user;
        construct(str);
    }

    public Term(DataInputStream din, User user) throws IOException, ClassNotFoundException {
        this.user = user;
        type = DataType.values()[din.readInt()];
        name = din.readUTF();

        index = din.readInt();
        id = din.readLong();
        user.getMind().getTermsLink().put(this,din.readLong());

        switch (type) {
            case DATE:
                value = new Date(din.readLong());
                break;
            case NUMERIC:
                value = din.readDouble();
                break;
            case INTERVAL:
            case STRING:
                value = din.readUTF();
                break;
        }

    }

    private void construct(Object o) {
        value = null;
        if (o instanceof Number) {
            type = DataType.NUMERIC;
            value = ((Number) o).doubleValue();
        } else if (o instanceof Date) {
            type = DataType.DATE;
            value = o;
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
                        if ((d = Tools.parseDate(token)) != null) {
                            type = DataType.DATE;
                            value = d;
                        } else if (Tools.isInterval(token)) {
                            type = DataType.INTERVAL;
                            value = conatructInterval(token);
                        } else {
                            type = DataType.STRING;
                            value = token;
                        }
                    } else if ((d = Tools.parseDate(token)) != null) {
                        type = DataType.DATE;
                        value = d;
                    } else if (Tools.isInterval(token)) {
                        type = DataType.INTERVAL;
                        value = conatructInterval(token);
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

    private Object conatructInterval(String ch) {
        if (ch.contains("..")) {
            List<Term> list = new ArrayList<>();
            for (String s : ch.split("\\.\\.")) {
                if (!s.trim().isEmpty()) {
                    Term t = user.getMind().getTerms().add(new Term(s, user));
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

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Right getRight() {
        return right;
    }

    public void setRight(Right r) {
        this.right = r;
    }

    public Term getNext() {
        return next;
    }

    public void setNext(Term next) {
        this.next = next;
    }

    public boolean isCVariable() {
        return index > 0;
    }

    public String formatValue() {
        if (type == DataType.INTERVAL) {
            if (value instanceof Collection && ((Collection) value).size() == 2) {
                return ((Collection) value).toArray()[0].toString() + ".." + ((Collection) value).toArray()[1].toString();
            } else {
                return value.toString();
            }
        } else {
            return value.toString();
        }
    }

    public String toString() {
        if (value != null) {
            if (isCVariable()) {
                switch (user.getMind().getDebugLevel() & 0x00FF) {
                    case Enums.DEBUG_LEVEL_DEBUG:
                        return formatValue();
                    default:
                        return name;
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

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(id);
        dos.writeLong(right.getId());
        dos.writeInt(type.ordinal());
        switch (type) {
            case DATE:
                dos.writeLong(((Date) value).getTime());
                break;
            case NUMERIC:
                dos.writeDouble((double) value);
                break;
            case INTERVAL:
                dos.writeUTF((String) value);
                break;
            case STRING:
                dos.writeUTF((String) value);
                break;
            case TERM:
                new ObjectOutputStream(dos).writeObject(value);
                break;
        }
        dos.writeUTF(name);
        dos.writeInt(index);
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
            } else if (type == DataType.INTERVAL && value instanceof Collection) {
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

}

//TODO ТИП данных blob
