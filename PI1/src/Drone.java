import java.util.List;

public class Drone implements Runnable {
    public static final float DROP_RATE = 2; //drop rate of water
    public static final float TANK_SIZE = 15; //tank size in liters
    public static final float BATTERY_SIZE = 50; //battery size in minutes
    public static final float DRONE_SPEED = 2*60; //drone speed in units per minute

    private Event event;
    private boolean running = true;
    private final DroneStateMachine stateMachine;
    private final List<Zone> zones;
    private int currentZoneId = 0;
    private float waterRemaining;
    private float batteryRemaining;

    private final String droneName;
    private final DroneSubsystem droneSubsystem;
    private final FireIncidentSubsystemGUI gui;

    public Drone(DroneSubsystem droneSubsystem, List<Zone> zones, String droneName, FireIncidentSubsystemGUI gui) {
        this.droneSubsystem = droneSubsystem;
        this.gui = gui;
        this.stateMachine = new DroneStateMachine(droneName);

        this.event = null;
        this.droneName = droneName;
        this.zones = zones;

        this.waterRemaining = TANK_SIZE;
        this.batteryRemaining = BATTERY_SIZE;

        gui.registerDrone(this);
    }

    public String getDroneName() {
        return droneName;
    }
    DroneState getDroneState() {
        return stateMachine.getState();
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
    public int getCurrentZoneId() {
        return currentZoneId;
    }

    public void setDroneFull() {
        waterRemaining = TANK_SIZE;
        batteryRemaining = BATTERY_SIZE;
    }
    public static float timeToOrigin(Zone z) {
        return (Zone.getDistanceToOrigin(z) / DRONE_SPEED);
    }
    public static float timeBetweenZones(Zone z1, Zone z2) {
        return (Zone.getDistance(z1, z2) / DRONE_SPEED);
    }
    public float timeToZone(Zone z) {
        if(currentZoneId == 0) {
            return timeToOrigin(z);
        }
        try {
            Zone currentZoneV = Zone.getZoneFromId(zones, currentZoneId);
            return timeBetweenZones(currentZoneV, z);
        } catch(Zone.UnknownZoneException e) {
            return 0;
        }
    }

    @Override
    public void run() {
        while(running) {
            gui.paintDrone(this);
            try {
                switch(stateMachine.getState()) {
                    case DroneState.idle:
                        event = droneSubsystem.requestTask(); // blocks until work
                        event.deliverEvent(Event.State.DISPATCHED);
                        gui.paintEvent(event);
                        stateMachine.handleEvent(DroneEvent.fireAssigned);
                        break;
                    case DroneState.enRoute:
                        System.out.println("[" + droneName + "] Enroute to zone " + event.getZoneID());

                        try {
                            Zone newZone = Zone.getZoneFromId(zones, event.getZoneID());
                            float travelTimeToZone = timeToZone(newZone);
                            System.out.println("[" + droneName + "] Travelling for " + travelTimeToZone*60 + "s");
                            Thread.sleep((int)(travelTimeToZone*60*SchedulerServer.simulationSpeed));
                            useUpBattery(travelTimeToZone);
                            currentZoneId = newZone.id;
                        } catch (Zone.UnknownZoneException e) {
                            System.out.println("[" + droneName + "] Zone does not exist " + event.getZoneID());
                        }
                        stateMachine.handleEvent(DroneEvent.arrivedToFire);
                        break;
                    case DroneState.droppingAgent:
                        System.out.println("[" + droneName + "] Servicing fire at zone " + event.getZoneID());

                        event.deliverEvent(Event.State.DROPPING);
                        gui.paintEvent(event);

                        float emptyAmount = event.getWaterLeft();
                        if(event.getWaterLeft() > getWaterRemaining())
                            emptyAmount = getWaterRemaining();
                        float emptyTime = emptyAmount / DROP_RATE;

                        //only drop until the drone must go back to the base to recharge
                        try {
                            Zone currentZone = Zone.getZoneFromId(zones, currentZoneId);
                            //ensure that the drone doesnt get stranded by limiting emptying time
                            if(emptyTime+timeToOrigin(currentZone) > batteryRemaining) {
                                emptyTime = Math.max(batteryRemaining - timeToOrigin(currentZone), 0);
                                emptyAmount = emptyTime * DROP_RATE;
                            }
                        } catch(Zone.UnknownZoneException e) {
                            System.out.println("[" + droneName + "] Unknown zone " + currentZoneId);
                        }

                        Thread.sleep((int)(emptyTime*60*SchedulerServer.simulationSpeed));
                        useUpBattery(emptyTime);

                        useUpWater(emptyAmount);
                        event.useWater(emptyAmount);

                        if(event.isFireOut()) {
                            droneSubsystem.reportDone(event);
                            System.out.println("[" + droneName + "] Completed fire at zone " + event.getZoneID());

                            event.deliverEvent(Event.State.EXTINGUISHED);
                            gui.paintEvent(event);

                            this.event = null;
                            stateMachine.handleEvent(DroneEvent.jobFinished);
                        } else {
                            System.out.println("[" + droneName + "] Emptied " + emptyAmount + " L of water, going for refill");
                            stateMachine.handleEvent(DroneEvent.needRefill);

                            event.deliverEvent(Event.State.DISPATCHED);
                            gui.paintEvent(event);
                        }
                        break;
                    case DroneState.returnForRefill:
                        float travelTimeToRefill;
                        try {
                            Zone currentZoneV = Zone.getZoneFromId(zones, currentZoneId);
                            travelTimeToRefill = timeToOrigin(currentZoneV);
                        } catch(Zone.UnknownZoneException e) { travelTimeToRefill=0; }
                        System.out.println("[" + droneName + "] Travelling for " + travelTimeToRefill*60 + "s");
                        Thread.sleep((int)(travelTimeToRefill*60*SchedulerServer.simulationSpeed));
                        useUpBattery(travelTimeToRefill);
                        currentZoneId = 0;

                        setDroneFull();
                        stateMachine.handleEvent(DroneEvent.fireAssigned);
                        break;
                    case DroneState.returnOrigin:
                        float travelTimeToOrigin;
                        try {
                            Zone currentZoneV = Zone.getZoneFromId(zones, currentZoneId);
                            travelTimeToOrigin = timeToOrigin(currentZoneV);
                        } catch(Zone.UnknownZoneException e) { travelTimeToOrigin=0; }
                        System.out.println("[" + droneName + "] Travelling for " + travelTimeToOrigin*60 + "s");
                        Thread.sleep((int)(travelTimeToOrigin*60*SchedulerServer.simulationSpeed));
                        useUpBattery(travelTimeToOrigin);
                        currentZoneId = 0;

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
