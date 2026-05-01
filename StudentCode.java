import java.util.*;
import java.util.concurrent.*;

public class StudentCode extends Server {

	private CountryGraph graph = new CountryGraph();
	private Map<String, Country> countryMap = new HashMap<>();
	private volatile boolean simulationStarted = false;
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
	private final int maxLevel = 10;

	private long dnaPoints = 0;
	private long totalPointsEarned = 0;
	private volatile String gameState = "PLAYING"; // PLAYING, WON, LOST
	private double globalCureProgress = 0.0;

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

	private synchronized void simulationTick() {
		if (!simulationStarted || !gameState.equals("PLAYING"))
			return;

		dayCount++;

		List<Country> countries = new ArrayList<>(graph.getCountrySet());
		Map<Country, Double> infectionIncreases = new HashMap<>();
		Map<Country, Long> deathIncreases = new HashMap<>();

		long currentInfected = 0;
		long currentDeaths = 0;
		int currentInfectedCount = 0;
		double totalDrugProgress = 0;

		// 1. Check Win/Loss
		if (infectedCountryCount == countries.size() && totalGlobalInfected > 0) {
			gameState = "WON";
			sendMessageToUser("VICTORY: The virus has spread to every corner of the earth!");
			return;
		}

		if (dayCount > 10 && totalGlobalInfected == 0) {
			gameState = "LOST";
			sendMessageToUser("DEFEAT: The virus has been eradicated. Humanity survives.");
			return;
		}

		for (Country c : countries) {
			// --- Country Defenses & Intelligence ---
			// Block borders if infection is high
			if (c.getInfectionLevel() > 0.15 && !c.isLandBorderBlocked()) {
				c.setLandBorderBlocked(true);
				sendMessageToUser(c.getName() + " has closed its land borders!");
			}
			if (c.getInfectionLevel() > 0.25 && !c.isAirBorderBlocked()) {
				c.setAirBorderBlocked(true);
				sendMessageToUser(c.getName() + " has suspended all international flights!");
			}

			// Drug Research (Starts if infected or global threat is high)
			boolean globalThreat = (infectedCountryCount > countries.size() * 0.2);
			if ((c.getInfectionLevel() > 0.05 || globalThreat) && !c.isImmune()) {
				double wealthFactor = Math.log10(c.getGdpPerCapita() + 1) / 6.0;
				// Drug Resistance upgrade slows down research
				double resistanceEffect = Math.max(0.1, 1.0 - (resistanceLevel * 0.05));
				double researchSpeed = 0.002 * wealthFactor * resistanceEffect; 
				c.setDrugProgress(c.getDrugProgress() + researchSpeed);
			}
			totalDrugProgress += c.getDrugProgress();

			// Apply Cure/Immunity
			if (c.getDrugProgress() >= 1.0 && !c.isImmune()) {
				c.setImmune(true);
				sendMessageToUser(c.getName() + " has developed a cure and is now immune!");
			}

			if (c.getInfectionLevel() > 0) {
				currentInfectedCount++;
				
				long livingPop = Math.max(0, c.getPopulation() - c.getDeaths());
				
				// Base growth
				double populationFactor = Math.log10(c.getPopulation() + 1) / 10.0;
				double gdpImpact = Math.log10(c.getGdp() / (c.getPopulation() + 1) + 1) / 5.0;
				double resistanceModifier = Math.max(0.1, 1.0 - (resistanceLevel * 0.08));
				double gdpFactor = gdpImpact * resistanceModifier;
				double baseGrowth = 0.05 + (infectivityLevel * 0.02);
				double growth = baseGrowth * populationFactor * (1.0 - gdpFactor);
				
				// Cure impact: Drug progress actively reduces infection
				double cureImpact = c.getDrugProgress() * 0.2; 
				double totalChange = growth - cureImpact;

				double newLevel = Math.max(0.0, Math.min(1.0, c.getInfectionLevel() + totalChange));
				if (c.isImmune() || livingPop == 0) newLevel = 0;
				infectionIncreases.put(c, newLevel);

				// Death calculation (based on living infected people)
				double baseMortality = 0.005; // Slightly increased
				double lethalityModifier = 1.0 + (lethalityLevel * 0.6);
				double mortalityRate = baseMortality * c.getInfectionLevel() * lethalityModifier;
				long newDeaths = (long) (livingPop * c.getInfectionLevel() * mortalityRate);
				deathIncreases.put(c, Math.min(c.getPopulation(), c.getDeaths() + newDeaths));

				// Cross-border spread (Land) - Threshold lowered to 2%
				if (c.getInfectionLevel() > 0.02 && !c.isLandBorderBlocked()) {
					Set<Country> neighbors = graph.getNeighbors(c);
					for (Country neighbor : neighbors) {
						if (neighbor.getInfectionLevel() == 0 && !neighbor.isImmune() && !neighbor.isLandBorderBlocked()) {
							double baseInfectChance = 0.25; // Increased from 0.1
							double landModifier = 1.0 + (landTransportLevel * 1.0); // Increased multiplier
							// Spread chance is higher even at low infection to help early game
							double infectChance = baseInfectChance * (c.getInfectionLevel() + 0.05) * landModifier;
							if (Math.random() < infectChance) {
								infectionIncreases.put(neighbor, 0.01);
								sendMessageToUser("Virus spread to " + neighbor.getName() + " via land border!");
							}
						}
					}
				}

				// Air/Sea Spread (Global) - Threshold lowered to 5%
				if (c.getInfectionLevel() > 0.05 && !c.isAirBorderBlocked()) {
					double baseAirChance = 0.04; // Increased from 0.02
					double airModifier = 1.0 + (internationalTransportLevel * 1.8);
					double airTravelChance = baseAirChance * (c.getGdpPerCapita() / 50000.0 + 0.15) * airModifier;
					if (Math.random() < airTravelChance) {
						Country target = countries.get((int) (Math.random() * countries.size()));
						if (target.getInfectionLevel() == 0 && !target.isImmune() && !target.isAirBorderBlocked()) {
							infectionIncreases.put(target, 0.01);
							sendMessageToUser("Virus reached " + target.getName() + " via international flight!");
						}
					}
				}
			}

			long living = Math.max(0, c.getPopulation() - c.getDeaths());
			// "Infected" now represents Total Cases (Living Infected + Total Deaths)
			currentInfected += (long) (living * c.getInfectionLevel()) + c.getDeaths();
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
		this.globalCureProgress = totalDrugProgress / countries.size();

		// Update DNA Points
		long pointsFromInfected = currentInfected / 1000000;
		long pointsFromDeaths = currentDeaths / 200000;
		long newTotalPoints = pointsFromInfected + pointsFromDeaths;

		if (newTotalPoints > totalPointsEarned) {
			dnaPoints += (newTotalPoints - totalPointsEarned);
			totalPointsEarned = newTotalPoints;
		}
	}

	@Override
	public synchronized Map<String, String> getStatus() {
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

		status.put("global_cure", String.format("%.1f%%", globalCureProgress * 100));
		status.put("game_state", gameState);

		return status;
	}

	private int getUpgradeCost(int currentLevel) {
		if (currentLevel >= maxLevel)
			return -1;
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
	public void getInputCountries(String country1, String country2) {
	}

	@Override
	public void getColorPath() {
	}

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
	public synchronized void handleUpgrade(String type) {
		System.out.println("LOG: handleUpgrade requested [" + type + "]");
		int cost;
		switch (type.toLowerCase()) {
			case "lethality":
				if (lethalityLevel >= maxLevel)
					return;
				cost = getUpgradeCost(lethalityLevel);
				if (dnaPoints >= cost) {
					dnaPoints -= cost;
					lethalityLevel++;
					sendMessageToUser("Lethality Upgraded to Lvl " + lethalityLevel);
				}
				break;
			case "land":
				if (landTransportLevel >= maxLevel)
					return;
				cost = getUpgradeCost(landTransportLevel);
				if (dnaPoints >= cost) {
					dnaPoints -= cost;
					landTransportLevel++;
					sendMessageToUser("Land Spread Upgraded to Lvl " + landTransportLevel);
				}
				break;
			case "international":
				if (internationalTransportLevel >= maxLevel)
					return;
				cost = getUpgradeCost(internationalTransportLevel);
				if (dnaPoints >= cost) {
					dnaPoints -= cost;
					internationalTransportLevel++;
					sendMessageToUser("Air/Sea Travel Upgraded to Lvl " + internationalTransportLevel);
				}
				break;
			case "infectivity":
				if (infectivityLevel >= maxLevel)
					return;
				cost = getUpgradeCost(infectivityLevel);
				if (dnaPoints >= cost) {
					dnaPoints -= cost;
					infectivityLevel++;
					sendMessageToUser("Infectivity Upgraded to Lvl " + infectivityLevel);
				}
				break;
			case "resistance":
				if (resistanceLevel >= maxLevel)
					return;
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
