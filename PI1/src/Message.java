import java.io.Serializable;

public class Message implements Serializable {

    public enum Type {
        EVENT,          // Fire -> Scheduler (new event)
        REQUEST_TASK,   // Drone -> Scheduler (ask for work)
        DISPATCH,       // Scheduler -> Drone (send event to execute)
        DONE,           // Drone -> Scheduler (finished event)
        ACK,            // Scheduler -> Fire (confirmation)
        NO_TASK         // Scheduler -> Drone (nothing available)
    }
    private final Type type;
    private final Event event;
    private final String note;
    public Message(Type type, Event event, String note) {
        this.type = type;
        this.event = event;
        this.note = note;
    }
    public Type getType() { return type; }
    public Event getEvent() { return event; }
    public String getNote() { return note; }
    @Override
    public String toString() {
        return "Message[type=" + type + ", event=" + event + ", note=" + note + "]";
    }
}
