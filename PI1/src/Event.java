import java.io.Serializable;

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
    //event attrib
    private final String time;
    private final int zoneID;
    private final EventType eventType;
    private final Severity severity;
    private boolean eventDelivered = false;

    private float waterLeft; //amount of water left to put out the fire

    //event constr
    public Event(String time, int zoneID, EventType eventType, Severity severity){
        this.time = time;
        this.zoneID = zoneID;
        this.eventType = eventType;
        this.severity = severity;
        this.waterLeft = getWaterRequired();
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
    public int getWaterRequired(){
        return severity.getWaterRequired();
    }
    public boolean isEventDelivered() {return eventDelivered;}

    public float getWaterLeft(){
        return waterLeft;
    } //get the amount of water left to put out the fire
    public boolean isFireOut() {
        return getWaterLeft() <= 0;
    } //whether the fire is gone

    public void deliverEvent() { eventDelivered = true; }
    public void useWater(float waterVolume) { //use water on a fire
        if(waterVolume >= getWaterLeft())
            waterLeft = 0;
        else
            waterLeft -= waterVolume;
    }

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
    @Override
    public String toString(){
        return String.format("Event[Time=%s, Zone=%d, Type=%s, Severity=%s (%dL)]", time, zoneID, eventType, severity, severity.getWaterRequired());
    }




}
