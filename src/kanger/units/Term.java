package kanger.units;

import kanger.User;
import kanger.compiler.PTree;
import kanger.enums.DataType;
import kanger.enums.Enums;
import kanger.enums.Tools;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
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
public class Term implements Comparable<Object>, Externalizable, Identifiable<Term> {

    public static final double FLT_EPSILON = 0.00000000001;

    private static final long serialVersionUID = 196402070008L;

    private long id = -1;                // Идентификатор
    private DataType type = DataType.VOID;
    private Object value = null;

    private int index = 0;              // Индекс c-переменной
    private Term name = null;             // Оригинальное имя c-переменной
    private Right right = null;          // Ссылка на правило

//    private Term next = null;      // Следующая запись
    private User user = null;

    public Term() {
    }

    public Term(User user) {
        this.user = user;
    }

    public Term(Object str, User user) throws Exception {
        this.user = user;
        construct(str);
    }

    @Override
    public void readExternal(ObjectInput din) throws IOException, ClassNotFoundException {
        id = din.readLong();
        type = DataType.values()[din.readInt()];
        switch (type) {
            case DATE:
                value = new Date(din.readLong());
                break;
            case NUMERIC:
                value = din.readDouble();
                break;
            case SET:
            case INTERVAL:
                if (din.readBoolean()) {
                    value = new ArrayList<Term>();
                    for (int i = 0; i < din.readInt(); ++i) {
                        Term t = (Term) din.readObject();
                        ((List<Term>) value).add(t);
                    }
                } else {
                    value = din.readUTF();
                }
                break;
            case STRING:
                value = din.readUTF();
                break;
            case TERM:
                value = din.readObject();
                break;
        }

        index = din.readInt();
        if(index > 0) {
            name = (Term) din.readObject();
            right = (Right) din.readObject();
        }
    }

    @Override
    public void writeExternal(ObjectOutput dos) throws IOException {
        dos.writeLong(id);
        dos.writeInt(type.ordinal());
        switch (type) {
            case DATE:
                dos.writeLong(((Date) value).getTime());
                break;
            case NUMERIC:
                dos.writeDouble((double) value);
                break;
            case INTERVAL:
            case SET:
                if (value instanceof Collection) {
                    dos.writeBoolean(true);
                    dos.writeInt(((Collection) value).size());
                    for (Term t : (Collection<Term>) value) {
                        dos.writeObject(t);
                    }
                } else {
                    dos.writeBoolean(false);
                    dos.writeUTF((String) value);
                }
                break;
            case STRING:
                dos.writeUTF((String) value);
                break;
            case TERM:
                dos.writeObject(value);
                break;
        }
        dos.writeInt(index);
        if(index > 0) {
            dos.writeObject(name);
            dos.writeObject(right);
        }
    }

//    @Override
//    public void linkExternal(User user) {
//        this.user = user;
//    }

    private void construct(Object o) throws Exception {
        value = null;
        if (o instanceof Number) {
            type = DataType.NUMERIC;
            value = ((Number) o).doubleValue();
        } else if (o instanceof Date) {
            type = DataType.DATE;
            value = o;
        } else if (o instanceof PTree) {
            if ("..".equals(((PTree) o).getName())) {
                List<Term> list = new ArrayList<>();
                list.add(user.getMind().getTerms().add(((PTree) o).getLeft().getName()));
                list.add(user.getMind().getTerms().add(((PTree) o).getRight().getName()));
                type = DataType.INTERVAL;
                value = list;
            }
        } else if (o instanceof ArgList) {
            List<Term> list = new ArrayList<>();
            for(Argument a : (ArgList) o) {
                list.add(a.getValue());
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

    private Object conatructInterval(String ch) throws Exception {
        if (ch.contains("..")) {
            if(ch.startsWith("{") && ch.endsWith("}")) {
                ch = ch.substring(1, ch.length()-1);
            }
            List<Term> list = new ArrayList<>();
            for (String s : ch.split("\\.\\.")) {
                if (!s.trim().isEmpty()) {
                    Term t = user.getMind().getTerms().add(s);
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

    public Right getRight() {
        return right;
    }

    public void setRight(Right r) {
        this.right = r;
    }

    public boolean isCVariable() {
        return index > 0;
    }

    public String formatValue() {
        if (type == DataType.INTERVAL) {
            if (value instanceof Collection && ((Collection) value).size() == 2) {
                return "{" + ((Collection) value).toArray()[0].toString() + ".." + ((Collection) value).toArray()[1].toString() + "}";
            } else {
                return value.toString();
            }
        } else if (type == DataType.SET) {
            String s = "";
            for(Object a : ((Collection) value)) {
                if(!s.isEmpty()) {
                    s += ",";
                }
                s += a.toString();
            }
            return "{" + s + "}";
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
                        return name.toString();
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
        StringBuffer buffer = new StringBuffer();
        buffer.append(type.ordinal());
        buffer.append(value.hashCode());
         return buffer.toString().hashCode();
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

    public Term getName() {
        return name;
    }

    public void setName(Term name) {
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}

//TODO ТИП данных blob
