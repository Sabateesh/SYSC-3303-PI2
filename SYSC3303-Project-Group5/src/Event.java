import java.io.Serializable;
import java.util.Objects;

public class Event implements Serializable {
    public enum EventType {
        FIRE_DETECTED,
        DRONE_REQUEST
    }
    public enum Severity{
        //L of water/foam needed
        LOW(10),
        MODERATE(20),
        HIGH(30);
        private final int waterRequired;
        Severity(int waterRequired){
            this.waterRequired = waterRequired;
        }
        public int getWaterRequired(){
            return waterRequired;
        }
    }
    public enum FaultType {
        NONE,
        DRONE_STUCK,
        ARRIVAL_SENSOR_FAILED,
        NOZZLE_STUCK_OPEN,
        COMM_FAILURE
    }
    public enum State {
        INACTIVE,
        EXTINGUISHED,
        PENDING,
        DISPATCHED,
        DROPPING,
        PARTIAL_EXTINGUISHED
    }
    //event attrib
    private final String time;
    private final int zoneID;
    private final EventType eventType;
    private final Severity severity;
    private final FaultType faultType;

    private State curState;
    private float waterLeft; //amount of water left to put out the fire

    //event constr
    public Event(String time, int zoneID, EventType eventType, Severity severity){
        this(time, zoneID, eventType, severity, FaultType.NONE);
    }

    public Event(String time, int zoneID, EventType eventType, Severity severity, FaultType faultType){
        this.time = time;
        this.zoneID = zoneID;
        this.eventType = eventType;
        this.severity = severity;
        this.faultType = faultType == null ? FaultType.NONE : faultType;
        this.waterLeft = getWaterRequired();
        this.curState = State.INACTIVE;
    }

    //getters
    public String getTime(){
        return time;
    }
    public int getZoneID(){
        return zoneID;
    }
    public EventType getEventType(){
        return eventType;
    }
    public Severity getSeverity(){
        return severity;
    }
    public FaultType getFaultType() { return faultType; }
    public int getWaterRequired(){
        return severity.getWaterRequired();
    }
    public State currentState() {return curState;}

    public float getWaterLeft(){
        return waterLeft;
    } //get the amount of water left to put out the fire

    public void deliverEvent(State newState) { curState = newState; }
    public void useWater(float waterVolume) { //use water on a fire
        if(waterVolume >= getWaterLeft())
            waterLeft = 0;
        else
            waterLeft -= waterVolume;
    }
    public void setWaterLeft(float waterLeft) { this.waterLeft = waterLeft; }

    //parse an eventtype from a string
    public static EventType parseEventType(String typeString){
        try{
            return EventType.valueOf(typeString.toUpperCase());
        } catch (IllegalArgumentException e ) {
            throw new IllegalArgumentException("invalid event type" + typeString);
        }
    }
    public static Severity parseSeverity(String servertiyString){
        try{
            return Severity.valueOf(servertiyString.toUpperCase());
        } catch (IllegalArgumentException e){
            throw new IllegalArgumentException("invalid severity level" + servertiyString);
        }
    }
    public static FaultType parseFaultType(String faultString) {
        if (faultString == null || faultString.trim().isEmpty()) {
            return FaultType.NONE;
        }
        String normalized = faultString.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if (normalized.equals("NONE") || normalized.equals("NO_FAULT")) {
            return FaultType.NONE;
        }
        try {
            return FaultType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid fault type " + faultString);
        }
    }
    @Override
    public boolean equals(Object o){
        if(this==o) return true;
        if (o==null|| getClass()!= o.getClass()) return false;
        Event event =(Event) o;
        return zoneID == event.zoneID && Objects.equals(time,event.time);
    }
    @Override
    public int hashCode(){
        return Objects.hash(time, zoneID);
    }
    @Override
    public String toString(){
        return String.format("Event[Time=%s, Zone=%d, Type=%s, Severity=%s (%dL), Fault=%s]",
                time, zoneID, eventType, severity, severity.getWaterRequired(), faultType);
    }
}
