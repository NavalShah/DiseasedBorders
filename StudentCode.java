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
	private int dayCount = 0;

	private int lethalityLevel = 0;
	private int landTransportLevel = 0;
	private int internationalTransportLevel = 0;
	private int infectivityLevel = 0;
	private int resistanceLevel = 0;
	private int maxLevel = 10;

	private long dnaPoints = 0;
	private long totalPointsEarned = 0;

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

		dayCount++;

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
				
				// Drug Resistance upgrade reduces the impact of GDP/Wealth on stopping the virus
				double gdpImpact = Math.log10(c.getGdp() / (c.getPopulation() + 1) + 1) / 5.0;
				double resistanceModifier = Math.max(0.1, 1.0 - (resistanceLevel * 0.08));
				double gdpFactor = gdpImpact * resistanceModifier;

				// Infectivity upgrade boosts base growth
				double baseGrowth = 0.05 + (infectivityLevel * 0.02);
				double growth = baseGrowth * populationFactor * (1.0 - gdpFactor);
				growth = Math.max(0.005, growth); 

				double newLevel = Math.min(1.0, c.getInfectionLevel() + growth);
				infectionIncreases.put(c, newLevel);

				// Death calculation
				double baseMortality = 0.002;
				double lethalityModifier = 1.0 + (lethalityLevel * 0.6); // +60% per level
				double mortalityRate = baseMortality * c.getInfectionLevel() * lethalityModifier; 
				long newDeaths = (long) (c.getPopulation() * c.getInfectionLevel() * mortalityRate);
				deathIncreases.put(c, c.getDeaths() + newDeaths);

				// Cross-border spread (Land)
				if (c.getInfectionLevel() > 0.1) {
					Set<Country> neighbors = graph.getNeighbors(c);
					for (Country neighbor : neighbors) {
						if (neighbor.getInfectionLevel() == 0) {
							double baseInfectChance = 0.08;
							double landModifier = 1.0 + (landTransportLevel * 0.8); 
							double infectChance = baseInfectChance * c.getInfectionLevel() * landModifier;
							if (Math.random() < infectChance) {
								infectionIncreases.put(neighbor, 0.01);
								sendMessageToUser("Virus spread to " + neighbor.getName() + " via land border!");
							}
						}
					}
				}

				// Air/Sea Spread (Global)
				if (c.getInfectionLevel() > 0.3) {
					double baseAirChance = 0.015;
					double airModifier = 1.0 + (internationalTransportLevel * 1.5);
					double airTravelChance = baseAirChance * (c.getGdpPerCapita() / 50000.0 + 0.1) * airModifier;
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

		// Update DNA Points (10x harder)
		// 1,000,000 infected = 1 DNA
		// 200,000 killed = 1 DNA (effectively 1,000,000 killed = 5 DNA)
		long pointsFromInfected = totalGlobalInfected / 1000000;
		long pointsFromDeaths = totalGlobalDeaths / 200000;
		long newTotalPoints = pointsFromInfected + pointsFromDeaths;
		
		if (newTotalPoints > totalPointsEarned) {
			dnaPoints += (newTotalPoints - totalPointsEarned);
			totalPointsEarned = newTotalPoints;
		}
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
		status.put("stats_days", String.valueOf(dayCount));
		status.put("stats_dna", String.valueOf(dnaPoints));
		
		status.put("lvl_lethality", String.valueOf(lethalityLevel));
		status.put("lvl_land", String.valueOf(landTransportLevel));
		status.put("lvl_international", String.valueOf(internationalTransportLevel));
		status.put("lvl_infectivity", String.valueOf(infectivityLevel));
		status.put("lvl_resistance", String.valueOf(resistanceLevel));
		
		status.put("cost_lethality", String.valueOf(getUpgradeCost(lethalityLevel)));
		status.put("cost_land", String.valueOf(getUpgradeCost(landTransportLevel)));
		status.put("cost_international", String.valueOf(getUpgradeCost(internationalTransportLevel)));
		status.put("cost_infectivity", String.valueOf(getUpgradeCost(infectivityLevel)));
		status.put("cost_resistance", String.valueOf(getUpgradeCost(resistanceLevel)));

		return status;
	}

	private int getUpgradeCost(int currentLevel) {
		if (currentLevel >= maxLevel) return -1;
		// Exponential cost: 10, 18, 32, 58, 105, 189...
		return (int) (10 * Math.pow(1.8, currentLevel));
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

	@Override
	public void handleUpgrade(String type) {
		System.out.println("LOG: handleUpgrade requested [" + type + "]");
		int cost;
		switch (type.toLowerCase()) {
			case "lethality":
				if (lethalityLevel >= maxLevel) return;
				cost = getUpgradeCost(lethalityLevel);
				if (dnaPoints >= cost) {
					dnaPoints -= cost;
					lethalityLevel++;
					sendMessageToUser("Lethality Upgraded to Lvl " + lethalityLevel);
				}
				break;
			case "land":
				if (landTransportLevel >= maxLevel) return;
				cost = getUpgradeCost(landTransportLevel);
				if (dnaPoints >= cost) {
					dnaPoints -= cost;
					landTransportLevel++;
					sendMessageToUser("Land Spread Upgraded to Lvl " + landTransportLevel);
				}
				break;
			case "international":
				if (internationalTransportLevel >= maxLevel) return;
				cost = getUpgradeCost(internationalTransportLevel);
				if (dnaPoints >= cost) {
					dnaPoints -= cost;
					internationalTransportLevel++;
					sendMessageToUser("Air/Sea Travel Upgraded to Lvl " + internationalTransportLevel);
				}
				break;
			case "infectivity":
				if (infectivityLevel >= maxLevel) return;
				cost = getUpgradeCost(infectivityLevel);
				if (dnaPoints >= cost) {
					dnaPoints -= cost;
					infectivityLevel++;
					sendMessageToUser("Infectivity Upgraded to Lvl " + infectivityLevel);
				}
				break;
			case "resistance":
				if (resistanceLevel >= maxLevel) return;
				cost = getUpgradeCost(resistanceLevel);
				if (dnaPoints >= cost) {
					dnaPoints -= cost;
					resistanceLevel++;
					sendMessageToUser("Drug Resistance Upgraded to Lvl " + resistanceLevel);
				}
				break;
		}
	}
}