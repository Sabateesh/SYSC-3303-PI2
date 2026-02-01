//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
import java.util.LinkedList;
import java.util.Queue;
public class Main {
    public static void main(String[] args) {
        String eventPath = "PI1/Sample_event_file.csv";
        Queue<Event> fromFire = new LinkedList<>();
        Queue<String> toFire = new LinkedList<>();
        Scheduler scheduler = new Scheduler(fromFire, toFire);
        FireIncidentSubsystem fire = new FireIncidentSubsystem(eventPath, scheduler);
        //start threads (one JVM, multiple threads)
        Thread schedulerThread = new Thread(scheduler, "Scheduler");
        Thread droneThread = new Thread(new DroneSubsystem(scheduler), "DroneSubsystem");
        Thread fireThread = new Thread(fire, "FireIncidentSubsystem");
        schedulerThread.start();
        droneThread.start();
        fireThread.start();
    }
}
