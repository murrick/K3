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

package org.kanger.stores;

import org.kanger.Mind;
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Quznetsov on 28.05.15.
 */
public class SolutionsStore implements IFactory<IRule> {

    private List<IRule> root = null;
    private boolean enableStore = true;

    private final transient Mind mind;

    public SolutionsStore(Mind mind) {
        this.mind = mind;
    }

    public void commit(SolutionsStore base) {
        if (!enableStore) {
            return;
        }
        clear();
        if (!base.isEmpty()) {
            if (root == null) {
                root = new ArrayList<>();
            }
            root.addAll(base.getRoot());
        }
    }

    public IRule add(IRule d) {
        if (!enableStore) {
            return null;
        }
        if (root == null) {
            root = new ArrayList<>();
        }
//        Solution s = new Solution(d);
        if (!root.contains(d)) {
            root.add(d);
        }
//        else {
//            d = root.get(root.indexOf(d));
//        }
        return d;
    }

    public boolean contains(Domain d) throws Exception {
        if (!isEmpty()) {
            for (IRule r : root) {
                if (!r.isDeleted(mind) && ((Rule) r).getDomain().equalsBase(d) && r.isAntc() == d.isAntc()) {
                    return true;
                }
            }
        }
        return false;
    }

//    public boolean contains(Right rt) throws Exception {
//        if (!isEmpty()) {
//            for (Right r : root) {
//                if (r.getDomain().equalsBase(rt.getDomain())) {
//                    return true;
//                }
//            }
//        }
//        return false;
//    }

    public void enable(boolean e) {
        enableStore = e;
    }

    public boolean isEnabled() {
        return enableStore;
    }

    public Rule get(int index) {
        return root.toArray(new Rule[]{})[index];
    }

//    public int find(Solution o) {
//        return root.indexOf(o);
//    }

    public List<IRule> getRoot() {
        return root;
    }

//    public void remove(Solution s) {
//        root.remove(s);
//    }

    public void clear() {
        if (enableStore) {
            root = null;
        }
    }

    @Override
    public IRule get(long id) throws Exception {
        return null;
    }

    public int size() {
        return root == null ? 0 : root.size();
    }

    public boolean isEmpty() {
        return root == null || root.isEmpty();
    }

    @Override
    public Iterator<IRule> iterator() {
        if (root == null) {
            root = new ArrayList<>();
        }
        return root.iterator();
    }
}
