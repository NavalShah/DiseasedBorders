import java.util.*;

public class StudentCode extends Server {

	private CountryGraph graph = new CountryGraph();
	private Map<String, Country> countryMap = new HashMap<>();

	public StudentCode() {
		super();

		graph = GraphLoader.loadGraph("CountryBorders.CSV");
		GraphLoader.loadPopulation(graph, "CountryPopulation_Fixed.csv");

		for (Country c : graph.getCountrySet()) {
			countryMap.put(c.getName().toLowerCase(), c);
		}
	}

	public static void main(String[] args) {

		Server server = new StudentCode(); // Initialize server on default port (8000).
		server.run(); // Start the server.
		server.openURL(); // Open url in browser.
	}

	@Override
	public void getInputCountries(String country1, String country2) {

		clearCountryColors(); // reset map

		Country start = countryMap.get(country1.toLowerCase());
		Country end = countryMap.get(country2.toLowerCase());

		if (start == null || end == null) {
			sendMessageToUser("Invalid country selection.");
			return;
		}

		List<Country> path = graph.findPath(start, end);

		if (path.isEmpty()) {
			sendMessageToUser("No path found.");
			return;
		}

		// color full path
		for (Country c : path) {
			addCountryColor(c.getName(), "#00ccff"); // light blue
		}

		// highlight endpoints
		addCountryColor(country1, "#00ff00"); // green
		addCountryColor(country2, "#ff0000"); // red

		sendMessageToUser("Path length: " + (path.size() - 1));
	}

	@Override
	public void getColorPath() {

	}

	@Override
	public void handleClick(String country) {
		clearCountryColors();

		Country clicked = countryMap.get(country.toLowerCase());

		if (clicked == null)
			return;

		// Use a copy of the neighbors set to avoid modifying the graph's internal
		// adjList
		Set<Country> neighbors = new HashSet<>(graph.getNeighbors(clicked));

		// Include the clicked country too
		neighbors.add(clicked);

		// Find highest population among clicked country and its neighbors
		Country maxPop = null;
		for (Country c : neighbors) {
			if (maxPop == null || c.getPopulation() > maxPop.getPopulation()) {
				maxPop = c;
			}
		}

		// Color all neighbors and clicked country light gray first
		for (Country c : neighbors) {
			addCountryColor(c.getName(), "#cccccc"); // light gray
		}

		// Highlight the winner with orange
		if (maxPop != null) {
			addCountryColor(maxPop.getName(), "#ff9900"); // orange highlight
			sendMessageToUser("Highest population: " + maxPop.getName() + " (" + maxPop.getPopulation() + ")");
		}

		// Highlight clicked country with yellow (overwrites orange if it was the max)
		addCountryColor(clicked.getName(), "#ffff00"); // yellow
	}
}