import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import javax.swing.*;
public class Main {
    public static void main(String[] args) {
        String eventPath = "PI1/Sample_event_file.csv";
        String zonePath = "PI1/sample_zone_file.csv";

        Queue<Event> fromFire = new LinkedList<>();
        Queue<String> toFire = new LinkedList<>();
        Scheduler scheduler = new Scheduler(fromFire, toFire);
        List<Zone> zones = Zone.loadFromCSV(zonePath);
        FireIncidentSubsystemGUI gui = new FireIncidentSubsystemGUI(zones);
        FireIncidentSubsystem fire = new FireIncidentSubsystem(eventPath, scheduler, gui);
        DroneSubsystem droneSubsystem = new DroneSubsystem(scheduler, zones, gui);

        SwingUtilities.invokeLater(() -> gui.setVisible(true));

        fire.loadEvents();
        for(Event event: fire.getEvents())
            gui.addEvent(event);
        gui.setStatus("Simulation started");

        //start threads (one JVM, multiple threads)
        Thread schedulerThread = new Thread(scheduler, "Scheduler");
        Thread droneThread = new Thread(droneSubsystem, "DroneSubsystem");
        Thread fireThread = new Thread(fire, "FireIncidentSubsystem");
        schedulerThread.start();
        droneThread.start();
        fireThread.start();
        try {
            fireThread.join();
            Thread.sleep(5000);
            schedulerThread.interrupt();
            droneThread.interrupt();
            schedulerThread.join();
            droneThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        gui.setStatus("Simulation complete");

    }
}
