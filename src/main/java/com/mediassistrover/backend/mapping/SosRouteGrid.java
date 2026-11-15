package com.mediassistrover.backend.mapping;

import java.util.*;

/** Eight-connected A* over verified free cells. Coordinates use the map frame in metres. */
public record SosRouteGrid(int width, int height, double resolution, double originX,
                           double originY, double yaw, boolean[] free) {
    public record Point(double x, double y) { }
    public record Route(List<Point> points, double distance) { }
    private record Visit(int cell, double cost, double estimate) { }

    public int cell(Point point) {
        if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) return -1;
        double dx = point.x() - originX, dy = point.y() - originY;
        double x = (Math.cos(yaw) * dx + Math.sin(yaw) * dy) / resolution;
        double y = (-Math.sin(yaw) * dx + Math.cos(yaw) * dy) / resolution;
        if (x < 0 || y < 0 || x >= width || y >= height) return -1;
        return (int) Math.floor(y) * width + (int) Math.floor(x);
    }

    public boolean walkable(Point point) {
        int cell = cell(point);
        return cell >= 0 && free[cell];
    }

    private Point centre(int cell) {
        double x = (cell % width + .5) * resolution;
        double y = (cell / width + .5) * resolution;
        return new Point(originX + Math.cos(yaw) * x - Math.sin(yaw) * y,
                originY + Math.sin(yaw) * x + Math.cos(yaw) * y);
    }

    private double heuristic(int cell, int destination) {
        int dx = Math.abs(cell % width - destination % width);
        int dy = Math.abs(cell / width - destination / width);
        return resolution * (Math.max(dx, dy) + (Math.sqrt(2) - 1) * Math.min(dx, dy));
    }

    /** Multi-source search selects the shortest reachable monitoring-room origin. */
    public Optional<Route> shortest(List<Point> origins, Point destination) {
        if (!walkable(destination)) return Optional.empty();
        int goal = cell(destination), count = free.length;
        double[] costs = new double[count];
        Arrays.fill(costs, Double.POSITIVE_INFINITY);
        int[] previous = new int[count], source = new int[count];
        Arrays.fill(previous, -1);
        Arrays.fill(source, -1);
        var queue = new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::estimate)
                .thenComparingInt(Visit::cell));
        Route sameCell = null;
        for (int i = 0; i < origins.size(); i++) {
            Point origin = origins.get(i);
            if (!walkable(origin)) continue;
            int start = cell(origin);
            if (start == goal) {
                double distance = distance(origin, destination);
                if (sameCell == null || distance < sameCell.distance())
                    sameCell = new Route(List.of(origin, destination), distance);
            }
            double cost = distance(origin, centre(start));
            if (cost < costs[start]) {
                costs[start] = cost;
                source[start] = i;
                queue.add(new Visit(start, cost, cost + heuristic(start, goal)));
            }
        }
        while (!queue.isEmpty()) {
            Visit visit = queue.remove();
            int current = visit.cell();
            if (visit.cost() > costs[current]) continue;
            if (current == goal) {
                List<Point> points = new ArrayList<>();
                for (int cursor = goal; cursor >= 0; cursor = previous[cursor])
                    points.add(centre(cursor));
                Collections.reverse(points);
                points.addFirst(origins.get(source[goal]));
                points.add(destination);
                double distance = costs[goal] + distance(centre(goal), destination);
                if (sameCell != null && sameCell.distance() <= distance) return Optional.of(sameCell);
                return Optional.of(new Route(List.copyOf(points), distance));
            }
            int x = current % width, y = current / width;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                    int next = ny * width + nx;
                    if (!free[next]) continue;
                    if (dx != 0 && dy != 0 && (!free[y * width + nx] || !free[ny * width + x])) continue;
                    double cost = costs[current] + resolution * Math.hypot(dx, dy);
                    if (cost >= costs[next]) continue;
                    costs[next] = cost;
                    previous[next] = current;
                    source[next] = source[current];
                    queue.add(new Visit(next, cost, cost + heuristic(next, goal)));
                }
            }
        }
        return Optional.ofNullable(sameCell);
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }
}
