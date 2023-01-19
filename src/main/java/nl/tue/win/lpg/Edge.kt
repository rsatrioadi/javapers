package nl.tue.win.lpg;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Set;

public class Edge extends HashMap<String, Object> {

    private final @NotNull Node source;

    private final @NotNull Node target;

    private final @NotNull Set<String> labels;

    public Edge(@NotNull Node source, @NotNull Node target) {
        this.source = source;
        this.target = target;
        this.labels = Set.of();
    }

    public Edge(@NotNull Node source, @NotNull Node target, String ... labels) {
        this.source = source;
        this.target = target;
        this.labels = Set.of(labels);
    }

    public @NotNull Node getSource() {
        return source;
    }

    public @NotNull Node getTarget() {
        return target;
    }

    public @NotNull Set<String> getLabels() {
        return labels;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) return true;
        if (that == null || getClass() != that.getClass()) return false;
//        if (!super.equals(that)) return false;

        Edge thatEdge = (Edge) that;

        return getSource().equals(thatEdge.getSource())
                && getTarget().equals(thatEdge.getTarget())
                && getLabels().equals(thatEdge.getLabels());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + getSource().hashCode();
        result = 31 * result + getTarget().hashCode();
        result = 31 * result + getLabels().hashCode();
        return result;
    }
}
