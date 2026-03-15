import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Timeout;
import java.lang.reflect.Field;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Queue;
import static org.junit.jupiter.api.Assertions.*;
//required junit test file to see if
//file reads input
//sends event to scheduler
//gets confirmations back
public class FireIncidentSubsystemJUnitTest {
    @TempDir
    Path tempDir;
    //parse test
    @Test
    void loadEvents_parsesCsv_andBuildsEventsList() throws Exception {
        Path csv = tempDir.resolve("events.csv");
        Files.writeString(csv,
                "Time,Zone ID,Event type,Severity\n" +
                        "14:03:15,3,FIRE_DETECTED,HIGH\n" +
                        "14:10:00,7,DRONE_REQUEST,MODERATE\n"
        );
        FireIncidentSubsystem fis = new FireIncidentSubsystem(csv.toString());
        fis.loadEvents();
        List<Event> events = getPrivateEventsList(fis);
        assertEquals(2, events.size());
        Event e0 = events.get(0);
        assertEquals("14:03:15", e0.getTime());
        assertEquals(3, e0.getZoneID());
        assertEquals(Event.EventType.FIRE_DETECTED, e0.getEventType());
        assertEquals(Event.Severity.HIGH, e0.getSeverity());
        assertEquals(30, e0.getWaterRequired());
        Event e1 = events.get(1);
        assertEquals("14:10:00", e1.getTime());
        assertEquals(7, e1.getZoneID());
        assertEquals(Event.EventType.DRONE_REQUEST, e1.getEventType());
        assertEquals(Event.Severity.MODERATE, e1.getSeverity());
        assertEquals(20, e1.getWaterRequired());
    }
    //Test time parsing
    @Test
    void parseTimeToMillis_parsesTimeStringsCorrectly() throws Exception {
        Path csv = tempDir.resolve("events.csv");
        Files.writeString(csv, "Time,Zone ID,Event type,Severity\n14:03:15,3,FIRE_DETECTED,HIGH\n");
        FireIncidentSubsystem fis = new FireIncidentSubsystem(csv.toString());
        assertEquals(14*3600 + 3*60 + 15, fis.parseTimeToMillis("14:03:15"));
        assertEquals(10*3600 + 0*60 + 0, fis.parseTimeToMillis("10:00:00"));
        assertEquals(0, fis.parseTimeToMillis("invalid"));
    }
    @SuppressWarnings("unchecked")
    private static List<Event> getPrivateEventsList(FireIncidentSubsystem fis) throws Exception {
        Field f = FireIncidentSubsystem.class.getDeclaredField("events");
        f.setAccessible(true);
        return (List<Event>) f.get(fis);
    }
}