package kanger.primitives;

import java.io.*;
import java.text.*;
import java.util.*;
import kanger.*;
import kanger.enums.*;
import kanger.interfaces.*;

/**
 * Created by Dmitry G. Qusnetsov on 20.05.15.
 * <p>
 * Элемент словаря
 */
public class Term implements IValue, Comparable<Object> {


    private DataType type = DataType.VOID;
    private Object value = null;
    private long id = -1;                // Идентификатор
    private Right right = null;          // Ссылка на правило
    private Term next = null;      // Следующая запись

    private String name = "";             // Оригинальное имя c-переменной
    private int index = 0;              // Индекс c-переменной

    private Mind mind = null;

    public Term(Mind mind) {
        this.mind = mind;
    }

//    public Term(StringBuffer str, int pos) throws ParseErrorException {
//        int c = pos;
//        while (c < str.length() && Parser.str.charAt(c) <= ' ') {
//            ++c;
//        }
//        int stop = c;
//        String tmp;
//        if ((str.charAt(c) == '\"' && (stop = str.indexOf("\"", c + 1)) != -1) || (str.charAt(c) == '\'' && (stop = str.indexOf("\'", c + 1)) != -1)) {
//            ++stop;
//        } else {
//            while (stop < str.length() && !Tools.isKey(str.charAt(stop))) {
//                ++stop;
//            }
//        }
//
////        sourceLength = stop - c;
//        construct(str.substring(c, stop));
//    }

    public Term(Object str, Mind mind) {
//        sourceLength = str.length();
        this.mind = mind;
        construct(str);
    }

    public Term(DataInputStream din, Mind mind) throws IOException, ClassNotFoundException {
        id = din.readLong();
        this.mind = mind;
        mind.getDictionaryLinks().put(this, din.readLong());
        int typeIndex = din.readInt();
        type = DataType.values()[typeIndex];
        switch (type) {
            case DATE:
                value = new Date(din.readLong());
                break;
            case NUMERIC:
                value = din.readDouble();
                break;
            case INTERVAL:
                value = din.readUTF();
                break;
            case STRING:
                value = din.readUTF();
                break;
            case TERM:
                value = new ObjectInputStream(din).readObject();
                break;
        }

        name = din.readUTF();
        index = din.readInt();
//        sourceLength = din.readInt();
//        token = din.readUTF();
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
//                if (token.contains(" ") || token.contains("\r") || token.contains("\t")) {
//                    type = DataType.STRING;
//                    token = token.replaceAll("\r", "");
//                    token = token.replaceAll("\t", "");
//                    token = token.replaceAll(" ", "");
//                }
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
                    Term t = mind.getTerms().add(new Term(s, mind));
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

    public void setType(DataType type) {
        this.type = type;
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

    public boolean isCVar() {
        return index > 0;
//        return value != null
//                && type == DataType.STRING
//                && !value.toString().isEmpty()
//                && value.toString().charAt(0) == Enums.CVC;
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
            if (isCVar()) {
                switch (mind.getDebugLevel() & 0x00FF) {
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

    public String asString() {
        String str = toString();
        if (str.contains(" ") || str.contains("\t") || str.contains("\r")) {
            str = "\"" + str.replace("\"", "\\\"").replace("\'", "\\\'") + "\"";
        }
        return str;
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
    public boolean equals(Object t) {
        return t != null && t instanceof Term && ((Term) t).getId() == id;
//        if (t instanceof Term) {
//            return ((Term) t).id == id;
//        } else if (value == null && t == null) {
//            return true;
//        } else if (value == null || t == null) {
//            return false;
//        } else {
//            return value.equals(t);
//        }
    }

    public Object getVal() {
        return value;
    }

    public void setVal(Object value) {
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
            } else if (isCVar() && o.isCVar()) {
                return Integer.valueOf(index).compareTo(o.getIndex());
            } else if (type == DataType.INTERVAL && value instanceof Collection) {
                if (o.getVal() instanceof Collection) {
                    if (((Collection) value).size() != ((Collection) o.getVal()).size()) {
                        return -2;
                    } else {
                        int c = 0;
                        Object[] a = ((Collection) value).toArray();
                        Object[] b = ((Collection) o.getVal()).toArray();
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
            } else {
                return ((Comparable) value).compareTo(o.getVal());
            }
//            return c > 0 ? 1 : (c < 0 ? -1 : 0);
        } else {
            return Integer.valueOf(index).compareTo(((TVariable) oo).getIndex());
        }
    }
    
    @Override
    public Term getValue() {
        return this;
    }

    @Override
    public Object setValue(Term term) {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return value == null;
    }

    @Override
    public void clear() {
        value = null;
    }

    @Override
    public boolean isTSet() {
        return false;
    }

    @Override
    public boolean isFSet() {
        return false;
    }

    @Override
    public boolean isVSet() {
        return false;
    }

    @Override
    public boolean isTerm() {
        return true;
    }

    @Override
    public TVariable getT() {
        return null;
    }

    @Override
    public Function getF() {
        return null;
    }

    @Override
    public TValue getV() {
        return null;
    }

    
}
