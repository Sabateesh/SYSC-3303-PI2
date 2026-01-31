
public class Event {
    public enum EventType {
        FIRE_DETECTED,
        DRONE_REQUEST
    }
    public enum Severity{
        //L of water/foam needed
        Low(10),
        Moderate(20),
        High(30);
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

    //event constr
    public Event(String time, int zoneID, EventType eventType, Severity severity){
        this.time = time;
        this.zoneID = zoneID;
        this.eventType = eventType;
        this.severity = severity;
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
