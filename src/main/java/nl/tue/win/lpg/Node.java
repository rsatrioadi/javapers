package nl.tue.win.lpg;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Set;

public class Node extends HashMap<String, Object> {

    private final @NotNull String id;

    private final @NotNull Set<String> labels;

    public Node(@NotNull String id) {
        this.id = id;
        this.labels = Set.of();
    }

    public Node(@NotNull String id, String ... labels) {
        this.id = id;
        this.labels = Set.of(labels);
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull Set<String> getLabels() {
        return labels;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) return true;
        if (that == null || getClass() != that.getClass()) return false;
//        if (!super.equals(that)) return false;

        Node thatNode = (Node) that;

        return this.getId().equals(thatNode.getId())
                && this.getLabels().equals(thatNode.getLabels());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + getId().hashCode();
        result = 31 * result + getLabels().hashCode();
        return result;
    }
}
