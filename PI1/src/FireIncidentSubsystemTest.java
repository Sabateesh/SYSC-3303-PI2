import java.util.LinkedList;
import java.util.Queue;
public class FireIncidentSubsystemTest {
    public static void main(String[] args) {
        System.out.println("Fire Incident Subsystem Test - PI1");

        Queue<Event> eventQueue = new LinkedList<>();
        Queue<String> confirmationQueue = new LinkedList<>();
        //file path
        String eventpath = "Sample_event_file.csv";
        String zonepath = "Sample_zone_file.csv";
        //create fire incident subsystem
        FireIncidentSubsystem fireSubsystem = new FireIncidentSubsystem(
                eventpath,
                zonepath,
                eventQueue,
                confirmationQueue
        );
        //create thread
        Thread fireThread = new Thread(fireSubsystem, "FireIncidentSubsystem");
        System.out.println("[Main] Starting Fire Incident Subsystem thread\\n");
        fireThread.start();






    }
}
