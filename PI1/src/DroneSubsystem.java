import java.util.ArrayList;
import java.util.List;

public class DroneSubsystem implements Runnable {

    private final SchedulerServer scheduler;
    private final List<Thread> drones;
    private volatile boolean running;

    public DroneSubsystem(SchedulerServer scheduler) {
        this.scheduler = scheduler;
        this.drones = new ArrayList<>();
        this.running = true;
    }

    @Override
    public void run() {
        System.out.println("[DroneSubsystem] Drone subsystem started");

        while (running) {
            try {
                System.out.println("[DroneSubsystem] Waiting for task...");
                Event task = scheduler.requestTask(); // blocks until work

                Drone drone = new Drone(task, scheduler);
                Thread droneThread =
                        new Thread(drone, "Drone-" + task.getZoneID());

                drones.add(droneThread);
                droneThread.start();

            } catch (InterruptedException e) {
                System.out.println("[DroneSubsystem] Interrupted, shutting down");
                running = false;
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("[DroneSubsystem] Drone subsystem ended");
    }
}
