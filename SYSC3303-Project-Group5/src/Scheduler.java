import java.net.*;
import java.io.*;
import java.util.*;

public class Scheduler implements Runnable {
    private DatagramSocket socket;
    public static final int simulationSpeed = 10; //1000 for real-time, do 10 or less to quickly test

    private final List<Event> events = new LinkedList<>();
    private final Map<String, DroneStatus> droneStatuses = new HashMap<>();
    private SchedulerGUI gui;
    private int nextDroneIndex = 0;
    private final Queue<String> requestingDrones = new LinkedList<>();
    private final Map<String, Integer> tasksAssigned = new HashMap<>();
    private static final int SCHEDULER_PORT = 5000;
    private static final int FIRE_PORT = 5001;
    private static final int DRONE_PORT = 5002;
    private static final String HOST = "localhost";

    private boolean simulationStarted = false;
    private boolean endReceived = false;

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

    public void setGui(SchedulerGUI gui) {
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
                events.add(msg.getEvent());
                sendMessage(new Message(Message.Type.ACK, null, "Partial Done: " + msg.getEvent()), FIRE_PORT);
                if (gui != null) gui.updateEvent(msg.getEvent());
                assignTasks();
                break;
            case DRONE_STATUS:
                // note: "droneId,battery,zoneId,water,targetZoneId,lastAnim,animStart,state"
                String[] parts = msg.getNote().split(",");
                String id = parts[0];
                float battery = Float.parseFloat(parts[1]);
                int zone = Integer.parseInt(parts[2]);
                float water = Float.parseFloat(parts[3]);
                int target = Integer.parseInt(parts[4]);
                long lastAnim = Long.parseLong(parts[5]);
                long animStart = Long.parseLong(parts[6]);
                String state = parts[7];
                DroneStatus ds = new DroneStatus(id, battery, zone, water, target, lastAnim, animStart, state);
                droneStatuses.put(id, ds);
                if (gui != null) gui.updateDrone(ds);
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
            requestingDrones.remove(bestDrone);
            tasksAssigned.merge(bestDrone, 1, Integer::sum);
            System.out.println("[Scheduler] Dispatching " + bestDrone + " to zone " + event.getZoneID()
                    + " (tasks assigned: " + tasksAssigned.get(bestDrone) + ")");
            sendMessage(new Message(Message.Type.DISPATCH, event, bestDrone), DRONE_PORT);
        }
    }

    private String selectBestDrone(Event event) {
        String best = null;
        float bestScore = Float.MAX_VALUE;
        List<Zone> zones = Zone.loadFromCSV("SYSC3303-Project-Group5/sample_zone_file.csv");
        for (String droneId : requestingDrones) {
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
