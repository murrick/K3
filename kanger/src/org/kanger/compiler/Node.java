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

package org.kanger.compiler;

import org.kanger.units.Domain;

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
