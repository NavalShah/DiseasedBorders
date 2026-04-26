import java.util.*;

public class CountryGraph {

    private Map<Country, Set<Country>> adjList;

    public CountryGraph() {
        adjList = new HashMap<>();
    }

    public void addCountry(Country c) {
        adjList.putIfAbsent(c, new HashSet<>());
    }

    public void addBorder(Country a, Country b) {
        addCountry(a);
        addCountry(b);

        adjList.get(a).add(b);
        adjList.get(b).add(a);
    }


    public Set<Country> getCountrySet() {
        return adjList.keySet();
    }

    public Set<String> getBorderSet() {
        Set<String> borders = new HashSet<>();

        for (Country c : adjList.keySet()) {
            for (Country neighbor : adjList.get(c)) {
                String edge = c.getName() + " - " + neighbor.getName();
                String reverse = neighbor.getName() + " - " + c.getName();

                if (!borders.contains(reverse)) {
                    borders.add(edge);
                }
            }
        }
        return borders;
    }

    public boolean shareBorder(Country a, Country b) {
        return adjList.containsKey(a) && adjList.get(a).contains(b);
    }

    public Set<Country> getNeighbors(Country a) {
        return adjList.getOrDefault(a, new HashSet<>());
    }



    public boolean isConnected(Country a, Country b) {
        return getDistance(a, b) != -1;
    }

    public List<Country> findPath(Country start, Country end) {
        if (!adjList.containsKey(start) || !adjList.containsKey(end)) {
            return new ArrayList<>();
        }

        Map<Country, Country> parentMap = new HashMap<>();
        Queue<Country> queue = new LinkedList<>();
        Set<Country> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Country current = queue.poll();

            if (current.equals(end)) break;

            for (Country neighbor : adjList.get(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parentMap.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        List<Country> path = new ArrayList<>();
        if (!start.equals(end) && !parentMap.containsKey(end)) {
            return path;
        }

        Country curr = end;
        while (curr != null) {
            path.add(curr);
            curr = parentMap.get(curr);
        }

        Collections.reverse(path);
        return path;
    }

    public int getDistance(Country start, Country end) {
        if (!adjList.containsKey(start) || !adjList.containsKey(end)) {
            return -1;
        }

        Queue<Country> queue = new LinkedList<>();
        Map<Country, Integer> distance = new HashMap<>();

        queue.add(start);
        distance.put(start, 0);

        while (!queue.isEmpty()) {
            Country current = queue.poll();

            if (current.equals(end)) {
                return distance.get(current);
            }

            for (Country neighbor : adjList.get(current)) {
                if (!distance.containsKey(neighbor)) {
                    distance.put(neighbor, distance.get(current) + 1);
                    queue.add(neighbor);
                }
            }
        }

        return -1; // can't reach it (islands and stuff )
    }

    public Set<Country> getWithinRadius(Country start, int radius) {
        Set<Country> result = new HashSet<>();

        if (!adjList.containsKey(start)) return result;

        Queue<Country> queue = new LinkedList<>();
        Map<Country, Integer> distance = new HashMap<>();

        queue.add(start);
        distance.put(start, 0);

        while (!queue.isEmpty()) {
            Country current = queue.poll();
            int dist = distance.get(current);

            if (dist > radius) continue;

            result.add(current);

            for (Country neighbor : adjList.get(current)) {
                if (!distance.containsKey(neighbor)) {
                    distance.put(neighbor, dist + 1);
                    queue.add(neighbor);
                }
            }
        }

        return result;
    }
}