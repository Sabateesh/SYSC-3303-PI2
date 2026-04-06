import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class Zone {
    public static class UnknownZoneException extends Exception {};

    public final int id, x1, y1, x2, y2;
    public Zone(int id, int x1, int y1, int x2, int y2) {
        this.id = id;
        this.x1 = x1; this.y1 = y1;
        this.x2 = x2; this.y2 = y2;
    }
    public int centreX() { return (x1 + x2) / 2; }
    public int centreY() { return (y1 + y2) / 2; }
    public static List<Zone> loadFromCSV(String path) {
        List<Zone> zones = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) { first = false; continue; }
                line = line.trim();
                if (line.isEmpty()) continue;
                String cleaned = line.replace("(", "").replace(")", "").replace(";", ",");
                String[] parts = cleaned.split(",");
                if (parts.length < 5) continue;
                zones.add(new Zone(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()),
                    Integer.parseInt(parts[3].trim()),
                    Integer.parseInt(parts[4].trim())
                ));
            }
        } catch (Exception e) {
            System.err.println("error loading zones: " + e.getMessage());
        }
        return zones;
    }
    public static Zone getZoneFromId(List<Zone> zones, int id) throws UnknownZoneException {
        for(Zone z : zones) {
            if (z.id == id)
                return z;
        }
        throw new UnknownZoneException();
    }
    public static float getDistance(Zone z1, Zone z2) {
        return (float)Math.sqrt(Math.pow(z1.centreX()-z2.centreX(), 2) + Math.pow(z1.centreY()-z2.centreY(), 2));
    }
    public static float getDistanceToOrigin(Zone z) {
        return (float)Math.sqrt(Math.pow(z.centreX(), 2) + Math.pow(z.centreY(), 2));
    }
}