package kanger.compiler;

/**
 * Created by murray on 26.06.15.
 */
public class Operation {
    private String name;            // Operation name
    private String subst;           // Substitution name
    private int prior;              // Operation pryority
    private int range;              // Number of parameters
    private int dir;                // Direction: L->R or R->L
    private boolean post;           // Allow postfix form
    private boolean repl;           // Must be just replaced w/o making function syntax

    public Operation(String name, String subst, int prior, int cp, int dir, boolean post, boolean repl) {
        this.name = name;
        this.subst = subst;
        this.prior = prior;
        this.range = cp;
        this.dir = dir;
        this.post = post;
        this.repl = repl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubst() {
        return subst;
    }

    public void setSubst(String subst) {
        this.subst = subst;
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

    public void setRange(int range) {
        this.range = range;
    }

    public int getDir() {
        return dir;
    }

    public void setDir(int dir) {
        this.dir = dir;
    }

    public boolean isPost() {
        return post;
    }

    public void setPost(boolean post) {
        this.post = post;
    }

    public boolean isRepl() {
        return repl;
    }

    public void setRepl(boolean repl) {
        this.repl = repl;
    }
}

