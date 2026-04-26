public class Country {
    private String code;
    private String name;
    private long population;

    public Country(String code, String name) {
        this.code = code;
        this.name = name.trim();
    }

    public String getCode() { return code; }
    public String getName() { return name; }

    public long getPopulation() { return population; }
    public void setPopulation(long population) { this.population = population; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Country)) return false;
        Country c = (Country) o;
        // Identification is now based on name
        return name.equalsIgnoreCase(c.name);
    }

    @Override
    public int hashCode() {
        return name.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return name + " [" + code + "] - Pop: " + population;
    }
}