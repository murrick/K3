package kanger.compiler;

/**
 * Created by murray on 04.06.15.
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
    private PTree right = null;            // Right branch ptr

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
        return right;
    }

    public void setRight(PTree right) {
        this.right = right;
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
        return (left != null ? "[" + left.toString() + "] <- " : "") + name + (right != null ? " -> [" + right.toString() + "]": "");
    }
}
