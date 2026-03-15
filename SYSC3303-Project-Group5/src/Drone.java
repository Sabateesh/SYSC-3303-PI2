public class Drone implements Runnable {
    public static final float DROP_RATE = 2; //drop rate of water
    public static final float TANK_SIZE = 15; //tank size in liters
    public static final float BATTERY_SIZE = 50; //battery size in minutes
    public static final float DRONE_SPEED = 2*60; //drone speed in units per minute
    public static final float REFILL_RATE = 5; //liters refilled per second at base

    public static final float DROP_RATE = 2;
    public static final float TANK_SIZE = 15;
    public static final float BATTERY_SIZE = 50;
    private final DroneSubsystem droneSubsystem;
    private Event event;
    private boolean running = true;
    private final DroneStateMachine stateMachine;
    private final List<Zone> zones;
    private int currentZoneId = 0;
    private int targetZoneId = 0;
    private long lastAnimDurationMs = 0;  
    private long animStartTime = 0;       
    private float waterRemaining;
    private float batteryRemaining;
    private final String droneName;
    public Drone(DroneSubsystem droneSubsystem, String droneName) {

        this.droneSubsystem = droneSubsystem;
        this.droneName = droneName;
        this.stateMachine = new DroneStateMachine(droneName);
        this.waterRemaining = TANK_SIZE;
        this.batteryRemaining = BATTERY_SIZE;
        this.event = null;
    }

    public String getDroneName() {
        return droneName;
    }
    public DroneState getDroneState() {
        return stateMachine.getState();
    }
    public float getWaterRemaining() {
        return waterRemaining;
    }

    public boolean isTankEmpty() {
        return waterRemaining <= 0;
    }

    public void useUpWater(float waterVolume) {
        if(waterVolume >= waterRemaining)
            waterRemaining = 0;
        else
            waterRemaining -= waterVolume;
    }

    public void useUpBattery(float time) {
        if(time >= batteryRemaining)
            batteryRemaining = 0;
        else
            batteryRemaining -= time;
    }

    public int batteryPercent() {
        return (int)((batteryRemaining * 100) / BATTERY_SIZE);
    }
    public int getCurrentZoneId() {
        return currentZoneId;
    }
    public int getTargetZoneId() { return targetZoneId; }
    public long getLastAnimDurationMs() { return lastAnimDurationMs; }
    public long getAnimStartTime() { return animStartTime; }

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
                        event = droneSubsystem.requestTask();
                        event.deliverEvent(Event.State.DISPATCHED);
                        stateMachine.handleEvent(DroneEvent.fireAssigned);
                        break;
                    case DroneState.enRoute: {
                        System.out.println("[" + droneName + "] Enroute to zone " + event.getZoneID());
                        targetZoneId = event.getZoneID();
                        float travelTime = 0;
                        Zone destZone = null;
                        try {
                            destZone = Zone.getZoneFromId(zones, event.getZoneID());
                            travelTime = timeToZone(destZone);
                        } catch (Zone.UnknownZoneException ex) {
                            System.out.println("[" + droneName + "] Zone does not exist " + event.getZoneID());
                        }
                        lastAnimDurationMs = (long)(travelTime * 60 * SchedulerServer.simulationSpeed);
                        animStartTime = System.currentTimeMillis();
                        gui.paintDrone(this);
                        if (destZone != null) {
                            System.out.println("[" + droneName + "] Travelling for " + travelTime * 60 + "s");
                            Thread.sleep(lastAnimDurationMs);
                            useUpBattery(travelTime);
                            currentZoneId = destZone.id;
                        }
                        targetZoneId = 0;
                        stateMachine.handleEvent(DroneEvent.arrivedToFire);
                        break;
                    }
                    case DroneState.droppingAgent:
                        System.out.println("[" + droneName + "] Servicing fire at zone " + event.getZoneID());
                        event.deliverEvent(Event.State.DROPPING);
                        float emptyAmount = event.getWaterLeft();
                        if(emptyAmount > waterRemaining)
                            emptyAmount = waterRemaining;
                        float emptyTime = emptyAmount / DROP_RATE;
                        Thread.sleep((int)(emptyTime * 1000));
                        useUpBattery(emptyTime);
                        useUpWater(emptyAmount);
                        event.useWater(emptyAmount);
                        if(event.getWaterLeft() <= 0) {
                            System.out.println("[" + droneName + "] Fire extinguished");
                            droneSubsystem.reportDone(event);
                            event.deliverEvent(Event.State.EXTINGUISHED);
                            event = null;
                            stateMachine.handleEvent(DroneEvent.jobFinished);
                        } else {
                            System.out.println("[" + droneName + "] Tank empty, need refill");
                            stateMachine.handleEvent(DroneEvent.needRefill);
                        }
                        break;
                    case DroneState.returnForRefill: {
                        float travelTimeToRefill = 0;
                        try {
                            Zone currentZoneV = Zone.getZoneFromId(zones, currentZoneId);
                            travelTimeToRefill = timeToOrigin(currentZoneV);
                        } catch(Zone.UnknownZoneException e) { }
                        lastAnimDurationMs = (long)(travelTimeToRefill * 60 * SchedulerServer.simulationSpeed);
                        animStartTime = System.currentTimeMillis();
                        gui.paintDrone(this);
                        System.out.println("[" + droneName + "] Travelling for " + travelTimeToRefill*60 + "s");
                        Thread.sleep(lastAnimDurationMs);
                        useUpBattery(travelTimeToRefill);
                        currentZoneId = 0;
                        float waterNeeded = TANK_SIZE - waterRemaining;
                        long refillMs = (long)((waterNeeded / REFILL_RATE) * 1000);
                        gui.paintDrone(this);
                        System.out.println("[" + droneName + "] Refilling " + waterNeeded + "L at base");
                        Thread.sleep(refillMs);
                        setDroneFull();
                        stateMachine.handleEvent(DroneEvent.fireAssigned);
                        break;
                    }
                    case DroneState.returnOrigin: {
                        float travelTimeToOrigin = 0;
                        try {
                            Zone currentZoneV = Zone.getZoneFromId(zones, currentZoneId);
                            travelTimeToOrigin = timeToOrigin(currentZoneV);
                        } catch(Zone.UnknownZoneException e) { }
                        lastAnimDurationMs = (long)(travelTimeToOrigin * 60 * SchedulerServer.simulationSpeed);
                        animStartTime = System.currentTimeMillis();
                        gui.paintDrone(this);
                        System.out.println("[" + droneName + "] Travelling for " + travelTimeToOrigin*60 + "s");
                        Thread.sleep(lastAnimDurationMs);
                        useUpBattery(travelTimeToOrigin);
                        currentZoneId = 0;
                        setDroneFull();
                        stateMachine.handleEvent(DroneEvent.arrivedToOrigin);
                        droneSubsystem.reportToBase();
                        stateMachine.handleEvent(DroneEvent.arrivedToOrigin);
                        break;
                    }
                    default:
                        System.out.println("[" + droneName + "] Unknown state");
                        break;
                }

            } catch (InterruptedException e) {
                System.out.println("[" + droneName + "] Interrupted");
                running = false;
                Thread.currentThread().interrupt();
            }
        }
    }
}