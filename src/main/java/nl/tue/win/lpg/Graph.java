package nl.tue.win.lpg;

import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;

public class Graph extends HashMap<String, Object> {
    private final Nodes nodes;
    private final Edges edges;

    public Graph(String id, boolean directed, Nodes nodes, Edges edges) {
        if (id == null) id = "";
        this.put("id", id);
        this.put("directed", directed);
        this.nodes = nodes;
        this.edges = edges;
    }

    public Graph(String id, Nodes nodes, Edges edges) {
        this(id, false, nodes, edges);
    }

    public Graph(String id, boolean directed) {
        this(id, true, new Nodes(), new Edges());
    }

    public Graph(String id) {
        this(id, new Nodes(), new Edges());
    }

    public String getId() {
        return getOrDefault("id", "").toString();
    }

    public Nodes getNodes() {
        return nodes;
    }

    public Edges getEdges() {
        return edges;
    }

    public Optional<Node> getNode(String nodeId) {
        return nodes.stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst();
    }

    public Nodes getNodes(String attrKey, Object attrValue) {
        return nodes.stream()
                .filter(n -> n.containsKey(attrKey) && n.get(attrKey).equals(attrValue))
                .collect(Collectors.toCollection(Nodes::new));
    }

    @Override
    public String toString() {
        return "Graph:" +
                "\n  Nodes:\n    " + nodes.toString().replaceAll("\n", "\n    ") +
                "\n  Edges:\n    " + edges.toString().replaceAll("\n", "\n    ") +
                "\n";
    }
}
