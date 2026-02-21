import java.util.List;

public class Drone implements Runnable {
    public static final float DROP_RATE = 2; //drop rate of water
    public static final float TANK_SIZE = 15; //tank size in liters
    public static final float BATTERY_SIZE = 50; //battery size in minutes
    public static final float DRONE_SPEED = 200; //drone speed in units per minute

    private Event event;
    private boolean running = true;
    private final DroneStateMachine stateMachine;
    private final List<Zone> zones;
    private Zone currentZone;
    private float waterRemaining;
    private float batteryRemaining;

    private final String droneName;
    private final DroneSubsystem droneSubsystem;

    public Drone(DroneSubsystem droneSubsystem, List<Zone> zones, String droneName) {
        this.droneSubsystem = droneSubsystem;
        this.event = null;
        this.droneName = droneName;
        this.stateMachine = new DroneStateMachine(droneName);
        this.zones = zones;
        this.currentZone = null;

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
    public void useUpBattery(float flightTime) {
        if(flightTime >= batteryRemaining)
            batteryRemaining = 0;
        else
            batteryRemaining -= flightTime;
    }
    public int batteryPercent() {
        return (int)((batteryRemaining * 100) / BATTERY_SIZE);
    }

    public void setDroneFull() {
        waterRemaining = TANK_SIZE;
        batteryRemaining = BATTERY_SIZE;
    }
    public static int timeBetweenZones(Zone z1, Zone z2) {
        return (int)((Zone.getDistance(z1, z2) * 1000) / DRONE_SPEED);
    }
    public static int timeToOrigin(Zone z) {
        return (int)((Zone.getDistanceToOrigin(z) * 1000) / DRONE_SPEED);
    }
    public int timeToZone(Zone z) {
        if(currentZone == null) {
            return timeToOrigin(z);
        }
        return timeBetweenZones(currentZone, z);
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

                        try {
                            Zone newZone = Zone.getZoneFromId(zones, event.getZoneID());
                            int travelTimeToZone = timeToZone(newZone);
                            System.out.println("[" + droneName + "] Travelling for " + travelTimeToZone + "s");
                            Thread.sleep(travelTimeToZone);
                            useUpBattery((int)(travelTimeToZone/1000));
                            currentZone = newZone;
                        } catch (Zone.UnknownZoneException e) {
                            System.out.println("[" + droneName + "] Zone does not exist " + event.getZoneID());
                        }
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
                        int travelTimeToRefill = timeToOrigin(currentZone);
                        System.out.println("[" + droneName + "] Travelling for " + travelTimeToRefill + "s");
                        Thread.sleep(travelTimeToRefill);
                        useUpBattery((int)(travelTimeToRefill/1000));
                        currentZone = null;

                        setDroneFull();
                        stateMachine.handleEvent(DroneEvent.fireAssigned);
                        break;
                    case DroneState.returnOrigin:
                        int travelTimeToOrigin = timeToOrigin(currentZone);
                        System.out.println("[" + droneName + "] Travelling for " + travelTimeToOrigin + "s");
                        Thread.sleep(travelTimeToOrigin);
                        useUpBattery((int)(travelTimeToOrigin/1000));
                        currentZone = null;

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
