import java.net.*;
import java.io.*;
import java.util.*;
import java.nio.file.*;

public class Scheduler implements Runnable {
    private DatagramSocket socket;
    public static final int simulationSpeed = 10; //1000 for real-time, do 10 or less to quickly test

    private final List<Event> events = new LinkedList<>();
    private final Map<String, DroneStatus> droneStatuses = new HashMap<>();
    private final Set<String> failedDrones = new HashSet<>();
    private final Set<String> nozzleFaultPendingRepair = new HashSet<>();
    private final Map<String, Set<String>> blockedEventKeysByDrone = new HashMap<>();
    private final Map<String, Event> activeAssignments = new HashMap<>();
    private final Map<String, Message> outboundMessages = new HashMap<>();
    private final Map<String, PendingResend> pendingResends = new HashMap<>();
    private final Map<String, ExpectedArrival> expectedArrivals = new HashMap<>();
    private final Set<String> processedDroneMessages = new HashSet<>();
    private SchedulerGUI gui;
    private final Queue<String> requestingDrones = new LinkedList<>();
    private final Map<String, Integer> tasksAssigned = new HashMap<>();
    private final List<Zone> zones;

    private static final long RESEND_TIMEOUT_MS = 2500;
    // Extra grace so a normal dispatch/travel cycle does not get mistaken for a stuck drone,
    // especially on the first fire where startup/UI/network jitter is highest.
    private static final float ARRIVAL_TIMEOUT_FACTOR = 2.0f;
    private static final long ARRIVAL_TIMEOUT_BUFFER_MS = 1000;
    private static final int MAX_RESEND_ATTEMPTS = 1;
    private static final long MAX_SEQUENCE_GAP = 5;
    private final Map<String, Long> lastReceivedSequenceFromDroneSubsystem = new HashMap<>();
    private long lastReceivedSequenceFromFireSubsystem = -1;
    private static final int SCHEDULER_PORT = 5000;
    private static final int FIRE_PORT = 5001;
    private static final int DRONE_PORT = 5002;
    private static final String HOST = "localhost";

    private boolean simulationStarted = false;
    private boolean endReceived = false;
    private boolean completionNotified = false;

    // Simulation instrumentation
    private boolean metricsFinalized = false;
    private long simulationStartMs = -1;
    private long simulationEndMs = -1;
    private long firstIncidentDetectedMs = -1;
    private long lastIncidentExtinguishedMs = -1;
    private String metricsReportPath = "";

    private final Map<String, Long> incidentDetectedMs = new HashMap<>();
    private final Map<String, Long> incidentFirstDispatchMs = new HashMap<>();
    private final Map<String, Long> incidentExtinguishedMs = new HashMap<>();
    private final Map<String, Long> incidentLatencyMs = new HashMap<>();
    private final Map<String, Float> incidentDispatchDistance = new HashMap<>();
    private final Map<String, String> incidentDetectedSimTime = new HashMap<>();
    private final Map<String, String> incidentExtinguishedSimTime = new HashMap<>();

    private final Map<String, String> lastDroneState = new HashMap<>();
    private final Map<String, Long> lastDroneStateTimestamp = new HashMap<>();
    private final Map<String, Long> droneIdleMs = new HashMap<>();
    private final Map<String, Long> droneFlightMs = new HashMap<>();
    private final Map<String, Float> droneDispatchDistance = new HashMap<>();
    private final Map<String, Integer> droneFiresExtinguished = new HashMap<>();
    private final Map<String, Float> droneWaterDropped = new HashMap<>();
    private float totalDispatchDistance = 0f;

    private static String ts() {
        return "[" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + "]";
    }

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
                    checkArrivalTimeouts();
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
                System.out.println(ts() + " [Scheduler] Fire event received: zone=" + msg.getEvent().getZoneID() + " severity=" + msg.getEvent().getSeverity());
                events.add(msg.getEvent());
                recordIncidentDetected(msg.getEvent());
                sendMessage(new Message(Message.Type.ACK, null, "Event received"), FIRE_PORT);
                if (gui != null) gui.addEvent(msg.getEvent());
                assignTasks();
                break;
            case REQUEST_TASK:
                if (!isDroneFailed(msg.getNote())) {
                    System.out.println(ts() + " [Scheduler] Drone idle: " + msg.getNote());
                    requestingDrones.add(msg.getNote());
                }
                assignTasks();
                break;
            case DONE:
                Event completedAssigned = activeAssignments.remove(msg.getNote());
                expectedArrivals.remove(msg.getNote());
                if (completedAssigned != null && msg.getEvent() != null) {
                    recordDroneWaterDrop(msg.getNote(), completedAssigned, msg.getEvent());
                }
                recordDroneFireExtinguished(msg.getNote(), msg.getEvent());
                recordIncidentExtinguished(msg.getEvent());
                sendMessage(new Message(Message.Type.ACK, null, "Done: " + msg.getEvent()), FIRE_PORT);
                if (gui != null) gui.updateEvent(msg.getEvent());
                assignTasks();
                break;
            case PARTIAL_DONE:
                Event assigned = activeAssignments.get(msg.getNote());
                expectedArrivals.remove(msg.getNote());
                Event reported = msg.getEvent();
                DroneStatus reporterStatus = droneStatuses.get(msg.getNote());
                boolean nozzleFaultEvent = reported != null
                        && reported.getFaultType() == Event.FaultType.NOZZLE_STUCK_OPEN;
                boolean atDropContext = reporterStatus != null
                        && "droppingAgent".equalsIgnoreCase(reporterStatus.state);
                boolean noWaterDropped = assigned != null
                        && reported != null
                        && Math.abs(assigned.getWaterLeft() - reported.getWaterLeft()) < 0.0001f;
                if (nozzleFaultEvent && atDropContext && noWaterDropped) {
                    // Drone attempted a drop but fire water requirement did not change => nozzle jam fault.
                    markDroneFault(msg.getNote(), "nozzleStuckFault", "nozzle_jam_detected");
                    sendMessage(new Message(Message.Type.ACK, null, "Partial Done: nozzle jam"), FIRE_PORT);
                } else {
                    // Keep ownership with the same drone; do not enqueue for others unless a fault occurs.
                    if (reported != null) {
                        activeAssignments.put(msg.getNote(), reported);
                        recordDroneWaterDrop(msg.getNote(), assigned, reported);
                    }
                    sendMessage(new Message(Message.Type.ACK, null, "Partial Done: " + reported), FIRE_PORT);
                    if (gui != null) gui.updateEvent(reported);
                }
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
                String rawState = parts.length > 7 ? parts[7] : "idle";
                String state = rawState;
                String faultState = "";
                if (rawState.contains("|")) {
                    String[] stateParts = rawState.split("\\|", 2);
                    state = stateParts[0];
                    faultState = stateParts.length > 1 ? stateParts[1] : "";
                }
                DroneStatus ds = new DroneStatus(id, battery, zone, water, target, lastAnim, animStart, state, faultState);
                updateDroneTimeBuckets(ds);
                recoverNozzleFaultIfRepaired(ds);
                droneStatuses.put(id, ds);
                armExpectedArrivalIfMoving(ds);
                clearExpectedArrivalIfReached(ds);
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
                completionNotified = false;
                resetMetrics();
                simulationStartMs = System.currentTimeMillis();
                if (gui != null) gui.setStatus(true);
                System.out.println("STARTED !!!!!");
                break;
            case END_SIMULATION:
                endReceived = true;
                break;
            default:
                System.out.println("[Scheduler] Unknown message type: " + msg.getType());
        }
        if (!completionNotified && canMarkSimulationComplete()) {
            completionNotified = true;
            // Tell both subsystems that simulation is over so they stop cleanly.
            sendMessage(new Message(Message.Type.END_SIMULATION, null, "scheduler_complete"), FIRE_PORT);
            sendMessage(new Message(Message.Type.END_SIMULATION, null, "scheduler_complete"), DRONE_PORT);
            finalizeAndPublishMetrics();
            if (gui != null) gui.setStatus(false);
            System.out.println("STOPPED !!!!!");
        }
    }

    private boolean canMarkSimulationComplete() {
        if (!simulationStarted || !endReceived) {
            return false;
        }
        if (!events.isEmpty()) {
            return false;
        }
        if (!activeAssignments.isEmpty()) {
            return false;
        }
        if (!allDronesAtBase()) {
            return false;
        }
        // Do not complete while any detected incident is not yet fully extinguished.
        if (!incidentDetectedMs.isEmpty() && incidentExtinguishedMs.size() < incidentDetectedMs.size()) {
            return false;
        }
        return true;
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
            recordDispatch(bestDrone, event);
            System.out.println(ts() + " [Scheduler] Dispatching " + bestDrone + " to zone " + event.getZoneID()
                    + " (tasks assigned: " + tasksAssigned.get(bestDrone) + ")");
            trackExpectedArrival(bestDrone, event);
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
        String eventKey = eventKey(event);
        for (String droneId : requestingDrones) {
            if (isDroneFailed(droneId)) {
                continue;
            }
            if (blockedEventKeysByDrone.getOrDefault(droneId, Collections.emptySet()).contains(eventKey)) {
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

    private void checkArrivalTimeouts() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, ExpectedArrival>> it = expectedArrivals.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ExpectedArrival> entry = it.next();
            String droneId = entry.getKey();
            ExpectedArrival expected = entry.getValue();
            if (!expected.armed) {
                continue;
            }
            DroneStatus current = droneStatuses.get(droneId);
            if (current != null && current.zoneId == expected.zoneId) {
                it.remove();
                continue;
            }
            if (now > expected.deadlineMs) {
                it.remove();
                markDroneFault(droneId, "droneStuckFault", "arrival_timeout");
            }
        }
    }

    private void markDroneCommunicationFailed(String droneId, String reason) {
        markDroneFault(droneId, "commFailure", reason);
    }

    private void handleDroneFailureMessage(Message msg) {
        String droneId = extractDroneId(msg);
        String reason = extractFailureReason(msg.getNote());
        // Drones should not explicitly classify motion/nozzle faults; only comm failures are accepted here.
        markDroneCommunicationFailed(droneId, reason.isEmpty() ? "peer_reported" : reason);
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
        if ("nozzleStuckFault".equals(faultState)) {
            nozzleFaultPendingRepair.add(droneId);
        } else {
            nozzleFaultPendingRepair.remove(droneId);
        }
        requestingDrones.removeIf(droneId::equals);
        expectedArrivals.remove(droneId);

        Event assigned = activeAssignments.remove(droneId);
        if (assigned != null && assigned.currentState() != Event.State.EXTINGUISHED) {
            if ("nozzleStuckFault".equals(faultState)) {
                blockedEventKeysByDrone
                        .computeIfAbsent(droneId, k -> new HashSet<>())
                        .add(eventKey(assigned));
            }
            // Re-queue without the fault so the next drone handles it normally
            Event retry = new Event(assigned.getTime(), assigned.getZoneID(),
                    assigned.getEventType(), assigned.getSeverity(), Event.FaultType.NONE);
            retry.setWaterLeft(assigned.getWaterLeft());
            retry.deliverEvent(Event.State.PENDING);
            events.add(0, retry);
            if (gui != null) {
                gui.updateEvent(retry);
            }
        }

        DroneStatus prev = droneStatuses.get(droneId);
        DroneStatus failedStatus = new DroneStatus(droneId,
                prev == null ? 0 : prev.battery,
                prev == null ? 0 : prev.zoneId,
                prev == null ? 0 : prev.water,
                prev == null ? 0 : prev.targetZoneId,
                prev == null ? 0 : prev.lastAnimDurationMs,
                prev == null ? 0 : prev.animStartTime,
                faultState,
                faultState);
        droneStatuses.put(droneId, failedStatus);
        if (gui != null) {
            gui.updateDrone(failedStatus);
        }

        System.out.println(ts() + " [Scheduler] Marked " + droneId + " fault=" + faultState + " (" + reason + ")");
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
        String sequenceKey = sequenceTrackingKey(msg, sourceDroneId);

        // Track one sequence stream for all messages coming from DroneSubsystem process.
        // Track per-drone sequence streams so one drone's packet gap cannot fault another drone.
        if (type == Message.Type.REQUEST_TASK
                || type == Message.Type.DONE
                || type == Message.Type.PARTIAL_DONE
                || type == Message.Type.DRONE_STATUS
                || type == Message.Type.COMM_FAILURE
                || type == Message.Type.RESEND_REQUEST) {
            if (sequenceKey == null || sequenceKey.isEmpty()) {
                return;
            }

            Long lastSeq = lastReceivedSequenceFromDroneSubsystem.get(sequenceKey);
            if (lastSeq != null) {
                if (inboundSeq <= lastSeq) {
                    // UDP can reorder/duplicate packets; ignore non-forward movement.
                    return;
                }

                long gap = inboundSeq - lastSeq;
                // REQUEST_TASK is frequent and non-critical; dropped task requests are retried naturally.
                // Only fault on completion-critical control messages.
                boolean taskControlType = type == Message.Type.DONE
                        || type == Message.Type.PARTIAL_DONE;

                // Always advance baseline once we've observed forward progress.
                lastReceivedSequenceFromDroneSubsystem.put(sequenceKey, inboundSeq);

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

            lastReceivedSequenceFromDroneSubsystem.put(sequenceKey, inboundSeq);
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

    private String sequenceTrackingKey(Message msg, String sourceDroneId) {
        if (sourceDroneId != null && !sourceDroneId.isEmpty()) {
            return sourceDroneId;
        }
        if (msg.getType() == Message.Type.DRONE_STATUS || msg.getType() == Message.Type.COMM_FAILURE) {
            return extractDroneId(msg);
        }
        return sourceDroneId;
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

    private void trackExpectedArrival(String droneId, Event event) {
        expectedArrivals.put(droneId, new ExpectedArrival(event.getZoneID()));
    }

    private void armExpectedArrivalIfMoving(DroneStatus status) {
        ExpectedArrival expected = expectedArrivals.get(status.id);
        if (expected == null || expected.armed) {
            return;
        }
        boolean headingToExpectedZone = status.targetZoneId == expected.zoneId;
        boolean movingToFire = "enRoute".equalsIgnoreCase(status.state) || "enroute".equalsIgnoreCase(status.state);
        if (!headingToExpectedZone || !movingToFire) {
            return;
        }

        long observedTravelMs = status.lastAnimDurationMs > 0 ? status.lastAnimDurationMs : estimateTravelToZoneMs(status.id, expected.zoneId);
        long timeoutMs = (long) (observedTravelMs * ARRIVAL_TIMEOUT_FACTOR) + ARRIVAL_TIMEOUT_BUFFER_MS;
        expected.deadlineMs = status.animStartTime + Math.max(1, timeoutMs);
        expected.armed = true;
    }

    private long estimateTravelToZoneMs(String droneId, int zoneId) {
        try {
            Zone origin = new Zone(0, 0, 0, 0, 0);
            DroneStatus status = droneStatuses.get(droneId);
            Zone from = origin;
            if (status != null && status.zoneId > 0) {
                from = Zone.getZoneFromId(zones, status.zoneId);
            }
            Zone to = Zone.getZoneFromId(zones, zoneId);
            float distance = Zone.getDistance(from, to);
            float travelMinutes = distance / Drone.DRONE_SPEED;
            return (long) (travelMinutes * 60 * simulationSpeed);
        } catch (Exception ignored) {
            return 1000;
        }
    }

    private void clearExpectedArrivalIfReached(DroneStatus status) {
        ExpectedArrival expected = expectedArrivals.get(status.id);
        if (expected != null && status.zoneId == expected.zoneId) {
            expectedArrivals.remove(status.id);
        }
    }

    private void recoverNozzleFaultIfRepaired(DroneStatus ds) {
        if (ds == null || ds.id == null || ds.id.isEmpty()) {
            return;
        }
        if (!failedDrones.contains(ds.id) || !nozzleFaultPendingRepair.contains(ds.id)) {
            return;
        }
        boolean repairedAtBase = ds.zoneId == 0 && "idle".equalsIgnoreCase(ds.state);
        if (!repairedAtBase) {
            return;
        }

        failedDrones.remove(ds.id);
        nozzleFaultPendingRepair.remove(ds.id);
        System.out.println(ts() + " [Scheduler] Cleared nozzle fault for " + ds.id + " (repaired at base)");
    }

    private void resetMetrics() {
        metricsFinalized = false;
        simulationStartMs = -1;
        simulationEndMs = -1;
        firstIncidentDetectedMs = -1;
        lastIncidentExtinguishedMs = -1;
        metricsReportPath = "";

        incidentDetectedMs.clear();
        incidentFirstDispatchMs.clear();
        incidentExtinguishedMs.clear();
        incidentLatencyMs.clear();
        incidentDispatchDistance.clear();
        incidentDetectedSimTime.clear();
        incidentExtinguishedSimTime.clear();

        lastDroneState.clear();
        lastDroneStateTimestamp.clear();
        droneIdleMs.clear();
        droneFlightMs.clear();
        droneDispatchDistance.clear();
        droneFiresExtinguished.clear();
        droneWaterDropped.clear();
        totalDispatchDistance = 0f;
    }

    private void updateDroneTimeBuckets(DroneStatus status) {
        if (status == null || status.id == null || status.id.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lastTs = lastDroneStateTimestamp.get(status.id);
        String prev = lastDroneState.get(status.id);
        if (lastTs != null && prev != null && now >= lastTs) {
            long delta = now - lastTs;
            if (isIdleState(prev)) {
                droneIdleMs.merge(status.id, delta, Long::sum);
            }
            if (isFlightState(prev)) {
                droneFlightMs.merge(status.id, delta, Long::sum);
            }
        }
        lastDroneState.put(status.id, status.state);
        lastDroneStateTimestamp.put(status.id, now);
    }

    private boolean isIdleState(String state) {
        return "idle".equalsIgnoreCase(state);
    }

    private boolean isFlightState(String state) {
        return "enRoute".equalsIgnoreCase(state)
                || "enroute".equalsIgnoreCase(state)
                || "returnOrigin".equalsIgnoreCase(state)
                || "returnForRefill".equalsIgnoreCase(state);
    }

    private void recordIncidentDetected(Event event) {
        if (event == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = eventKey(event);
        incidentDetectedMs.putIfAbsent(key, now);
        incidentDetectedSimTime.putIfAbsent(key, event.getTime());
        if (firstIncidentDetectedMs < 0 || now < firstIncidentDetectedMs) {
            firstIncidentDetectedMs = now;
        }
    }

    private void recordDroneWaterDrop(String droneId, Event before, Event after) {
        if (droneId == null || before == null || after == null) {
            return;
        }
        float dropped = Math.max(0f, before.getWaterLeft() - after.getWaterLeft());
        droneWaterDropped.merge(droneId, dropped, Float::sum);
    }

    private void recordDroneFireExtinguished(String droneId, Event event) {
        if (droneId == null || droneId.isEmpty() || event == null) {
            return;
        }
        droneFiresExtinguished.merge(droneId, 1, Integer::sum);
    }

    private void recordDispatch(String droneId, Event event) {
        if (droneId == null || event == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = eventKey(event);
        incidentFirstDispatchMs.putIfAbsent(key, now);

        float distance = estimateDispatchDistance(droneId, event);
        incidentDispatchDistance.merge(key, distance, Float::sum);
        droneDispatchDistance.merge(droneId, distance, Float::sum);
        totalDispatchDistance += distance;
    }

    private float estimateDispatchDistance(String droneId, Event event) {
        try {
            Zone from = new Zone(0, 0, 0, 0, 0);
            DroneStatus status = droneStatuses.get(droneId);
            if (status != null && status.zoneId > 0) {
                from = Zone.getZoneFromId(zones, status.zoneId);
            }
            Zone to = Zone.getZoneFromId(zones, event.getZoneID());
            return Zone.getDistance(from, to);
        } catch (Exception ignored) {
            return 0f;
        }
    }

    private void recordIncidentExtinguished(Event event) {
        if (event == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String key = eventKey(event);
        incidentExtinguishedMs.put(key, now);
        incidentExtinguishedSimTime.put(key, event.getTime());
        Long detected = incidentDetectedMs.get(key);
        if (detected != null && now >= detected) {
            incidentLatencyMs.put(key, now - detected);
        }
        if (lastIncidentExtinguishedMs < 0 || now > lastIncidentExtinguishedMs) {
            lastIncidentExtinguishedMs = now;
        }
    }

    private void finalizeAndPublishMetrics() {
        if (metricsFinalized) {
            return;
        }
        metricsFinalized = true;
        simulationEndMs = System.currentTimeMillis();
        finalizeOpenDroneStateDurations(simulationEndMs);

        String reportBody = buildMetricsReport();
        writeMetricsReport(reportBody);
        publishMetricsSummaryToGui();
    }

    private void finalizeOpenDroneStateDurations(long now) {
        for (String droneId : new HashSet<>(lastDroneState.keySet())) {
            Long lastTs = lastDroneStateTimestamp.get(droneId);
            String state = lastDroneState.get(droneId);
            if (lastTs == null || state == null || now < lastTs) {
                continue;
            }
            long delta = now - lastTs;
            if (isIdleState(state)) {
                droneIdleMs.merge(droneId, delta, Long::sum);
            }
            if (isFlightState(state)) {
                droneFlightMs.merge(droneId, delta, Long::sum);
            }
            lastDroneStateTimestamp.put(droneId, now);
        }
    }

    private String buildMetricsReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Simulation Metrics Report\n");
        sb.append("Generated: ").append(new Date()).append("\n\n");

        long totalSimulationMs = (simulationStartMs >= 0 && simulationEndMs >= simulationStartMs)
                ? (simulationEndMs - simulationStartMs) : -1;
        long totalFireWindowMs = (firstIncidentDetectedMs >= 0 && lastIncidentExtinguishedMs >= firstIncidentDetectedMs)
                ? (lastIncidentExtinguishedMs - firstIncidentDetectedMs) : -1;

        sb.append("Overview\n");
        sb.append("- Incidents detected: ").append(incidentDetectedMs.size()).append("\n");
        sb.append("- Incidents extinguished: ").append(incidentExtinguishedMs.size()).append("\n");
        sb.append("- First fire simulated timestamp: ").append(firstSimulatedTimestamp()).append("\n");
        sb.append("- Last fire extinguished simulated timestamp: ").append(lastExtinguishedSimulatedTimestamp()).append("\n");
        sb.append("- Total simulation time: ").append(formatDuration(totalSimulationMs)).append("\n");
        sb.append("- Time from first incident to last extinguished: ").append(formatDuration(totalFireWindowMs)).append("\n");
        sb.append("- Total dispatch distance: ").append(String.format(Locale.US, "%.2f", totalDispatchDistance)).append("\n\n");

        sb.append("Incident Metrics\n");
        List<String> incidentKeys = new ArrayList<>(incidentDetectedMs.keySet());
        Collections.sort(incidentKeys);
        for (String key : incidentKeys) {
            long detect = incidentDetectedMs.getOrDefault(key, -1L);
            long dispatch = incidentFirstDispatchMs.getOrDefault(key, -1L);
            long extinguish = incidentExtinguishedMs.getOrDefault(key, -1L);
            long latency = incidentLatencyMs.getOrDefault(key, -1L);
            float dist = incidentDispatchDistance.getOrDefault(key, 0f);
            sb.append("- ").append(key)
                    .append(" | first response=").append(formatDuration(dispatch >= detect && detect >= 0 ? dispatch - detect : -1))
                    .append(" | detect->extinguish=").append(formatDuration(latency))
                    .append(" | dispatch distance=").append(String.format(Locale.US, "%.2f", dist))
                    .append("\n");
        }
        sb.append("\n");

        sb.append("Drone Metrics\n");
        Set<String> droneIds = new TreeSet<>(droneStatuses.keySet());
        for (String droneId : droneIds) {
            long idle = droneIdleMs.getOrDefault(droneId, 0L);
            long flight = droneFlightMs.getOrDefault(droneId, 0L);
            float dist = droneDispatchDistance.getOrDefault(droneId, 0f);
            int fires = droneFiresExtinguished.getOrDefault(droneId, 0);
            float water = droneWaterDropped.getOrDefault(droneId, 0f);
            sb.append("- ").append(droneId)
                    .append(" | idle=").append(formatDuration(idle))
                    .append(" | flight=").append(formatDuration(flight))
                    .append(" | dispatch distance=").append(String.format(Locale.US, "%.2f", dist))
                    .append(" | fires extinguished=").append(fires)
                    .append(" | water dropped=").append(String.format(Locale.US, "%.2f", water))
                    .append("\n");
        }

        return sb.toString();
    }

    private void writeMetricsReport(String body) {
        try {
            String fileName = "simulation_metrics_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt";
            Path output = Paths.get("SYSC3303-Project-Group5", fileName);
            Files.writeString(output, body, java.nio.charset.StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            metricsReportPath = output.toAbsolutePath().toString();
            System.out.println(ts() + " [Scheduler] Metrics written to " + metricsReportPath);
        } catch (Exception e) {
            metricsReportPath = "write_failed";
            System.out.println(ts() + " [Scheduler] Failed to write metrics report: " + e.getMessage());
        }
    }

    private void publishMetricsSummaryToGui() {
        if (gui == null) {
            return;
        }
        long totalSimulationMs = (simulationStartMs >= 0 && simulationEndMs >= simulationStartMs)
                ? (simulationEndMs - simulationStartMs) : -1;
        long totalFireWindowMs = (firstIncidentDetectedMs >= 0 && lastIncidentExtinguishedMs >= firstIncidentDetectedMs)
                ? (lastIncidentExtinguishedMs - firstIncidentDetectedMs) : -1;

        long avgLatency = averageLong(incidentLatencyMs.values());
        long avgIdle = averageLong(droneIdleMs.values());
        long avgFlight = averageLong(droneFlightMs.values());

        gui.logEvent("=== Simulation Metrics ===");
        gui.logEvent("Incidents extinguished: " + incidentExtinguishedMs.size() + "/" + incidentDetectedMs.size());
        gui.logEvent("Total simulation time: " + formatDuration(totalSimulationMs));
        gui.logEvent("First incident -> last extinguished: " + formatDuration(totalFireWindowMs));
        gui.logEvent("First fire simulated timestamp: " + firstSimulatedTimestamp());
        gui.logEvent("Last fire extinguished simulated timestamp: " + lastExtinguishedSimulatedTimestamp());
        gui.logEvent("Avg incident detect->extinguish: " + formatDuration(avgLatency));
        gui.logEvent("Avg drone idle time: " + formatDuration(avgIdle));
        gui.logEvent("Avg drone flight time: " + formatDuration(avgFlight));
        gui.logEvent("Total dispatch distance: " + String.format(Locale.US, "%.2f", totalDispatchDistance));
        for (String droneId : new TreeSet<>(droneStatuses.keySet())) {
            gui.logEvent(droneId + " | fires extinguished: " + droneFiresExtinguished.getOrDefault(droneId, 0)
                    + " | water dropped: " + String.format(Locale.US, "%.2f", droneWaterDropped.getOrDefault(droneId, 0f)));
        }
        if (!metricsReportPath.isEmpty()) {
            gui.logEvent("Metrics saved: " + metricsReportPath);
        }
    }

    private long averageLong(Collection<Long> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        long total = 0;
        int count = 0;
        for (Long value : values) {
            if (value == null) {
                continue;
            }
            total += value;
            count++;
        }
        return count == 0 ? 0 : (total / count);
    }

    private String formatDuration(long millis) {
        if (millis < 0) {
            return "n/a";
        }
        long seconds = millis / 1000;
        long ms = millis % 1000;
        long minutes = seconds / 60;
        long secPart = seconds % 60;
        return String.format(Locale.US, "%dm %02ds %03dms", minutes, secPart, ms);
    }

    private String firstSimulatedTimestamp() {
        if (incidentDetectedSimTime.isEmpty()) {
            return "n/a";
        }
        return incidentDetectedSimTime.entrySet().stream()
                .min(Comparator.comparingLong(e -> incidentDetectedMs.getOrDefault(e.getKey(), Long.MAX_VALUE)))
                .map(Map.Entry::getValue)
                .orElse("n/a");
    }

    private String lastExtinguishedSimulatedTimestamp() {
        if (incidentExtinguishedSimTime.isEmpty()) {
            return "n/a";
        }
        return incidentExtinguishedSimTime.entrySet().stream()
                .max(Comparator.comparingLong(e -> incidentExtinguishedMs.getOrDefault(e.getKey(), Long.MIN_VALUE)))
                .map(Map.Entry::getValue)
                .orElse("n/a");
    }

    private String eventKey(Event event) {
        if (event == null) {
            return "";
        }
        return event.getTime() + "#" + event.getZoneID();
    }

    private static class PendingResend {
        final String expectedMessageId;
        final long requestedAtMs;

        PendingResend(String expectedMessageId, long requestedAtMs) {
            this.expectedMessageId = expectedMessageId;
            this.requestedAtMs = requestedAtMs;
        }
    }

    private static class ExpectedArrival {
        final int zoneId;
        long deadlineMs;
        boolean armed;

        ExpectedArrival(int zoneId) {
            this.zoneId = zoneId;
            this.deadlineMs = Long.MAX_VALUE;
            this.armed = false;
        }
    }

    public void injectFault(String droneId, String faultCode) throws Exception {
        System.out.println("[Scheduler] Injecting fault: " + faultCode + " on " + droneId);
        sendMessage(new Message(Message.Type.FAULT_INJECT, null, droneId + "," + faultCode), DRONE_PORT);
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
        String faultState;
        DroneStatus(String i, float b, int z, float w, int t, long la, long as, String s, String fs) {
            id = i;
            battery = b;
            zoneId = z;
            water = w;
            targetZoneId = t;
            lastAnimDurationMs = la;
            animStartTime = as;
            state = s;
            faultState = fs == null ? "" : fs;
        }
    }

    public static void main(String[] args) throws Exception {
        new Thread(new Scheduler()).start();
    }
}
