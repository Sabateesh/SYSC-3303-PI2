public class Drone implements Runnable {
    public static final float DROP_RATE = 2; //drop rate of water
    public static final float TANK_SIZE = 15; //tank size in liters
    public static final float BATTERY_SIZE = 50; //battery size in minutes

    private Event event;
    private boolean busy = false;
    private boolean running = true;
    private float waterRemaining;
    private float batteryRemaining;

    private final String droneName;
    private final DroneSubsystem droneSubsystem;

    public Drone(DroneSubsystem droneSubsystem, String droneName) {
        this.droneSubsystem = droneSubsystem;
        this.event = null;
        this.droneName = droneName;
        this.waterRemaining = TANK_SIZE;
        this.batteryRemaining = BATTERY_SIZE;
    }

    public boolean isBusy() {
        return this.busy;
    }

    public float getWaterRemaining() {
        return waterRemaining;
    }
    public boolean isTankEmpty() {
        return getWaterRemaining() <= 0;
    }
    public void useUpWater(float waterVolume) {
        if(waterVolume >= waterRemaining)
            waterRemaining = 0;
        else
            waterRemaining -= waterVolume;
    }

    @Override
    public void run() {
        while(running) {
            try {
                if(isTankEmpty()) {
                    System.out.println("[" + droneName + "] Refilling tank");

                    // TODO: TRAVEL TO ORIGIN TO REFILL TANK
                    Thread.sleep(2000);

                    System.out.println("[" + droneName + "] Tank full");
                    waterRemaining = TANK_SIZE;
                    batteryRemaining = BATTERY_SIZE;

                } else {
                    if (isBusy()) { //the drone has a fire to address
                        // TODO: TRAVEL TO ZONE

                        System.out.println("[" + droneName + "] Servicing fire at zone " + event.getZoneID());

                        float emptyAmount = event.getWaterLeft();
                        if(event.getWaterLeft() > getWaterRemaining())
                            emptyAmount = getWaterRemaining();
                        int emptyTime = (int) ((emptyAmount * 1000) / (float)DROP_RATE);
                        Thread.sleep(emptyTime);

                        useUpWater(emptyAmount);
                        event.useWater(emptyAmount);
                        System.out.println("[" + droneName + "] Emptied " + emptyAmount + " L of water");

                        if(event.isFireOut()) {
                            droneSubsystem.reportDone(event);
                            System.out.println("[" + droneName + "] Completed fire at zone " + event.getZoneID());
                            this.event = null;
                            this.busy = false;
                        }
                    } else { //the drone is waiting for a new fire
                        System.out.println("[" + droneName + "] Waiting for task...");
                        event = droneSubsystem.requestTask(); // blocks until work
                        this.busy = true;
                    }
                }
            } catch (InterruptedException e) {
                System.out.println("[" + droneName + "] Interrupted, shutting down");
                running = false;
                Thread.currentThread().interrupt();
            }
        }
    }
}
