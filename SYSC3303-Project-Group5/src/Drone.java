public class Drone implements Runnable {

    public static final float DROP_RATE = 2;
    public static final float TANK_SIZE = 15;
    public static final float BATTERY_SIZE = 50;
    private final DroneSubsystem droneSubsystem;
    private Event event;
    private boolean running = true;
    private final DroneStateMachine stateMachine;
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

                    case DroneState.returnForRefill:
                        System.out.println("[" + droneName + "] Refilling tank");
                        Thread.sleep(2000);
                        setDroneFull();
                        stateMachine.handleEvent(DroneEvent.fireAssigned);
                        break;

                    case DroneState.returnOrigin:
                        System.out.println("[" + droneName + "] Returning to base");
                        Thread.sleep(2000);
                        setDroneFull();
                        droneSubsystem.reportToBase();
                        stateMachine.handleEvent(DroneEvent.arrivedToOrigin);
                        break;

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