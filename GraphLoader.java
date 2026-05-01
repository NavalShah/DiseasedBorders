import java.io.*;
import java.util.*;

public class GraphLoader {

	public static CountryGraph loadGraph(String filename) {
		CountryGraph graph = new CountryGraph();
		// Keyed by Name instead of Code
		Map<String, Country> nameMap = new HashMap<>();

		try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
			br.readLine(); // skip header
			String line;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split(",", -1);
				if (parts.length < 4)
					continue;

				String codeA = parts[0].trim();
				String nameA = parts[1].trim();
				String codeB = parts[2].trim();
				String nameB = parts[3].trim();

				// Use nameA as the key
				nameMap.putIfAbsent(nameA, new Country(codeA, nameA));
				Country a = nameMap.get(nameA);
				graph.addCountry(a);

				if (codeB.isEmpty())
					continue;

				// Use nameB as the key
				nameMap.putIfAbsent(nameB, new Country(codeB, nameB));
				Country b = nameMap.get(nameB);
				graph.addBorder(a, b);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return graph;
	}

	public static void loadPopulation(CountryGraph graph, String filename) {
		Map<String, Country> lookup = new HashMap<>();
		for (Country c : graph.getCountrySet()) {
			lookup.put(c.getName().toLowerCase(), c);
		}

		try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
			br.readLine(); // skip header
			String line;
			while ((line = br.readLine()) != null) {
				try {
					// Use regex that respects quotes for commas
					String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
					if (parts.length < 2)
						continue;

					String name = parts[0].trim().replaceAll("^\"|\"$", "");
					String popStr = parts[parts.length - 1].trim();
					long pop = (long) Double.parseDouble(popStr);

					if (lookup.containsKey(name.toLowerCase())) {
						lookup.get(name.toLowerCase()).setPopulation(pop);
					}
				} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
					// Skip malformed lines
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void loadGDP(CountryGraph graph, String filename) {
		Map<String, Country> lookup = new HashMap<>();
		for (Country c : graph.getCountrySet()) {
			lookup.put(c.getName().toLowerCase(), c);
		}

		try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
			br.readLine(); // skip header
			String line;
			while ((line = br.readLine()) != null) {
				try {
					// Use regex that respects quotes for commas
					String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
					if (parts.length < 3)
						continue;

					String name = parts[0].trim().replaceAll("^\"|\"$", "");
					String gdpStr = parts[parts.length - 1].trim();
					if (gdpStr.isEmpty())
						continue;

					double gdp = Double.parseDouble(gdpStr);

					if (lookup.containsKey(name.toLowerCase())) {
						lookup.get(name.toLowerCase()).setGdp(gdp);
					}
				} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
					// Skip malformed lines
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
