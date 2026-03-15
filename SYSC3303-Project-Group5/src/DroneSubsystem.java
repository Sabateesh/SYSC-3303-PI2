import java.net.*;
import java.io.*;
import java.util.*;

public class DroneSubsystem implements Runnable {

    private DatagramSocket socket;
    private final List<Zone> zones;
    public final static int NUM_DRONES = 3;
    private final List<Thread> droneThreads;
    private final Queue<Event> taskQueue = new LinkedList<>();
    private final Object taskLock = new Object();

    private static final int SCHEDULER_PORT = 5000;
    private static final int DRONE_PORT = 5002;
    private static final String HOST = "localhost";

    public DroneSubsystem() throws Exception {
        socket = new DatagramSocket(DRONE_PORT);
        zones = Zone.loadFromCSV("SYSC3303-Project-Group5/sample_zone_file.csv");
        droneThreads = new ArrayList<>();
        initializeDrones();
    }

    private void initializeDrones() {
        for(int i = 0; i < NUM_DRONES; i++) {
            String droneName = "Drone-" + i;
            Drone drone = new Drone(this, droneName, zones);
            Thread droneThread = new Thread(drone, droneName);
            droneThreads.add(droneThread);
            droneThread.start();
        }
    }

    private void sendMessage(Message msg) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(msg);
        out.flush();
        byte[] data = bos.toByteArray();
        DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(HOST), SCHEDULER_PORT);
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

    public Event requestTask(String droneId) throws InterruptedException {
        try {
            sendMessage(new Message(Message.Type.REQUEST_TASK, null, droneId));
        } catch (Exception e) {
            e.printStackTrace();
        }
        synchronized (taskLock) {
            while (taskQueue.isEmpty()) {
                taskLock.wait();
            }
            return taskQueue.poll();
        }
    }

    public void reportDone(Event event, String droneId) throws Exception {
        sendMessage(new Message(Message.Type.DONE, event, droneId));
    }

    public void sendStatus(String droneId, float battery, int zoneId, float water) throws Exception {
        String note = droneId + "," + battery + "," + zoneId + "," + water;
        sendMessage(new Message(Message.Type.DRONE_STATUS, null, note));
    }

    @Override
    public void run() {
        System.out.println("[DroneSubsystem] Running on port " + DRONE_PORT);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Message msg = receiveMessage();
                System.out.println("[DroneSubsystem] Received: " + msg);
                if (msg.getType() == Message.Type.DISPATCH) {
                    synchronized (taskLock) {
                        taskQueue.offer(msg.getEvent());
                        taskLock.notifyAll();
                    }
                } else if (msg.getType() == Message.Type.NO_TASK) {
                    // Perhaps wait and retry, but for now, ignore
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        socket.close();
    }

    public static void main(String[] args) throws Exception {
        DroneSubsystem subsystem = new DroneSubsystem();
        new Thread(subsystem).start();
    }
}