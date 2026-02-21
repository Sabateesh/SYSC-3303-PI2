public class Drone implements Runnable {
    public static final float DROP_RATE = 2; //drop rate of water
    public static final float TANK_SIZE = 15; //tank size in liters
    public static final float BATTERY_SIZE = 50; //battery size in minutes

    private Event event;
    private boolean running = true;
    private DroneStateMachine stateMachine;
    private float waterRemaining;
    private float batteryRemaining;

    private final String droneName;
    private final DroneSubsystem droneSubsystem;

    public Drone(DroneSubsystem droneSubsystem, String droneName) {
        this.droneSubsystem = droneSubsystem;
        this.event = null;
        this.droneName = droneName;
        this.stateMachine = new DroneStateMachine(droneName);

        this.waterRemaining = TANK_SIZE;
        this.batteryRemaining = BATTERY_SIZE;
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
    public void setDroneFull() {
        waterRemaining = TANK_SIZE;
        batteryRemaining = BATTERY_SIZE;
    }

    @Override
    public void run() {
        while(running) {
            try {
                switch(stateMachine.getState()) {
                    case DroneState.idle:
                        event = droneSubsystem.requestTask(); // blocks until work
                        stateMachine.handleEvent(DroneEvent.fireAssigned);
                        break;
                    case DroneState.enRoute:
                        System.out.println("[" + droneName + "] Enroute to zone " + event.getZoneID());
                        // TODO: CALCULATE TIME TRAVEL TO ZONE
                        stateMachine.handleEvent(DroneEvent.arrivedToFire);
                        break;
                    case DroneState.droppingAgent: //TODO: ONLY EMPTY UNTIL BATTERY HAS JUST ENOUGH
                        System.out.println("[" + droneName + "] Servicing fire at zone " + event.getZoneID());

                        float emptyAmount = event.getWaterLeft();
                        if(event.getWaterLeft() > getWaterRemaining())
                            emptyAmount = getWaterRemaining();
                        int emptyTime = (int) ((emptyAmount * 1000) / (float)DROP_RATE);
                        Thread.sleep(emptyTime);

                        useUpWater(emptyAmount);
                        event.useWater(emptyAmount);

                        if(event.isFireOut()) {
                            droneSubsystem.reportDone(event);
                            System.out.println("[" + droneName + "] Completed fire at zone " + event.getZoneID());
                            this.event = null;
                            stateMachine.handleEvent(DroneEvent.jobFinished);
                        } else {
                            System.out.println("[" + droneName + "] Emptied " + emptyAmount + " L of water, going for refill");
                            stateMachine.handleEvent(DroneEvent.needRefill);
                        }

                        break;
                    case DroneState.returnForRefill:
                        Thread.sleep(2000); // TODO: CALCULATE TIME TRAVEL TO ORIGIN

                        setDroneFull();
                        stateMachine.handleEvent(DroneEvent.fireAssigned);
                        break;
                    case DroneState.returnOrigin:
                        Thread.sleep(2000); // TODO: CALCULATE TIME TRAVEL TO ORIGIN

                        setDroneFull();
                        stateMachine.handleEvent(DroneEvent.arrivedToOrigin);
                        break;
                    default:
                        System.out.println("[" + droneName + "] Unknown state");
                        Thread.currentThread().interrupt();
                        break;
                }
            } catch (InterruptedException e) {
                System.out.println("[" + droneName + "] Interrupted, shutting down");
                running = false;
                Thread.currentThread().interrupt();
            }
        }
    }
}
