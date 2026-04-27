import java.util.*;
import java.util.concurrent.*;

public class StudentCode extends Server {

	private CountryGraph graph = new CountryGraph();
	private Map<String, Country> countryMap = new HashMap<>();
	private boolean simulationStarted = false;
	private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private long totalGlobalInfected = 0;
	private long totalGlobalDeaths = 0;
	private int infectedCountryCount = 0;

	public StudentCode() {
		super();

		graph = GraphLoader.loadGraph("CountryBorders.CSV");
		GraphLoader.loadPopulation(graph, "CountryPopulation_Fixed.csv");
		GraphLoader.loadGDP(graph, "GDP_Cleaned_Latest.csv");

		for (Country c : graph.getCountrySet()) {
			countryMap.put(c.getName().toLowerCase(), c);
		}

		// Start simulation tick
		scheduler.scheduleAtFixedRate(this::simulationTick, 1, 1, TimeUnit.SECONDS);
	}

	private void simulationTick() {
		if (!simulationStarted) return;

		List<Country> countries = new ArrayList<>(graph.getCountrySet());
		Map<Country, Double> infectionIncreases = new HashMap<>();
		Map<Country, Long> deathIncreases = new HashMap<>();

		long currentInfected = 0;
		long currentDeaths = 0;
		int currentInfectedCount = 0;

		for (Country c : countries) {
			if (c.getInfectionLevel() > 0) {
				currentInfectedCount++;
				// In-country spread
				double populationFactor = Math.log10(c.getPopulation() + 1) / 10.0;
				double gdpFactor = Math.log10(c.getGdp() / (c.getPopulation() + 1) + 1) / 5.0;

				double growth = 0.05 * populationFactor * (1.0 - gdpFactor);
				growth = Math.max(0.005, growth); 

				double newLevel = Math.min(1.0, c.getInfectionLevel() + growth);
				infectionIncreases.put(c, newLevel);

				// Death calculation
				double mortalityRate = 0.002 * c.getInfectionLevel(); 
				long newDeaths = (long) (c.getPopulation() * c.getInfectionLevel() * mortalityRate);
				deathIncreases.put(c, c.getDeaths() + newDeaths);

				// Cross-border spread (Land)
				if (c.getInfectionLevel() > 0.1) {
					Set<Country> neighbors = graph.getNeighbors(c);
					for (Country neighbor : neighbors) {
						if (neighbor.getInfectionLevel() == 0) {
							double infectChance = 0.1 * c.getInfectionLevel();
							if (Math.random() < infectChance) {
								infectionIncreases.put(neighbor, 0.01);
								sendMessageToUser("Virus spread to " + neighbor.getName() + " via land border!");
							}
						}
					}
				}

				// Air/Sea Spread (Global)
				if (c.getInfectionLevel() > 0.3) {
					double airTravelChance = 0.02 * (c.getGdpPerCapita() / 50000.0 + 0.1);
					if (Math.random() < airTravelChance) {
						Country target = countries.get((int) (Math.random() * countries.size()));
						if (target.getInfectionLevel() == 0) {
							infectionIncreases.put(target, 0.01);
							sendMessageToUser("Virus reached " + target.getName() + " via international flight!");
						}
					}
				}
			}

			currentInfected += (long) (c.getPopulation() * c.getInfectionLevel());
			currentDeaths += c.getDeaths();
		}

		// Apply changes
		for (Map.Entry<Country, Double> entry : infectionIncreases.entrySet()) {
			entry.getKey().setInfectionLevel(entry.getValue());
		}
		for (Map.Entry<Country, Long> entry : deathIncreases.entrySet()) {
			entry.getKey().setDeaths(entry.getValue());
		}

		this.totalGlobalInfected = currentInfected;
		this.totalGlobalDeaths = currentDeaths;
		this.infectedCountryCount = currentInfectedCount;
	}

	@Override
	public Map<String, String> getStatus() {
		Map<String, String> status = new HashMap<>();
		for (Country c : graph.getCountrySet()) {
			if (c.getInfectionLevel() > 0) {
				status.put(c.getName(), getInfectionColor(c.getInfectionLevel()));
			}
		}

		status.put("stats_infected", String.format("%,d", totalGlobalInfected));
		status.put("stats_deaths", String.format("%,d", totalGlobalDeaths));
		status.put("stats_countries", String.valueOf(infectedCountryCount));

		return status;
	}

	private String getInfectionColor(double level) {
		int green = (int) (255 * (1.0 - level));
		return String.format("#ff%02x00", green);
	}

	public static void main(String[] args) {
		Server server = new StudentCode();
		server.run();
		server.openURL();
	}

	@Override
	public void getInputCountries(String country1, String country2) {}

	@Override
	public void getColorPath() {}

	@Override
	public void handleClick(String country) {
		Country clicked = countryMap.get(country.toLowerCase());
		if (clicked != null && !simulationStarted) {
			clicked.setInfectionLevel(0.01);
			simulationStarted = true;
			sendMessageToUser("Outbreak started in " + clicked.getName() + "!");
		}
	}
}