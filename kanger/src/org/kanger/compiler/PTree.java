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

/**
 * Created by Dmitry G. Quznetsov on 04.06.15.
 */
public class PTree {
    private String name = null;     // Token name
    private int post = 0;               // Postfix or Prefix form (POST || PRED */
    private int dir = 0;                // Saved direction
    private int prior = 0;              // Operation priority
    //private int mode;               // ID Mode (FUNC | ARRAY | CAST
    private boolean system = false;
    private int range = 0;                 // Parameters count
    private PTree left = null;             // Left branch ptr
    private PTree rule = null;            // Right branch ptr

    private int next = 0;               // Actual branch for insert (&left || &compiler)
    private int pos = 0;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPost() {
        return post;
    }

    public void setPost(int post) {
        this.post = post;
    }

    public int getDir() {
        return dir;
    }

    public void setDir(int dir) {
        this.dir = dir;
    }

    public int getPrior() {
        return prior;
    }

    public void setPrior(int prior) {
        this.prior = prior;
    }

    public int getRange() {
        return range;
    }

    public void setRange(int cp) {
        this.range = cp;
    }

    public PTree getLeft() {
        return left;
    }

    public void setLeft(PTree left) {
        this.left = left;
    }

    public PTree getRight() {
        return rule;
    }

    public void setRule(PTree rule) {
        this.rule = rule;
    }

    public int getNext() {
        return next;
    }

    public void setNext(int next) {
        this.next = next;
    }

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }

    public int getPos() {
        return pos;
    }

    public void setPos(int pos) {
        this.pos = pos;
    }

    public String toString() {
        return (left != null ? "[" + left.toString() + "] <- " : "") + name + (rule != null ? " -> [" + rule.toString() + "]" : "");
    }
}
