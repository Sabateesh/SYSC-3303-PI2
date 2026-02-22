import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class DroneSubsystem implements Runnable {

    private final SchedulerServer scheduler;
    private final FireIncidentSubsystemGUI gui;
    private final static int NUM_DRONES = 2;
    private final List<Thread> drones;
    private final List<Zone> zones;
    private final Queue<Event> fromFire;
    private volatile boolean running;

    public DroneSubsystem(SchedulerServer scheduler, List<Zone> zones, FireIncidentSubsystemGUI gui) {
        this.scheduler = scheduler;
        this.gui = gui;
        this.fromFire = new LinkedList<>();
        this.drones = new ArrayList<>();
        this.zones = zones;
        this.running = true;
        this.initializeDrones();
    }

    public void initializeDrones() {
        for(int i=0; i<NUM_DRONES; i++) {
            String droneName = "Drone-"+i;
            Drone drone = new Drone(this, zones, droneName, gui);
            Thread droneThread =
                    new Thread(drone, droneName);
            drones.add(droneThread);
            droneThread.start();
        }
        gui.paintAllDrones();
    }

    public Event requestTask() throws InterruptedException {
        synchronized (fromFire) {
            while (fromFire.isEmpty()) {
                fromFire.wait();
            }
            return fromFire.poll();
        }
    }

    public void reportDone(Event e) {
        if (e == null) return;
        gui.paintEvent(e);
        scheduler.reportDone(e);
    }

    @Override
    public void run() {
        System.out.println("[DroneSubsystem] Drone subsystem started");

        while (running) {
            try {
                Event task = scheduler.requestTask(); // blocks until work
                synchronized (fromFire) {
                    fromFire.offer(task);
                    fromFire.notifyAll();
                }
            } catch (InterruptedException e) {
                System.out.println("[DroneSubsystem] Interrupted, shutting down");
                running = false;
                Thread.currentThread().interrupt();
            }
        }

        for (Thread drone : drones) {
            drone.interrupt();
        }
        System.out.println("[DroneSubsystem] Drone subsystem ended");
    }
}
