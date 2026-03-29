import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.CRC32;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        EVENT,          // Fire -> Scheduler (new event)
        REQUEST_TASK,   // Drone -> Scheduler (ask for work)
        DISPATCH,       // Scheduler -> Drone (send event to execute)
        DONE,           // Drone -> Scheduler (finished event)
        PARTIAL_DONE,   // Drone -> Scheduler (partially finished event)
        ACK,            // Scheduler -> Fire (confirmation)
        NO_TASK,        // Scheduler -> Drone (nothing available)
        DRONE_STATUS,   // Drone -> Scheduler (status update)
        START,          // Fire -> Scheduler (simulation start)
        END_SIMULATION, // Fire -> Scheduler (simulation end)
        RESEND_REQUEST, // Receiver -> Sender (request resend after corruption)
        COMM_FAILURE,    // Sender/Receiver -> Peer (communications failed)'
        FAULT_INJECT
    }

    private final Type type;
    private final Event event;
    private final String note;
    private final String messageId;
    private final String correlationId;
    private final int attempt;
    private final long crc32;
    private final long sequenceNumber;

    private static long globalSequenceCounter = 0;

    public Message(Type type, Event event, String note) {
        this(type, event, note, UUID.randomUUID().toString(), null, 1, nextSequenceNumber());
    }

    public Message(Type type, Event event, String note, String messageId, String correlationId, int attempt) {
        this(type, event, note, messageId, correlationId, attempt, nextSequenceNumber());
    }

    public Message(Type type, Event event, String note, String messageId, String correlationId, int attempt, long sequenceNumber) {
        this.type = type;
        this.event = event;
        this.note = note == null ? "" : note;
        this.messageId = messageId == null ? UUID.randomUUID().toString() : messageId;
        this.correlationId = correlationId;
        this.attempt = Math.max(1, attempt);
        this.sequenceNumber = sequenceNumber;
        this.crc32 = computeCrc32();
    }

    private static synchronized long nextSequenceNumber() {
        return ++globalSequenceCounter;
    }

    public Type getType() { return type; }
    public Event getEvent() { return event; }
    public String getNote() { return note; }
    public String getMessageId() { return messageId; }
    public String getCorrelationId() { return correlationId; }
    public int getAttempt() { return attempt; }
    public long getCrc32() { return crc32; }
    public long getSequenceNumber() { return sequenceNumber; }

    public boolean isCrcValid() {
        return crc32 == computeCrc32();
    }

    public Message asResendAttempt() {
        return new Message(type, event, note, messageId, correlationId, attempt + 1, sequenceNumber);
    }

    public static Message resendRequest(String requesterId, String failedMessageId) {
        return new Message(Type.RESEND_REQUEST, null, requesterId, UUID.randomUUID().toString(), failedMessageId, 1);
    }

    public static Message commFailure(String droneId, String details) {
        String payload = details == null || details.isEmpty() ? droneId : droneId + "," + details;
        return new Message(Type.COMM_FAILURE, null, payload);
    }

    private long computeCrc32() {
        CRC32 crc = new CRC32();
        StringBuilder payload = new StringBuilder();
        payload.append(type == null ? "" : type.name()).append('|')
               .append(messageId == null ? "" : messageId).append('|')
               .append(correlationId == null ? "" : correlationId).append('|')
               .append(attempt).append('|')
               .append(sequenceNumber).append('|')
               .append(note == null ? "" : note).append('|');

        if (event != null) {
            payload.append(event.getTime()).append('|')
                   .append(event.getZoneID()).append('|')
                   .append(event.getEventType()).append('|')
                   .append(event.getSeverity()).append('|')
                   .append(event.currentState()).append('|')
                   .append(event.getWaterLeft());
        }

        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        crc.update(bytes, 0, bytes.length);
        return crc.getValue();
    }

    @Override
    public String toString() {
        return "Message[type=" + type + ", event=" + event + ", note=" + note
                + ", messageId=" + messageId + ", corr=" + correlationId
                + ", attempt=" + attempt + ", seq=" + sequenceNumber + ", crc=" + crc32 + "]";
    }
}
