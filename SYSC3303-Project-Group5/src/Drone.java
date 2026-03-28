import java.util.List;

public class Drone implements Runnable {
    public static final float DROP_RATE = 2; //drop rate of water
    public static final float TANK_SIZE = 15; //tank size in liters
    public static final float BATTERY_SIZE = 50; //battery size in minutes
    public static final float DRONE_SPEED = 2*60; //drone speed in units per minute
    public static final float REFILL_RATE = 5; //liters refilled per second at base
    private static final float ARRIVAL_TIMEOUT_FACTOR = 1.15f;
    private static final long ARRIVAL_TIMEOUT_BUFFER_MS = 500;

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
    private DroneState guiState = DroneState.idle;
    private boolean hardFaultActive = false;
    private String hardFaultState = "idle";

    private static String ts() {
        return "[" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + "]";
    }

    public Drone(DroneSubsystem droneSubsystem, String droneName, List<Zone> zones) {
        this.droneSubsystem = droneSubsystem;
        this.droneName = droneName;
        this.zones = zones;
        this.stateMachine = new DroneStateMachine(droneName);
        this.waterRemaining = TANK_SIZE;
        this.batteryRemaining = BATTERY_SIZE;
        this.event = null;
    }

    // Constructor for GUI updates
    public Drone(String droneName, List<Zone> zones, int currentZoneId, float waterRemaining, float batteryRemaining, int targetZoneId, long lastAnimDurationMs, long animStartTime, String state) {
        this.droneSubsystem = null;
        this.droneName = droneName;
        this.zones = zones;
        this.stateMachine = new DroneStateMachine(droneName);
        this.currentZoneId = currentZoneId;
        this.waterRemaining = waterRemaining;
        this.batteryRemaining = batteryRemaining;
        this.targetZoneId = targetZoneId;
        this.lastAnimDurationMs = lastAnimDurationMs;
        this.animStartTime = animStartTime;
        this.event = null;
       try {
           this.guiState = DroneState.valueOf(state);
       } catch (IllegalArgumentException e) {
           this.guiState=DroneState.idle;
       }
    }

    public String getDroneName() {
        return droneName;
    }
    public DroneState getDroneState() {
        if (droneSubsystem == null) {
            return guiState;
        }
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

    private float timeToZone(Zone dest) {
        Zone origin = new Zone(0, 0, 0, 0, 0); // assume origin at 0,0
        return Zone.getDistance(origin, dest) / DRONE_SPEED;
    }

    private float timeToOrigin(Zone from) {
        Zone origin = new Zone(0, 0, 0, 0, 0);
        return Zone.getDistance(from, origin) / DRONE_SPEED;
    }

    private void sendStatus() {
        try {
            String state;
            if (hardFaultActive) {
                state = hardFaultState;
            } else {
                state = stateMachine.getState().name();
                if (droneSubsystem.isCommunicationFailed(droneName)) {
                    state = "commFailure";
                }
            }
            droneSubsystem.sendStatus(droneName, batteryRemaining, currentZoneId, waterRemaining, targetZoneId, lastAnimDurationMs, animStartTime, state);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void triggerHardFault(Event.FaultType faultType) {
        if (hardFaultActive) {
            return;
        }

        String reason;
        switch (faultType) {
            case DRONE_STUCK:
                hardFaultState = "droneStuckFault";
                reason = "fault_drone_stuck";
                break;
            case ARRIVAL_SENSOR_FAILED:
                hardFaultState = "arrivalSensorFault";
                reason = "fault_arrival_sensor";
                break;
            case NOZZLE_STUCK_OPEN:
                hardFaultState = "nozzleStuckFault";
                reason = "fault_nozzle_stuck_open";
                break;
            default:
                hardFaultState = "commFailure";
                reason = "fault_unknown";
                break;
        }

        hardFaultActive = true;
        targetZoneId = 0;
        System.out.println(ts() + " [" + droneName + "] HARD FAULT triggered: " + hardFaultState);
        try {
            droneSubsystem.reportFault(droneName, reason);
        } catch (Exception e) {
            e.printStackTrace();
        }
        sendStatus();
    }

    @Override
    public void run() {
        sendStatus(); // initial status
        while(running) {
            try {
                if (hardFaultActive) {
                    // Return to base after fault
                    System.out.println(ts() + " [" + droneName + "] Returning to base after fault");
                    float returnTime = 0;
                    if (currentZoneId > 0) {
                        try {
                            Zone currentZoneV = Zone.getZoneFromId(zones, currentZoneId);
                            returnTime = timeToOrigin(currentZoneV);
                        } catch (Zone.UnknownZoneException ignored) { }
                    }
                    lastAnimDurationMs = (long)(returnTime * 60 * Scheduler.simulationSpeed);
                    animStartTime = System.currentTimeMillis();
                    targetZoneId = 0;
                    sendStatus();
                    if (lastAnimDurationMs > 0) Thread.sleep(lastAnimDurationMs);
                    currentZoneId = 0;
                    targetZoneId = -1;
                    sendStatus();
                    System.out.println(ts() + " [" + droneName + "] Arrived at base, drone offline");
                    running = false;
                    continue;
                }

                if (droneSubsystem.isCommunicationFailed(droneName)) {
                    sendStatus();
                    Thread.sleep(500);
                    continue;
                }

                switch(stateMachine.getState()) {
                    case idle:
                        event = droneSubsystem.requestTask(droneName);
                        if (event == null) {
                            sendStatus();
                            Thread.sleep(250);
                            break;
                        }
                        event.deliverEvent(Event.State.DISPATCHED);
                        stateMachine.handleEvent(DroneEvent.fireAssigned);
                        sendStatus();
                        break;
                    case enRoute: {
                        System.out.println(ts() + " [" + droneName + "] Enroute to zone " + event.getZoneID());
                        targetZoneId = event.getZoneID();
                        float travelTime = 0;
                        Zone destZone = null;
                        try {
                            destZone = Zone.getZoneFromId(zones, event.getZoneID());
                            travelTime = timeToZone(destZone);
                        } catch (Zone.UnknownZoneException ex) {
                            System.out.println("[" + droneName + "] Zone does not exist " + event.getZoneID());
                        }
                        lastAnimDurationMs = (long)(travelTime * 60 * Scheduler.simulationSpeed);
                        animStartTime = System.currentTimeMillis();
                        if (destZone != null) {
                            long arrivalTimeoutMs = (long)(lastAnimDurationMs * ARRIVAL_TIMEOUT_FACTOR) + ARRIVAL_TIMEOUT_BUFFER_MS;
                            System.out.println(ts() + " [" + droneName + "] Travelling for " + travelTime * 60 + "s");

                            if (event.getFaultType() == Event.FaultType.DRONE_STUCK
                                    || event.getFaultType() == Event.FaultType.ARRIVAL_SENSOR_FAILED) {
                                // Simulate timeout-before-arrival fault injection for Iteration 4.
                                Thread.sleep(arrivalTimeoutMs);
                                triggerHardFault(event.getFaultType());
                                event = null;
                                break;
                            }

                            Thread.sleep(lastAnimDurationMs);
                            useUpBattery(travelTime);
                            currentZoneId = destZone.id;
                        }
                        targetZoneId = 0;
                        stateMachine.handleEvent(DroneEvent.arrivedToFire);
                        sendStatus();
                        break;
                    }
                    case droppingAgent:
                        System.out.println("[" + droneName + "] Servicing fire at zone " + event.getZoneID());
                        event.deliverEvent(Event.State.DROPPING);
                        if (event.getFaultType() == Event.FaultType.NOZZLE_STUCK_OPEN) {
                            Thread.sleep(750);
                            triggerHardFault(event.getFaultType());
                            event = null;
                            break;
                        }
                        float emptyAmount = event.getWaterLeft();
                        if(emptyAmount > waterRemaining)
                            emptyAmount = waterRemaining;
                        float emptyTime = emptyAmount / DROP_RATE;
                        Thread.sleep((int)(emptyTime * 1000));
                        useUpBattery(emptyTime);
                        useUpWater(emptyAmount);
                        event.useWater(emptyAmount);
                        if(event.getWaterLeft() <= 0) {
                            System.out.println(ts() + " [" + droneName + "] Fire extinguished");
                            event.deliverEvent(Event.State.EXTINGUISHED);
                            try {
                                droneSubsystem.reportDone(event, droneName);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            event = null;
                            stateMachine.handleEvent(DroneEvent.jobFinished);
                        } else {
                            System.out.println("[" + droneName + "] Tank empty, need refill");
                            event.deliverEvent(Event.State.PARTIAL_EXTINGUISHED);
                            stateMachine.handleEvent(DroneEvent.needRefill);
                            try {
                                droneSubsystem.reportPartialDone(event, droneName);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        sendStatus();
                        break;
                    case returnForRefill: {
                        float travelTimeToRefill = 0;
                        try {
                            Zone currentZoneV = Zone.getZoneFromId(zones, currentZoneId);
                            travelTimeToRefill = timeToOrigin(currentZoneV);
                        } catch(Zone.UnknownZoneException e) { }
                        lastAnimDurationMs = (long)(travelTimeToRefill * 60 * Scheduler.simulationSpeed);
                        animStartTime = System.currentTimeMillis();
                        System.out.println("[" + droneName + "] Travelling for " + travelTimeToRefill*60 + "s");
                        Thread.sleep(lastAnimDurationMs);
                        useUpBattery(travelTimeToRefill);
                        currentZoneId = 0;
                        float waterNeeded = TANK_SIZE - waterRemaining;
                        long refillMs = (long)((waterNeeded / REFILL_RATE) * 1000);
                        System.out.println("[" + droneName + "] Refilling " + waterNeeded + "L at base");
                        Thread.sleep(refillMs);
                        setDroneFull();
                        stateMachine.handleEvent(DroneEvent.fireAssigned);
                        sendStatus();
                        break;
                    }
                    case returnOrigin: {
                        float travelTimeToOrigin = 0;
                        try {
                            Zone currentZoneV = Zone.getZoneFromId(zones, currentZoneId);
                            travelTimeToOrigin = timeToOrigin(currentZoneV);
                        } catch(Zone.UnknownZoneException e) { }
                        lastAnimDurationMs = (long)(travelTimeToOrigin * 60 * Scheduler.simulationSpeed);
                        animStartTime = System.currentTimeMillis();
                        System.out.println("[" + droneName + "] Travelling for " + travelTimeToOrigin*60 + "s");
                        Thread.sleep(lastAnimDurationMs);
                        useUpBattery(travelTimeToOrigin);
                        currentZoneId = 0;
                        setDroneFull();
                        stateMachine.handleEvent(DroneEvent.arrivedToOrigin);
                        sendStatus();
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