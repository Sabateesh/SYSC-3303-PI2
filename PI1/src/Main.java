import java.util.LinkedList;
import java.util.Queue;
import javax.swing.*;
public class Main {
    public static void main(String[] args) {
        String eventPath = "PI1/Sample_event_file.csv";
        Queue<Event> fromFire = new LinkedList<>();
        Queue<String> toFire = new LinkedList<>();
        Scheduler scheduler = new Scheduler(fromFire, toFire);
        FireIncidentSubsystem fire = new FireIncidentSubsystem(eventPath, scheduler);
        DroneSubsystem droneSubsystem = new DroneSubsystem(scheduler);

        FireIncidentSubsystemGUI gui = new FireIncidentSubsystemGUI();
        SwingUtilities.invokeLater(() -> gui.setVisible(true));

        //drone 1
        gui.registerDrone("Drone-1", Drone.TANK_SIZE);
        fire.loadEvents();
        for(Event event: fire.getEvents()) {
            gui.addEvent(
                event.getTime(),
                event.getZoneID(),
                event.getEventType().toString(),
                event.getSeverity().toString()
            );
        }
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
