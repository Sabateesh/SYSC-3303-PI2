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
            if(isBusy() && event != null) {
                System.out.println("["+droneName+"] Servicing fire at zone " + event.getZoneID());
                try {
                    Thread.sleep(2000); //place holder for drone time calculation later
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                droneSubsystem.reportDone(event);
                System.out.println("["+droneName+"] Completed fire at zone " + event.getZoneID());
                this.event = null;
                this.busy = false;
            } else {
                try {
                    System.out.println("["+droneName+"] Waiting for task...");
                    event = droneSubsystem.requestTask(); // blocks until work
                    this.busy = true;
                } catch (InterruptedException e) {
                    System.out.println("["+droneName+"] Interrupted, shutting down");
                    running = false;
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
