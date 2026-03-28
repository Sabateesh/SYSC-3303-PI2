import java.net.*;
import java.io.*;
import java.util.*;

public class Scheduler implements Runnable {
    private DatagramSocket socket;
    public static final int simulationSpeed = 10; //1000 for real-time, do 10 or less to quickly test

    private final List<Event> events = new LinkedList<>();
    private final Map<String, DroneStatus> droneStatuses = new HashMap<>();
    private final Set<String> failedDrones = new HashSet<>();
    private final Map<String, Event> activeAssignments = new HashMap<>();
    private final Map<String, Message> outboundMessages = new HashMap<>();
    private final Map<String, PendingResend> pendingResends = new HashMap<>();
    private final Set<String> processedDroneMessages = new HashSet<>();
    private SchedulerGUI gui;
    private final Queue<String> requestingDrones = new LinkedList<>();
    private final Map<String, Integer> tasksAssigned = new HashMap<>();
    private final List<Zone> zones;

    private static final long RESEND_TIMEOUT_MS = 2500;
    private static final int MAX_RESEND_ATTEMPTS = 1;
    private static final long MAX_SEQUENCE_GAP = 5;
    private long lastReceivedSequenceFromDroneSubsystem = -1;
    private long lastReceivedSequenceFromFireSubsystem = -1;
    private static final int SCHEDULER_PORT = 5000;
    private static final int FIRE_PORT = 5001;
    private static final int DRONE_PORT = 5002;
    private static final String HOST = "localhost";

    private boolean simulationStarted = false;
    private boolean endReceived = false;

    public Scheduler() throws Exception {
        socket = new DatagramSocket(SCHEDULER_PORT);
        socket.setSoTimeout(250);
        zones = Zone.loadFromCSV("SYSC3303-Project-Group5/sample_zone_file.csv");
    }

    private void sendMessage(Message msg, int port) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(msg);
        out.flush();
        byte[] data = bos.toByteArray();
        DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(HOST), port);
        socket.send(packet);

        if (port == DRONE_PORT && (msg.getType() == Message.Type.DISPATCH || msg.getType() == Message.Type.NO_TASK)) {
            outboundMessages.put(msg.getMessageId(), msg);
        }
    }

    private Message receiveMessage() throws Exception {
        byte[] buffer = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        ByteArrayInputStream bis = new ByteArrayInputStream(packet.getData(), 0, packet.getLength());
        ObjectInputStream in = new ObjectInputStream(bis);
        return (Message) in.readObject();
    }

    @Override
    public void run() {
        System.out.println("[Scheduler] Scheduler started on port " + SCHEDULER_PORT);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Message msg = receiveMessage();
                    System.out.println("[Scheduler] Received: " + msg);
                    handleMessage(msg);
                } catch (SocketTimeoutException e) {
                    checkResendTimeouts();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        socket.close();
        System.out.println("[Scheduler] Scheduler stopped");
    }

    public void setGui(SchedulerGUI gui) {
        this.gui = gui;
    }

    private void handleMessage(Message msg) throws Exception {
        if (!msg.isCrcValid()) {
            handleCorruptedMessage(msg);
            return;
        }

        String sourceDroneId = extractDroneId(msg);
        
        detectSequenceGapsFromDrone(msg, sourceDroneId);
        
        clearPendingResend(msg, sourceDroneId);

        if (sourceDroneId != null && !processedDroneMessages.add(msg.getMessageId())
                && (msg.getType() == Message.Type.REQUEST_TASK
                    || msg.getType() == Message.Type.DONE
                    || msg.getType() == Message.Type.PARTIAL_DONE
                    || msg.getType() == Message.Type.DRONE_STATUS)) {
            return;
        }

        switch (msg.getType()) {
            case EVENT:
                events.add(msg.getEvent());
                sendMessage(new Message(Message.Type.ACK, null, "Event received"), FIRE_PORT);
                if (gui != null) gui.addEvent(msg.getEvent());
                assignTasks();
                break;
            case REQUEST_TASK:
                if (!isDroneFailed(msg.getNote())) {
                    requestingDrones.add(msg.getNote());
                }
                assignTasks();
                break;
            case DONE:
                activeAssignments.remove(msg.getNote());
                sendMessage(new Message(Message.Type.ACK, null, "Done: " + msg.getEvent()), FIRE_PORT);
                if (gui != null) gui.updateEvent(msg.getEvent());
                assignTasks();
                break;
            case PARTIAL_DONE:
                activeAssignments.remove(msg.getNote());
                events.add(msg.getEvent());
                sendMessage(new Message(Message.Type.ACK, null, "Partial Done: " + msg.getEvent()), FIRE_PORT);
                if (gui != null) gui.updateEvent(msg.getEvent());
                assignTasks();
                break;
            case DRONE_STATUS:
                String[] parts = msg.getNote().split(",");
                String id = parts.length > 0 ? parts[0] : "";
                float battery = parseFloat(parts, 1, 0);
                int zone = parseInt(parts, 2, 0);
                float water = parseFloat(parts, 3, 0);
                int target = parseInt(parts, 4, 0);
                long lastAnim = parseLong(parts, 5, 0);
                long animStart = parseLong(parts, 6, 0);
                String state = parts.length > 7 ? parts[7] : "idle";
                DroneStatus ds = new DroneStatus(id, battery, zone, water, target, lastAnim, animStart, state);
                droneStatuses.put(id, ds);
                if (gui != null) gui.updateDrone(ds);
                break;
            case RESEND_REQUEST:
                handleResendRequest(msg);
                break;
            case COMM_FAILURE:
                handleDroneFailureMessage(msg);
                break;
            case START:
                simulationStarted = true;
                if (gui != null) gui.setStatus(true);
                System.out.println("STARTED !!!!!");
                break;
            case END_SIMULATION:
                endReceived = true;
                break;
            default:
                System.out.println("[Scheduler] Unknown message type: " + msg.getType());
        }
        if (simulationStarted && events.isEmpty() && allDronesAtBase() && endReceived) {
            if (gui != null) gui.setStatus(false);
            System.out.println("STOPPED !!!!!");
        }
    }

    private void assignTasks() throws Exception {
        while (!events.isEmpty() && !requestingDrones.isEmpty()) {
            Event event = events.remove(0);
            String bestDrone = selectBestDrone(event);
            if (bestDrone == null) {
                events.add(0, event);
                break;
            }
            requestingDrones.remove(bestDrone);
            tasksAssigned.merge(bestDrone, 1, Integer::sum);
            activeAssignments.put(bestDrone, event);
            System.out.println("[Scheduler] Dispatching " + bestDrone + " to zone " + event.getZoneID()
                    + " (tasks assigned: " + tasksAssigned.get(bestDrone) + ")");
            sendMessage(new Message(Message.Type.DISPATCH, event, bestDrone), DRONE_PORT);
        }

        Iterator<String> waitingIt = requestingDrones.iterator();
        while (waitingIt.hasNext()) {
            String droneId = waitingIt.next();
            waitingIt.remove();
            sendMessage(new Message(Message.Type.NO_TASK, null, droneId), DRONE_PORT);
        }
    }

    private String selectBestDrone(Event event) {
        String best = null;
        float bestScore = Float.MAX_VALUE;
        for (String droneId : requestingDrones) {
            if (isDroneFailed(droneId)) {
                continue;
            }
            DroneStatus status = droneStatuses.get(droneId);
            int tasks = tasksAssigned.getOrDefault(droneId, 0);

            // distance to fire zone to min wait time
            float distance = 0;
            if (status != null) {
                try {
                    Zone droneZone = new Zone(0, 0, 0, 0, 0);
                    if (status.zoneId > 0) {
                        droneZone = Zone.getZoneFromId(zones, status.zoneId);
                    }
                    Zone fireZone = Zone.getZoneFromId(zones, event.getZoneID());
                    distance = Zone.getDistance(droneZone, fireZone);
                } catch (Zone.UnknownZoneException e) {
                }
            }
            //task count
            float score = distance + (tasks * 500);
            if (best == null || score < bestScore) {
                bestScore = score;
                best = droneId;
            }

        }
        return best;
    }

    private boolean allDronesAtBase() {
        for (DroneStatus ds : droneStatuses.values()) {
            if (ds.zoneId != 0) return false;
        }
        return true;
    }

    private void handleCorruptedMessage(Message msg) {
        String droneId = extractDroneId(msg);
        if (droneId == null || droneId.isEmpty()) {
            return;
        }

        PendingResend pending = pendingResends.get(droneId);
        if (pending != null && pending.expectedMessageId.equals(msg.getMessageId())) {
            markDroneCommunicationFailed(droneId, "corrupted_again");
            return;
        }

        try {
            sendMessage(Message.resendRequest(droneId, msg.getMessageId()), DRONE_PORT);
            pendingResends.put(droneId, new PendingResend(msg.getMessageId(), System.currentTimeMillis()));
        } catch (Exception e) {
            markDroneCommunicationFailed(droneId, "failed_to_request_resend");
        }
    }

    private void handleResendRequest(Message request) {
        String droneId = request.getNote();
        if (droneId == null || droneId.isEmpty()) {
            return;
        }

        Message original = outboundMessages.get(request.getCorrelationId());
        if (original == null || !droneId.equals(original.getNote())) {
            markDroneCommunicationFailed(droneId, "scheduler_resend_missing");
            return;
        }

        if (original.getAttempt() > MAX_RESEND_ATTEMPTS + 1) {
            markDroneCommunicationFailed(droneId, "scheduler_resend_limit");
            return;
        }

        try {
            sendMessage(original.asResendAttempt(), DRONE_PORT);
        } catch (Exception e) {
            markDroneCommunicationFailed(droneId, "scheduler_resend_send_failed");
        }
    }

    private void clearPendingResend(Message msg, String sourceDroneId) {
        if (sourceDroneId == null) {
            return;
        }
        PendingResend pending = pendingResends.get(sourceDroneId);
        if (pending != null && pending.expectedMessageId.equals(msg.getMessageId())) {
            pendingResends.remove(sourceDroneId);
        }
    }

    private void checkResendTimeouts() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PendingResend>> it = pendingResends.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PendingResend> entry = it.next();
            if (now - entry.getValue().requestedAtMs > RESEND_TIMEOUT_MS) {
                String droneId = entry.getKey();
                it.remove();
                markDroneCommunicationFailed(droneId, "resend_timeout");
            }
        }
    }

    private void markDroneCommunicationFailed(String droneId, String reason) {
        markDroneFault(droneId, "commFailure", reason);
    }

    private void handleDroneFailureMessage(Message msg) {
        String droneId = extractDroneId(msg);
        String reason = extractFailureReason(msg.getNote());

        String faultState = "commFailure";
        if (reason.startsWith("fault_drone_stuck")) {
            faultState = "droneStuckFault";
        } else if (reason.startsWith("fault_arrival_sensor")) {
            faultState = "arrivalSensorFault";
        } else if (reason.startsWith("fault_nozzle_stuck")) {
            faultState = "nozzleStuckFault";
        }

        markDroneFault(droneId, faultState, reason.isEmpty() ? "peer_reported" : reason);
    }

    private String extractFailureReason(String note) {
        if (note == null || note.isEmpty()) {
            return "";
        }
        String[] parts = note.split(",", 2);
        return parts.length > 1 ? parts[1] : "";
    }

    private void markDroneFault(String droneId, String faultState, String reason) {
        if (droneId == null || droneId.isEmpty() || failedDrones.contains(droneId)) {
            return;
        }
        failedDrones.add(droneId);
        requestingDrones.removeIf(droneId::equals);

        Event assigned = activeAssignments.remove(droneId);
        if (assigned != null && assigned.currentState() != Event.State.EXTINGUISHED) {
            events.add(0, assigned);
        }

        DroneStatus prev = droneStatuses.get(droneId);
        DroneStatus failedStatus = new DroneStatus(droneId,
                prev == null ? 0 : prev.battery,
                prev == null ? 0 : prev.zoneId,
                prev == null ? 0 : prev.water,
                prev == null ? 0 : prev.targetZoneId,
                prev == null ? 0 : prev.lastAnimDurationMs,
                prev == null ? 0 : prev.animStartTime,
                faultState);
        droneStatuses.put(droneId, failedStatus);
        if (gui != null) {
            gui.updateDrone(failedStatus);
        }

        System.out.println("[Scheduler] Marked " + droneId + " fault=" + faultState + " (" + reason + ")");
        try {
            if ("commFailure".equals(faultState)) {
                sendMessage(Message.commFailure(droneId, reason), DRONE_PORT);
            }
            assignTasks();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void detectSequenceGapsFromDrone(Message msg, String sourceDroneId) {
        Message.Type type = msg.getType();
        long inboundSeq = msg.getSequenceNumber();

        // Track one sequence stream for all messages coming from DroneSubsystem process.
        // Fault only on task-control packets; status/failure chatter still advances baseline.
        if (type == Message.Type.REQUEST_TASK
                || type == Message.Type.DONE
                || type == Message.Type.PARTIAL_DONE
                || type == Message.Type.DRONE_STATUS
                || type == Message.Type.COMM_FAILURE
                || type == Message.Type.RESEND_REQUEST) {
            if (lastReceivedSequenceFromDroneSubsystem >= 0) {
                if (inboundSeq <= lastReceivedSequenceFromDroneSubsystem) {
                    // UDP can reorder/duplicate packets; ignore non-forward movement.
                    return;
                }

                long gap = inboundSeq - lastReceivedSequenceFromDroneSubsystem;
                // REQUEST_TASK is frequent and non-critical; dropped task requests are retried naturally.
                // Only fault on completion-critical control messages.
                boolean taskControlType = type == Message.Type.DONE
                        || type == Message.Type.PARTIAL_DONE;

                // Always advance baseline once we've observed forward progress.
                lastReceivedSequenceFromDroneSubsystem = inboundSeq;

                if (taskControlType && gap > 1 && gap <= MAX_SEQUENCE_GAP) {
                    if (sourceDroneId != null && !sourceDroneId.isEmpty()) {
                        System.out.println("[Scheduler] Detected packet loss from DroneSubsystem"
                                + " (expected seq=" + (inboundSeq - gap + 1)
                                + ", got seq=" + inboundSeq + ", lastDrone=" + sourceDroneId + ")");
                        markDroneCommunicationFailed(sourceDroneId, "packet_loss_detected");
                    }
                    return;
                }
                return;
            }

            lastReceivedSequenceFromDroneSubsystem = inboundSeq;
            return;
        }

        // FireIncidentSubsystem is also a separate sender with its own sequence counter.
        if (type == Message.Type.START || type == Message.Type.EVENT || type == Message.Type.END_SIMULATION) {
            if (lastReceivedSequenceFromFireSubsystem >= 0) {
                if (inboundSeq <= lastReceivedSequenceFromFireSubsystem) {
                    return;
                }
                long gap = inboundSeq - lastReceivedSequenceFromFireSubsystem;
                long expectedSeq = lastReceivedSequenceFromFireSubsystem + 1;
                lastReceivedSequenceFromFireSubsystem = inboundSeq;
                if (gap > 1 && gap <= MAX_SEQUENCE_GAP) {
                    System.out.println("[Scheduler] Detected packet loss from FireIncidentSubsystem"
                            + " (expected seq=" + expectedSeq
                            + ", got seq=" + inboundSeq + ")");
                }
                return;
            }
            lastReceivedSequenceFromFireSubsystem = inboundSeq;
        }
    }

    private boolean isDroneFailed(String droneId) {
        return failedDrones.contains(droneId);
    }

    private String extractDroneId(Message msg) {
        if (msg.getNote() == null || msg.getNote().isEmpty()) {
            return null;
        }
        switch (msg.getType()) {
            case DRONE_STATUS:
            case COMM_FAILURE:
                return msg.getNote().split(",")[0];
            case REQUEST_TASK:
            case DONE:
            case PARTIAL_DONE:
            case DISPATCH:
            case NO_TASK:
            case RESEND_REQUEST:
                return msg.getNote();
            default:
                return null;
        }
    }

    private float parseFloat(String[] parts, int idx, float defaultValue) {
        if (idx >= parts.length) return defaultValue;
        try { return Float.parseFloat(parts[idx]); } catch (Exception e) { return defaultValue; }
    }

    private int parseInt(String[] parts, int idx, int defaultValue) {
        if (idx >= parts.length) return defaultValue;
        try { return Integer.parseInt(parts[idx]); } catch (Exception e) { return defaultValue; }
    }

    private long parseLong(String[] parts, int idx, long defaultValue) {
        if (idx >= parts.length) return defaultValue;
        try { return Long.parseLong(parts[idx]); } catch (Exception e) { return defaultValue; }
    }

    private static class PendingResend {
        final String expectedMessageId;
        final long requestedAtMs;

        PendingResend(String expectedMessageId, long requestedAtMs) {
            this.expectedMessageId = expectedMessageId;
            this.requestedAtMs = requestedAtMs;
        }
    }

    public static class DroneStatus {
        String id;
        float battery;
        int zoneId;
        float water;
        int targetZoneId;
        long lastAnimDurationMs;
        long animStartTime;
        String state;
        DroneStatus(String i, float b, int z, float w, int t, long la, long as, String s) { id = i; battery = b; zoneId = z; water = w; targetZoneId = t; lastAnimDurationMs = la; animStartTime = as; state = s; }
    }

    public static void main(String[] args) throws Exception {
        new Thread(new Scheduler()).start();
    }
}
