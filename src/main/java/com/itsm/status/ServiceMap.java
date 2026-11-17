package com.itsm.status;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.itsm.domain.ITService;

/**
 * The dependency map, laid out for drawing as SVG: services that need nothing
 * at the bottom, the services built on them above, every arrow pointing down
 * from a service to what it needs. Coordinates are in the SVG's own units.
 */
public record ServiceMap(int width, int height, List<Node> nodes, List<Edge> edges) {

    public static final int NODE_WIDTH = 176;
    public static final int NODE_HEIGHT = 46;
    private static final int LAYER_GAP = 104;
    private static final int MARGIN = 16;

    public record Node(long id, String name, int x, int y, String label, String tone) {

        public int width() {
            return NODE_WIDTH;
        }

        public int height() {
            return NODE_HEIGHT;
        }

        /** Where the status dot and the two lines of text go. */
        public int dotX() {
            return x + 16;
        }

        public int dotY() {
            return y + NODE_HEIGHT / 2;
        }

        public int textX() {
            return x + 30;
        }

        public int nameY() {
            return y + 20;
        }

        public int labelY() {
            return y + 36;
        }
    }

    /**
     * From the bottom of one box to the top of the box it needs. When several
     * arrows leave or reach the same box, their ends are spread along its edge.
     */
    public record Edge(int x1, int y1, int x2, int y2, boolean troubled) {

        /** A smooth S-curve, as an SVG path. */
        public String path() {
            int bend = Math.max(24, (y2 - y1) / 2);
            return "M %d %d C %d %d, %d %d, %d %d".formatted(x1, y1, x1, y1 + bend, x2, y2 - bend, x2, y2);
        }
    }

    /** The i-th of n points along the middle 70% of a box's edge. */
    private static int spread(int boxX, int index, int count) {
        if (count <= 1) {
            return boxX + NODE_WIDTH / 2;
        }
        double usable = NODE_WIDTH * 0.7;
        return (int) Math.round(boxX + NODE_WIDTH * 0.15 + usable * index / (count - 1));
    }

    public static ServiceMap of(ServiceGraph graph, Map<ITService, ServiceStatus> statuses, int width) {
        Map<ITService, Integer> layers = graph.layers();
        int top = layers.values().stream().max(Integer::compare).orElse(0);
        int height = MARGIN * 2 + top * LAYER_GAP + NODE_HEIGHT;

        Map<ITService, int[]> positions = new java.util.HashMap<>();
        List<Node> nodes = new ArrayList<>();
        for (int layer = top; layer >= 0; layer--) {
            final int current = layer;
            List<ITService> row = graph.services().stream()
                    .filter(service -> layers.get(service) == current)
                    .sorted(Comparator.comparing(ITService::getName))
                    .toList();
            int y = MARGIN + (top - layer) * LAYER_GAP;
            for (int i = 0; i < row.size(); i++) {
                int x = (int) Math.round((i + 0.5) * width / (double) row.size()) - NODE_WIDTH / 2;
                ITService service = row.get(i);
                positions.put(service, new int[] {x, y});
                ServiceStatus status = statuses.get(service);
                nodes.add(new Node(service.getId(), service.getName(), x, y,
                        status == null ? "Operational" : status.getLabel(),
                        status == null ? "success" : status.getTone()));
            }
        }
        // Where each arrow leaves and arrives: spread along the box edges, in the order of the other end.
        Map<ITService, List<ITService>> outgoing = new java.util.HashMap<>();
        Map<ITService, List<ITService>> incoming = new java.util.HashMap<>();
        for (ITService service : graph.services()) {
            for (ITService dependency : service.getDependsOn()) {
                if (positions.containsKey(service) && positions.containsKey(dependency)) {
                    outgoing.computeIfAbsent(service, key -> new ArrayList<>()).add(dependency);
                    incoming.computeIfAbsent(dependency, key -> new ArrayList<>()).add(service);
                }
            }
        }
        Comparator<ITService> leftToRight = Comparator.comparingInt(service -> positions.get(service)[0]);
        outgoing.values().forEach(list -> list.sort(leftToRight));
        incoming.values().forEach(list -> list.sort(leftToRight));

        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<ITService, List<ITService>> entry : outgoing.entrySet()) {
            ITService service = entry.getKey();
            int[] from = positions.get(service);
            for (ITService dependency : entry.getValue()) {
                int[] to = positions.get(dependency);
                ServiceStatus status = statuses.get(dependency);
                edges.add(new Edge(
                        spread(from[0], entry.getValue().indexOf(dependency), entry.getValue().size()),
                        from[1] + NODE_HEIGHT,
                        spread(to[0], incoming.get(dependency).indexOf(service), incoming.get(dependency).size()),
                        to[1],
                        status != null && status.health().isOutage()));
            }
        }
        return new ServiceMap(width, height, nodes, edges);
    }
}
