import java.net.*;
import java.io.*;
import java.util.*;

public class Scheduler implements Runnable {
    private DatagramSocket socket;
    public static final int simulationSpeed = 10; //1000 for real-time, do 10 or less to quickly test

    private final List<Event> events = new LinkedList<>();
    private final Map<String, DroneStatus> droneStatuses = new HashMap<>();
    private FireIncidentSubsystemGUI gui;
    private int nextDroneIndex = 0;
    private final Queue<String> requestingDrones = new LinkedList<>();

    private static final int SCHEDULER_PORT = 5000;
    private static final int FIRE_PORT = 5001;
    private static final int DRONE_PORT = 5002;
    private static final String HOST = "localhost";

    public Scheduler() throws Exception {
        socket = new DatagramSocket(SCHEDULER_PORT);
    }

    private void sendMessage(Message msg, int port) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(msg);
        out.flush();
        byte[] data = bos.toByteArray();
        DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(HOST), port);
        socket.send(packet);
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
                Message msg = receiveMessage();
                System.out.println("[Scheduler] Received: " + msg);
                handleMessage(msg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        socket.close();
        System.out.println("[Scheduler] Scheduler stopped");
    }

    public void setGui(FireIncidentSubsystemGUI gui) {
        this.gui = gui;
    }

    private void handleMessage(Message msg) throws Exception {
        switch (msg.getType()) {
            case EVENT:
                events.add(msg.getEvent());
                sendMessage(new Message(Message.Type.ACK, null, "Event received"), FIRE_PORT);
                if (gui != null) gui.addEvent(msg.getEvent());
                assignTasks();
                break;
            case REQUEST_TASK:
                requestingDrones.add(msg.getNote());
                assignTasks();
                break;
            case DONE:
                // Assume DONE has event, send ACK to fire
                sendMessage(new Message(Message.Type.ACK, null, "Done: " + msg.getEvent()), FIRE_PORT);
                if (gui != null) gui.updateEvent(msg.getEvent());
                break;
            case PARTIAL_DONE:
                // Assume PARTIAL_DONE has event, send ACK to fire
                sendMessage(new Message(Message.Type.ACK, null, "Partial Done: " + msg.getEvent()), FIRE_PORT);
                if (gui != null) gui.updateEvent(msg.getEvent());
                break;
            case DRONE_STATUS:
                // note: "droneId,battery,zoneId,water,targetZoneId,lastAnim,animStart"
                String[] parts = msg.getNote().split(",");
                String id = parts[0];
                float battery = Float.parseFloat(parts[1]);
                int zone = Integer.parseInt(parts[2]);
                float water = Float.parseFloat(parts[3]);
                droneStatuses.put(id, new DroneStatus(battery, zone, water));
                if (gui != null) gui.updateDrone(msg.getNote());
                break;
            default:
                System.out.println("[Scheduler] Unknown message type: " + msg.getType());
        }
    }

    private void assignTasks() throws Exception {
        while (!events.isEmpty() && !requestingDrones.isEmpty()) {
            String droneId = requestingDrones.poll();
            Event event = events.remove(0);
            sendMessage(new Message(Message.Type.DISPATCH, event, droneId), DRONE_PORT);
        }
    }

    private static class DroneStatus {
        float battery;
        int zoneId;
        float water;
        DroneStatus(float b, int z, float w) { battery = b; zoneId = z; water = w; }
    }
}
