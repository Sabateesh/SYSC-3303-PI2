public class Drone implements Runnable {

    private Event event;
    private boolean busy = false;

    private boolean running = true;

    private final String droneName;
    private final DroneSubsystem droneSubsystem;

    public Drone(DroneSubsystem droneSubsystem, String droneName) {
        this.droneSubsystem = droneSubsystem;
        this.event = null;
        this.droneName = droneName;
    }

    public boolean isBusy() {
        return this.busy;
    }

    @Override
    public void run() {
        while(running) {
            try {
                if (isBusy()) { //the drone has a fire to address
                    System.out.println("[" + droneName + "] Servicing fire at zone " + event.getZoneID());
                    Thread.sleep(2000); //place holder for drone time calculation later

                    droneSubsystem.reportDone(event);
                    System.out.println("[" + droneName + "] Completed fire at zone " + event.getZoneID());
                    this.event = null;
                    this.busy = false;
                } else { //the drone is waiting for a new fire
                    System.out.println("[" + droneName + "] Waiting for task...");
                    event = droneSubsystem.requestTask(); // blocks until work
                    this.busy = true;
                }
            } catch (InterruptedException e) {
                System.out.println("[" + droneName + "] Interrupted, shutting down");
                running = false;
                Thread.currentThread().interrupt();
            }
        }
    }
}
