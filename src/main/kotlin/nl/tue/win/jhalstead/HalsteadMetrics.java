package nl.tue.win.jhalstead;

import java.util.List;
import java.util.Map;

public class HalsteadMetrics {
    public final String elementID;
    public final String elementKind;

    public final Integer n1; // Unique operators
    public final Integer n2; // Unique operands
    public final Integer N1; // Total operators
    public final Integer N2; // Total operands

    public final Integer vocabulary;
    public final Integer length;
    public final Double volume;
    public final Double difficulty;
    public final Double effort;
    public final Double estimatedBugs;

    // Constructor
    public HalsteadMetrics(String elementID, String elementKind, Integer n1, Integer n2, Integer N1, Integer N2) {
        this.elementID = elementID;
        this.elementKind = elementKind;
        this.n1 = n1;
        this.n2 = n2;
        this.N1 = N1;
        this.N2 = N2;
        this.vocabulary = n1 + n2;
        this.length = N1 + N2;
        this.volume = length * (Math.log(vocabulary) / Math.log(2));
        this.difficulty = (n1 / 2.0) * ((double) N2 / n2);
        this.effort = difficulty * volume;
        this.estimatedBugs = volume / 3000;
    }

    private HalsteadMetrics(String elementID, String elementKind, Integer length, Double volume, Double effort) {
        this.elementID = elementID;
        this.elementKind = elementKind;
        this.n1 = 0;
        this.n2 = 0;
        this.N1 = 0;
        this.N2 = 0;
        this.vocabulary = -1;
        this.length = length;
        this.volume = volume;
        this.difficulty = Double.NaN;
        this.effort = effort;
        this.estimatedBugs = volume/3000;
    }

    public static HalsteadMetrics aggregate(String elementID, String elementKind, List<HalsteadMetrics> halsteadMetrics) {
        var totalLength = halsteadMetrics.stream().map(m -> m.length).reduce(0, Integer::sum);
        var totalVolume = halsteadMetrics.stream().map(m -> m.volume).reduce(0d, Double::sum);
        var totalEffort = halsteadMetrics.stream().map(m -> m.effort).reduce(0d, Double::sum);
        return new HalsteadMetrics(
                elementID,
                elementKind,
                totalLength,
                totalVolume,
                totalEffort
        );
    }

    @Override
    public String toString() {
        return "Halstead Metrics:\n" +
                "  Unique Operators (n1): " + n1 + "\n" +
                "  Unique Operands (n2): " + n2 + "\n" +
                "  Total Operators (N1): " + N1 + "\n" +
                "  Total Operands (N2): " + N2 + "\n" +
                "  Vocabulary: " + vocabulary + "\n" +
                "  Length: " + length + "\n" +
                "  Volume: " + volume + "\n" +
                "  Difficulty: " + difficulty + "\n" +
                "  Effort: " + effort + "\n" +
                "  Estimated Bugs: " + estimatedBugs;
    }

    public Map<String, Object> toMap(Object replaceNaNWith) {
        return Map.ofEntries(
                Map.entry("id", elementID),
                Map.entry("kind", elementKind),
//                Map.entry("uniqueOperators", n1),
//                Map.entry("uniqueOperands", n2),
//                Map.entry("totalOperators", N1),
//                Map.entry("totalOperands", N2),
                Map.entry("vocabulary", vocabulary),
                Map.entry("length", length),
                Map.entry("volume", volume.isNaN() ? replaceNaNWith : volume),
                Map.entry("difficulty", difficulty.isNaN() ? replaceNaNWith : difficulty),
                Map.entry("effort", effort.isNaN() ? replaceNaNWith : effort),
                Map.entry("estimatedBugs", estimatedBugs.isNaN() ? replaceNaNWith : estimatedBugs)
        );
    }

    public Map<String, Object> toMap() {
        return toMap(-1);
    }
}
