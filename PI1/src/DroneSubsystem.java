import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class DroneSubsystem implements Runnable {

    private final SchedulerServer scheduler;
    public final static int NUM_DRONES = 3;
    private final List<Thread> drones;
    private final Queue<Event> fromFire;
    private volatile boolean running;

    public DroneSubsystem(SchedulerServer scheduler) {
        this.scheduler = scheduler;
        this.fromFire = new LinkedList<>();
        this.drones = new ArrayList<>();
        this.running = true;
    }

    public void initializeDrones() {
        for(int i=0; i<NUM_DRONES; i++) {
            String droneName = "Drone-"+i;
            Drone drone = new Drone(this, droneName);
            Thread droneThread =
                    new Thread(drone, droneName);
            drones.add(droneThread);
            droneThread.start();
        }
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
        scheduler.reportDone(e);
    }

    @Override
    public void run() {
        System.out.println("[DroneSubsystem] Drone subsystem started");
        initializeDrones();

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
