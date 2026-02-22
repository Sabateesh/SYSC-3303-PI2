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
        Queue<Event> fromFire = new LinkedList<>();
        Queue<String> toFire = new LinkedList<>();
        Scheduler scheduler = new Scheduler(fromFire, toFire);
        FireIncidentSubsystem fis = new FireIncidentSubsystem(csv.toString(), scheduler);
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
    //Fire -> Scheduler (queue) -> "done" -> Fire confirmation
    @Test
    @Timeout(10)
    void run_sendsEvents_andReceivesConfirmations_roundTrip() throws Exception {
        Path csv = tempDir.resolve("events.csv");
        Files.writeString(csv,
                "Time,Zone ID,Event type,Severity\n" +
                        "10:00:00,1,FIRE_DETECTED,LOW\n" +
                        "10:00:01,2,DRONE_REQUEST,HIGH\n"
        );
        Queue<Event> fromFire = new LinkedList<>();
        Queue<String> toFire = new LinkedList<>();
        Scheduler scheduler = new Scheduler(fromFire, toFire);
        FireIncidentSubsystem fis = new FireIncidentSubsystem(csv.toString(), scheduler);
        Thread droneSim = new Thread(() -> {
            try {
                for (int i = 0; i < 2; i++) {
                    Event e = scheduler.requestTask();
                    assertNotNull(e);
                    scheduler.reportDone(e);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "DroneSim");
        Thread fireThread = new Thread(fis, "FireIncidentSubsystem");
        droneSim.start();
        fireThread.start();
        fireThread.join();
        droneSim.join();
    }
    @SuppressWarnings("unchecked")
    private static List<Event> getPrivateEventsList(FireIncidentSubsystem fis) throws Exception {
        Field f = FireIncidentSubsystem.class.getDeclaredField("events");
        f.setAccessible(true);
        return (List<Event>) f.get(fis);
    }
}

