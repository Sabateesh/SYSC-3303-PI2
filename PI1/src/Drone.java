import java.util.List;

public class Drone implements Runnable {
    public static final float DROP_RATE = 2; //drop rate of water
    public static final float TANK_SIZE = 15; //tank size in liters
    public static final float BATTERY_SIZE = 50; //battery size in minutes
    private static final int DRONE_SPEED = 20;

    private Event event;
    private boolean running = true;
    private DroneStateMachine stateMachine;
    private float waterRemaining;
    private float batteryRemaining;
    private final List<Zone> zones;
    private int currentZone = 0; // base zone id

    private final String droneName;
    private final DroneSubsystem droneSubsystem;
    private final FireIncidentSubsystemGUI gui;

    public Drone(DroneSubsystem droneSubsystem, String droneName, List<Zone> zones, FireIncidentSubsystemGUI gui) {
        this.droneSubsystem = droneSubsystem;
        this.event = null;
        this.droneName = droneName;
        this.stateMachine = new DroneStateMachine(droneName);

        this.waterRemaining = TANK_SIZE;
        this.batteryRemaining = BATTERY_SIZE;
        this.zones = zones;
        this.gui = gui;
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

    public int timeToTravel(int zoneID1, int zoneID2) {

        Zone z1 = getZoneById(zoneID1);
        Zone z2 = getZoneById(zoneID2);

        if (z1 == null || z2 == null)
            return 0;

        double dx = z2.centreX() - z1.centreX();
        double dy = z2.centreY() - z1.centreY();

        double distance = Math.sqrt(dx * dx + dy * dy);

        double timeSeconds = distance / DRONE_SPEED;

        return (int) timeSeconds;
    }

    private Zone getZoneById(int id) {
        for (Zone z : zones) {
            if (z.id == id)
                return z;
        }
        return null;
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
                        System.out.println("[" + droneName + "] En route to zone " + event.getZoneID());
                        gui.updateDroneState(droneName, FireIncidentSubsystemGUI.DroneState.InRoute, waterRemaining, event.getZoneID());
                        Thread.sleep(timeToTravel(currentZone, event.getZoneID()));

                        currentZone = event.getZoneID();
                        stateMachine.handleEvent(DroneEvent.arrivedToFire);
                        break;
                    case DroneState.droppingAgent: //TODO: ONLY EMPTY UNTIL BATTERY HAS JUST ENOUGH
                        System.out.println("[" + droneName + "] Servicing fire at zone " + event.getZoneID());

                        float emptyAmount = event.getWaterLeft();
                        if(event.getWaterLeft() > getWaterRemaining())
                            emptyAmount = getWaterRemaining();
                        int emptyTime = (int) ((emptyAmount * 1000) / (float)DROP_RATE);
                        gui.updateDroneState(droneName, FireIncidentSubsystemGUI.DroneState.DroppingAgent, waterRemaining, event.getZoneID());
                        Thread.sleep(emptyTime);

                        useUpWater(emptyAmount);
                        event.useWater(emptyAmount);

                        if(event.isFireOut()) {

                            gui.setFireExtinguished(event.getZoneID());
                            gui.updateEventStatus(event.getGuiRowIndex(), "Extinguished");
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
                        Thread.sleep(timeToTravel(currentZone, 0));
                        currentZone = 0;
                        stateMachine.handleEvent(DroneEvent.arrivedToOrigin);

                        setDroneFull();
                        stateMachine.handleEvent(DroneEvent.fireAssigned);
                        break;
                    case DroneState.returnOrigin:
                        gui.updateDroneState(droneName, FireIncidentSubsystemGUI.DroneState.Returning, waterRemaining, 0);
                        Thread.sleep(timeToTravel(currentZone, 0));
                        currentZone = 0;
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
