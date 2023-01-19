package nl.tue.win.lpg;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class Nodes extends HashSet<Node> {
    @Override
    public String toString() {
        Set<String> attrKeys = this.stream()
                .flatMap(n -> n.keySet().stream())
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return String.format("id,%s\n%s", String.join(",", attrKeys), this.stream()
                .sorted(Comparator.comparing(Node::getId))
                .map(n -> String.format("%s,%s", n.getId(),
                        attrKeys.stream()
                                .map(k -> {
                                    String s = n.getOrDefault(k, "").toString();
                                    if (s.contains(",")) {
                                        s = String.format("\"%s\"", s.replaceAll("\"", "\\\""));
                                    }
                                    return s;
                                })
                                .collect(Collectors.joining(","))))
                .collect(Collectors.joining("\n")));
    }
}
