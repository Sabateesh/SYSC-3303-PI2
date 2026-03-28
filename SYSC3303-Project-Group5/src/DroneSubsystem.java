import java.net.*;
import java.io.*;
import java.util.*;

public class DroneSubsystem implements Runnable {

    private DatagramSocket socket;
    private final List<Zone> zones;
    public final static int NUM_DRONES = 3;
    private final List<Thread> droneThreads;
    private final Map<String, Queue<Event>> taskQueues = new HashMap<>();
    private final Object taskLock = new Object();
    private final Map<String, Message> outboundMessages = new HashMap<>();
    private final Map<String, Integer> resendCounts = new HashMap<>();
    private final Map<String, PendingResend> pendingResends = new HashMap<>();
    private final Set<String> communicationFailedDrones = new HashSet<>();
    private long lastReceivedSequenceFromScheduler = -1;
    private static final long MAX_SEQUENCE_GAP = 5;
    private static final long RESEND_TIMEOUT_MS = 2500;
    private static final int MAX_RESEND_ATTEMPTS = 1;

    private static final int SCHEDULER_PORT = 5000;
    private static final int DRONE_PORT = 5002;
    private static final String HOST = "localhost";

    public DroneSubsystem() throws Exception {
        socket = new DatagramSocket(DRONE_PORT);
        socket.setSoTimeout(250);
        zones = Zone.loadFromCSV("SYSC3303-Project-Group5/sample_zone_file.csv");
        droneThreads = new ArrayList<>();
        initializeDrones();
    }

    private void initializeDrones() {
        for(int i = 0; i < NUM_DRONES; i++) {
            String droneName = "Drone-" + i;
            taskQueues.put(droneName, new LinkedList<>());
            Drone drone = new Drone(this, droneName, zones);
            Thread droneThread = new Thread(drone, droneName);
            droneThreads.add(droneThread);
            droneThread.start();
        }
    }

    private synchronized void sendMessage(Message msg) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(msg);
        out.flush();
        byte[] data = bos.toByteArray();
        DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(HOST), SCHEDULER_PORT);
        socket.send(packet);

        if (msg.getType() == Message.Type.REQUEST_TASK
                || msg.getType() == Message.Type.DONE
                || msg.getType() == Message.Type.PARTIAL_DONE
                || msg.getType() == Message.Type.DRONE_STATUS) {
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

    public Event requestTask(String droneId) throws InterruptedException {
        if (isCommunicationFailed(droneId)) {
            Thread.sleep(300);
            return null;
        }
        try {
            sendMessage(new Message(Message.Type.REQUEST_TASK, null, droneId));
        } catch (Exception e) {
            e.printStackTrace();
        }
        synchronized (taskLock) {
            Queue<Event> queue = taskQueues.computeIfAbsent(droneId, k -> new LinkedList<>());
            while (queue.isEmpty() && !isCommunicationFailed(droneId)) {
                taskLock.wait(1000);
                if (queue.isEmpty()) {
                    return null;
                }
            }
            return queue.poll();
        }
    }

    public void reportDone(Event event, String droneId) throws Exception {
        sendMessage(new Message(Message.Type.DONE, event, droneId));
    }

    public void reportPartialDone(Event event, String droneId) throws Exception {
        sendMessage(new Message(Message.Type.PARTIAL_DONE, event, droneId));
    }

    public void sendStatus(String droneId, float battery, int zoneId, float water, int targetZoneId, long lastAnimDurationMs, long animStartTime, String state) throws Exception {
        if (isCommunicationFailed(droneId)) {
            state = "commFailure";
        }
        String note = droneId + "," + battery + "," + zoneId + "," + water + "," + targetZoneId + "," + lastAnimDurationMs + "," + animStartTime + "," + state;
        sendMessage(new Message(Message.Type.DRONE_STATUS, null, note));
    }

    public void reportFault(String droneId, String faultReason) throws Exception {
        sendMessage(Message.commFailure(droneId, faultReason));
    }

    public boolean isCommunicationFailed(String droneId) {
        synchronized (taskLock) {
            return communicationFailedDrones.contains(droneId);
        }
    }

    private String extractDroneIdFromStatus(String note) {
        if (note == null || note.isEmpty()) {
            return null;
        }
        return note.split(",")[0];
    }

    private void requestResend(String droneId, String failedMessageId) {
        try {
            sendMessage(Message.resendRequest(droneId, failedMessageId));
            if (droneId != null) {
                pendingResends.put(droneId, new PendingResend(failedMessageId, System.currentTimeMillis()));
            }
        } catch (Exception e) {
            markCommunicationFailed(droneId, "failed_to_request_resend");
        }
    }

    private void handleCorruptedMessage(Message msg) {
        String droneId = null;
        if (msg.getType() == Message.Type.DISPATCH || msg.getType() == Message.Type.NO_TASK || msg.getType() == Message.Type.RESEND_REQUEST) {
            droneId = msg.getNote();
        }
        if (droneId == null || droneId.isEmpty()) {
            droneId = "Drone-0";
        }

        Integer count = resendCounts.getOrDefault(msg.getMessageId(), 0);
        if (count >= MAX_RESEND_ATTEMPTS) {
            markCommunicationFailed(droneId, "corrupted_resend");
            return;
        }

        resendCounts.put(msg.getMessageId(), count + 1);
        requestResend(droneId, msg.getMessageId());
    }

    private void handleResendRequest(Message msg) {
        String requestedDrone = msg.getNote();
        String correlationId = msg.getCorrelationId();
        Message original = outboundMessages.get(correlationId);
        if (original == null || requestedDrone == null || requestedDrone.isEmpty()) {
            markCommunicationFailed(requestedDrone, "resend_message_missing");
            return;
        }

        String sourceDrone = extractDroneId(original);
        if (!requestedDrone.equals(sourceDrone)) {
            return;
        }

        if (original.getAttempt() > MAX_RESEND_ATTEMPTS + 1) {
            markCommunicationFailed(requestedDrone, "resend_limit");
            return;
        }

        try {
            sendMessage(original.asResendAttempt());
        } catch (Exception e) {
            markCommunicationFailed(requestedDrone, "resend_send_failed");
        }
    }

    private void detectSequenceGaps(Message msg) {
        long inboundSeq = msg.getSequenceNumber();
        Message.Type type = msg.getType();

        // All messages from Scheduler to DroneSubsystem share one sequence stream.
        // Advance baseline for all of them; only fault on task-dispatch control gaps.
        if (type == Message.Type.DISPATCH
                || type == Message.Type.NO_TASK
                || type == Message.Type.COMM_FAILURE
                || type == Message.Type.RESEND_REQUEST) {
            if (lastReceivedSequenceFromScheduler >= 0) {
                if (inboundSeq <= lastReceivedSequenceFromScheduler) {
                    // UDP can reorder/duplicate packets.
                    return;
                }
                long gap = inboundSeq - lastReceivedSequenceFromScheduler;
                long expectedSeq = lastReceivedSequenceFromScheduler + 1;
                lastReceivedSequenceFromScheduler = inboundSeq;
                boolean taskControlType = (type == Message.Type.DISPATCH);
                if (taskControlType && gap > 1 && gap <= MAX_SEQUENCE_GAP) {
                    // Scheduler sequence numbers are global for all target drones.
                    // A gap here cannot be safely attributed to the currently addressed drone.
                    // Keep as telemetry only; do not mark a specific drone failed.
                    System.out.println("[DroneSubsystem] Packet loss suspected on scheduler stream"
                            + " (expected seq=" + expectedSeq + ", got seq=" + inboundSeq + ")");
                }
                return;
            }
            lastReceivedSequenceFromScheduler = inboundSeq;
        }
    }

    private String extractDroneId(Message msg) {
        if (msg.getNote() == null || msg.getNote().isEmpty()) {
            return null;
        }
        if (msg.getType() == Message.Type.DRONE_STATUS || msg.getType() == Message.Type.COMM_FAILURE) {
            return msg.getNote().split(",")[0];
        }
        return msg.getNote();
    }

    private void markCommunicationFailed(String droneId, String reason) {
        if (droneId == null || droneId.isEmpty()) {
            return;
        }

        boolean changed;
        synchronized (taskLock) {
            changed = communicationFailedDrones.add(droneId);
            taskLock.notifyAll();
        }
        if (!changed) {
            return;
        }

        System.out.println("[DroneSubsystem] Communication failed for " + droneId + " (" + reason + ")");
        try {
            sendMessage(Message.commFailure(droneId, reason));
            sendStatus(droneId, Drone.BATTERY_SIZE, 0, Drone.TANK_SIZE, 0, 0, 0, "commFailure");
        } catch (Exception e) {
            e.printStackTrace();
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
                markCommunicationFailed(droneId, "resend_timeout");
            }
        }
    }

    @Override
    public void run() {
        System.out.println("[DroneSubsystem] Running on port " + DRONE_PORT);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Message msg = receiveMessage();
                    System.out.println("[DroneSubsystem] Received: " + msg);

                    if (!msg.isCrcValid()) {
                        handleCorruptedMessage(msg);
                        continue;
                    }

                    detectSequenceGaps(msg);

                    String msgDroneId = extractDroneId(msg);
                    PendingResend pending = msgDroneId == null ? null : pendingResends.get(msgDroneId);
                    if (pending != null && msg.getMessageId().equals(pending.expectedMessageId)) {
                        pendingResends.remove(msgDroneId);
                    }

                    if (msg.getType() == Message.Type.DISPATCH) {
                        synchronized (taskLock) {
                            Queue<Event> queue = taskQueues.computeIfAbsent(msg.getNote(), k -> new LinkedList<>());
                            queue.offer(msg.getEvent());
                            taskLock.notifyAll();
                        }
                    } else if (msg.getType() == Message.Type.NO_TASK) {
                        synchronized (taskLock) {
                            taskLock.notifyAll();
                        }
                    } else if (msg.getType() == Message.Type.RESEND_REQUEST) {
                        handleResendRequest(msg);
                    } else if (msg.getType() == Message.Type.COMM_FAILURE) {
                        String failedDrone = extractDroneId(msg);
                        markCommunicationFailed(failedDrone, "scheduler_reported");
                    }
                } catch (SocketTimeoutException e) {
                    checkResendTimeouts();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        socket.close();
    }

    private static class PendingResend {
        final String expectedMessageId;
        final long requestedAtMs;

        PendingResend(String expectedMessageId, long requestedAtMs) {
            this.expectedMessageId = expectedMessageId;
            this.requestedAtMs = requestedAtMs;
        }
    }

    public static void main(String[] args) throws Exception {
        DroneSubsystem subsystem = new DroneSubsystem();
        new Thread(subsystem).start();
    }
}