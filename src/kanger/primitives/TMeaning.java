package kanger.primitives;

public class TMeaning {
    private TVariable variable = null;
    private Term value = null;
    private Solution solution = null;

    public TMeaning(TVariable t) {
        this.variable = t;
        this.value = t.getValue();
    }

    public TVariable getVariable() {
        return variable;
    }

    public void setVariable(TVariable variavle) {
        this.variable = variavle;
    }

    public Term getValue() {
        return value;
    }

    public void setValue(Term value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return variable.getVarName() + (value != null ? "=" + value.toString() : "");
    }

    public Solution getSolution() {
        return solution;
    }

    public void setSolution(Solution solution) {
        this.solution = solution;
    }

    @Override
    public boolean equals(Object o) {
        return o != null
                && value != null
                && (o instanceof TMeaning)
                && ((TMeaning) o).getVariable().getId() == variable.getId()
                && ((TMeaning) o).getValue().getId() == value.getId();
    }
}
