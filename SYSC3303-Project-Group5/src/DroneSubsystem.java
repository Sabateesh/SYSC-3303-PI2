import java.net.*;
import java.io.*;
import java.util.*;

public class DroneSubsystem implements Runnable {

    private final SchedulerServer scheduler;
    private final FireIncidentSubsystemGUI gui;
    public final static int NUM_DRONES = 3;
    private final List<Thread> drones;
    private final List<Zone> zones;
    private final Queue<Event> fromFire;
    private volatile boolean running;

    private int activeDrones = 0;
    private final Object completionLock = new Object();

    private DatagramSocket socket;
    private InetAddress schedulerAddress;
    private int schedulerPort;

    public DroneSubsystem(String hostIP, int hostPort) throws Exception {

        schedulerAddress = InetAddress.getByName(hostIP);
        schedulerPort = hostPort;

        socket = new DatagramSocket();

        initializeDrones();
    }

    private void initializeDrones() {

        for(int i = 0; i < NUM_DRONES; i++) {

            String droneName = "Drone-" + i;

            Drone drone = new Drone(this, droneName);

            Thread droneThread = new Thread(drone);

            droneThreads.add(droneThread);

            droneThread.start();
        }
    }

    public Event requestTask() throws InterruptedException {

        synchronized(taskQueue) {

            while(taskQueue.isEmpty())
                taskQueue.wait();

            synchronized(completionLock) {
                activeDrones++;
            }

            return taskQueue.poll();
        }
    }

    public void reportDone(Event event) {

        try {

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);

            out.writeObject(event);
            out.flush();

            byte[] data = bos.toByteArray();

            DatagramPacket packet =
                    new DatagramPacket(data, data.length,
                            schedulerAddress, schedulerPort);

            socket.send(packet);

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void reportToBase() {

        synchronized(completionLock) {

            activeDrones--;

            if(activeDrones == 0 && taskQueue.isEmpty())
                completionLock.notifyAll();
        }
    }

    @Override
    public void run() {

        System.out.println("[DroneSubsystem] Running");

        byte[] buffer = new byte[4096];

        while(running) {

            try {

                DatagramPacket packet =
                        new DatagramPacket(buffer, buffer.length);

                socket.receive(packet);

                ByteArrayInputStream bis =
                        new ByteArrayInputStream(packet.getData(), 0, packet.getLength());

                ObjectInputStream in = new ObjectInputStream(bis);

                Object obj = in.readObject();

                if(obj instanceof Event) {

                    Event event = (Event) obj;

                    synchronized(taskQueue) {

                        taskQueue.offer(event);
                        taskQueue.notifyAll();
                    }
                }

            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void sendJoin() {

        try {

            String msg = "JOIN:DRONE_SUBSYSTEM";

            byte[] data = msg.getBytes();

            DatagramPacket packet =
                    new DatagramPacket(data, data.length,
                            schedulerAddress, schedulerPort);

            socket.send(packet);

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {

        DroneSubsystem subsystem =
                new DroneSubsystem("127.0.0.1", 5000);

        subsystem.sendJoin();

        new Thread(subsystem).start();
    }
}