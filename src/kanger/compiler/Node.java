package kanger.compiler;

import kanger.primitives.Domain;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 * <p/>
 * Узел
 */
public class Node {
    public static final int RIGHT = 0;         /* Direction of temporary */
    public static final int DOWN = 1;          /* tree growing const */
    public static final int STILL = 2;

    private Domain d = null;            /* Предикат */
    private Node right = null;          /* Правый элемент */
    private Node down = null;           /* Нижний элемент */
    private Node branch = null;         /* Вложенный элемент */

    public Domain getD() {
        return d;
    }

    public void setD(Domain d) {
        this.d = d;
    }

    public Node getRight() {
        return right;
    }

    public void setRight(Node right) {
        this.right = right;
    }

    public Node getDown() {
        return down;
    }

    public void setDown(Node down) {
        this.down = down;
    }

    public Node getBranch() {
        return branch;
    }

    public void setBranch(Node branch) {
        this.branch = branch;
    }

//    public Node clone() {
//        Node n = null;
//        for (Node p = this; p != null; p = p.getDown()) {
//            Node x = new Node();
//            x.setD(p.getD());
//            x.setDown(n);
//            n = x;
//        }
//        return n;
//    }

}
