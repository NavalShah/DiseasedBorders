public class Country {
    private String code;
    private String name;
    private volatile long population;
    private volatile double gdp;
    private volatile double infectionLevel = 0.0;
    private volatile long deaths = 0;
    private volatile boolean landBorderBlocked = false;
    private volatile boolean airBorderBlocked = false;
    private volatile double drugProgress = 0.0;
    private volatile boolean isImmune = false;

    public Country(String code, String name) {
        this.code = code;
        this.name = name.trim();
    }

    public boolean isLandBorderBlocked() { return landBorderBlocked; }
    public void setLandBorderBlocked(boolean blocked) { this.landBorderBlocked = blocked; }

    public boolean isAirBorderBlocked() { return airBorderBlocked; }
    public void setAirBorderBlocked(boolean blocked) { this.airBorderBlocked = blocked; }

    public double getDrugProgress() { return drugProgress; }
    public void setDrugProgress(double progress) { 
        this.drugProgress = Math.max(0.0, Math.min(1.0, progress)); 
    }

    public boolean isImmune() { return isImmune; }
    public void setImmune(boolean immune) { this.isImmune = immune; }

    public long getDeaths() { return deaths; }
    public void setDeaths(long deaths) { this.deaths = deaths; }
    public void addDeaths(long amount) { this.deaths += amount; }

    public double getInfectionLevel() { return infectionLevel; }
    public void setInfectionLevel(double level) {
        this.infectionLevel = Math.max(0.0, Math.min(1.0, level));
    }

    public void infect(double amount) {
        setInfectionLevel(this.infectionLevel + amount);
    }

    public boolean isInfected() {
        return infectionLevel > 0;
    }

    public String getCode() { return code; }
    public String getName() { return name; }

    public long getPopulation() { return population; }
    public void setPopulation(long population) { this.population = population; }

    public double getGdp() { return gdp; }
    public void setGdp(double gdp) { this.gdp = gdp; }

    public double getGdpPerCapita() {
        if (population == 0) return 0;
        return gdp / population;
    }

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