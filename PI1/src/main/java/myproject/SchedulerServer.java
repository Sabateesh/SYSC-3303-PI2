package myproject;

import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.Queue;

public class SchedulerServer {

    private static final int PORT = 5000;
    private final Queue<FireEvent> eventQueue = new ArrayDeque<>();
    private volatile ClientConnection fireClient = null;

    public static void main(String[] args) {
        new SchedulerServer().run();
    }

    public void run() {
        System.out.println("[Scheduler] Server starting on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handleClient(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("[Scheduler] Fatal server error: " + e.getMessage());
        }
    }

    private void handleClient(Socket socket) {
        String remote = socket.getRemoteSocketAddress().toString();
        System.out.println("[Scheduler] Client connected: " + remote);

        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

            ClientConnection conn = new ClientConnection(socket, out);

            while (true) {
                Object obj = in.readObject();
                if (!(obj instanceof Message msg)) {
                    System.out.println("[Scheduler] Ignored unknown object from " + remote);
                    continue;
                }

                switch (msg.type) {
                    case EVENT -> handleEvent(msg, conn);
                    case REQUEST_TASK -> handleRequestTask(conn);
                    case DONE -> handleDone(msg);
                    default -> System.out.println("[Scheduler] Unknown message type from " + remote);
                }
            }

        } catch (EOFException eof) {
            System.out.println("[Scheduler] Client disconnected: " + remote);
        } catch (Exception e) {
            System.err.println("[Scheduler] Client error (" + remote + "): " + e.getMessage());
        }
    }

    private synchronized void handleEvent(Message msg, ClientConnection conn) throws IOException {
        if (msg.event == null) {
            System.out.println("[Scheduler] EVENT received with null payload (ignored)");
            return;
        }

        fireClient = conn;

        eventQueue.add(msg.event);
        System.out.println("[Scheduler] EVENT received -> queued: " + msg.event);

        conn.send(new Message(MessageType.ACK, msg.event, "Scheduler received event"));
    }

    private synchronized void handleRequestTask(ClientConnection droneConn) throws IOException {
        FireEvent next = eventQueue.poll();

        if (next == null) {
            droneConn.send(new Message(MessageType.ACK, null, "NO_TASK"));
            System.out.println("[Scheduler] Drone requested task -> none available");
            return;
        }

        droneConn.send(new Message(MessageType.EVENT, next, "DISPATCH"));
        System.out.println("[Scheduler] Dispatched task to drone: " + next);
    }

    private synchronized void handleDone(Message msg) throws IOException {
        System.out.println("[Scheduler] DONE received from drone: " + msg.event);

        if (fireClient != null) {
            fireClient.send(new Message(MessageType.ACK, msg.event, "FIRE_EXTINGUISHED"));
            System.out.println("[Scheduler] Forwarded completion ACK to FireIncident");
        } else {
            System.out.println("[Scheduler] No FireIncident connected to forward ACK");
        }
    }


    private static class ClientConnection {
        private final Socket socket;
        private final ObjectOutputStream out;

        ClientConnection(Socket socket, ObjectOutputStream out) {
            this.socket = socket;
            this.out = out;
        }

        synchronized void send(Message msg) throws IOException {
            out.writeObject(msg);
            out.flush();
        }
    }

    public enum MessageType { EVENT, REQUEST_TASK, DONE, ACK }

    public static class Message implements Serializable {
        public final MessageType type;
        public final FireEvent event;
        public final String note;

        public Message(MessageType type, FireEvent event, String note) {
            this.type = type;
            this.event = event;
            this.note = note;
        }
    }

    public enum Severity { LOW, MODERATE, HIGH }
    public enum EventType { FIRE_DETECTED, DRONE_REQUEST }

    public static class FireEvent implements Serializable {
        public final LocalTime time;
        public final int zoneId;
        public final EventType eventType;
        public final Severity severity;

        public FireEvent(LocalTime time, int zoneId, EventType eventType, Severity severity) {
            this.time = time;
            this.zoneId = zoneId;
            this.eventType = eventType;
            this.severity = severity;
        }

        @Override
        public String toString() {
            return time + " zone=" + zoneId + " type=" + eventType + " severity=" + severity;
        }
    }
}
