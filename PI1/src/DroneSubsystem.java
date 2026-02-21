//import java.net.DatagramPacket;
//import java.net.DatagramSocket;
//import java.net.InetSocketAddress;
//import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class DroneSubsystem implements Runnable {

    private final SchedulerServer scheduler;
    private final static int NUM_DRONES = 1;
    private final List<Thread> drones;
    private final List<Zone> zones;
    private final Queue<Event> fromFire;
    private volatile boolean running;
//    private final String serverIP;
//    private final int serverPort;

    public DroneSubsystem(SchedulerServer scheduler, List<Zone> zones) {
        this.scheduler = scheduler;
        this.fromFire = new LinkedList<>();
        this.drones = new ArrayList<>();
        this.zones = zones;
        this.running = true;
//        this.serverIP = serverIP;
//        this.serverPort = serverPort;
        this.initializeDrones();
    }

    public void initializeDrones() {
        for(int i=0; i<NUM_DRONES; i++) {
            String droneName = "Drone-"+i;
            Drone drone = new Drone(this, zones, droneName);
            Thread droneThread =
                    new Thread(drone, droneName);
            drones.add(droneThread);
            droneThread.start();
        }
    }

//    private void sendRequest(DatagramSocket socket, String msg) throws Exception {
//        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
//        DatagramPacket packet = new DatagramPacket(
//                data, data.length, new InetSocketAddress(hostIP, hostPort)
//        );
//        socket.send(packet);
//        System.out.println("Sent: " + msg + "\n");
//    }

//    private String receiveResponse(DatagramSocket socket) throws Exception {
//        byte[] buf = new byte[4096];
//        DatagramPacket packet = new DatagramPacket(buf, buf.length);
//        socket.receive(packet);
//        return new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
//    }

    public Event requestTask() throws InterruptedException {
        synchronized (fromFire) {
            while (fromFire.isEmpty()) {
                fromFire.wait();
            }
            return fromFire.poll();
        }
    }

    public void reportDone(Event e) {
        if (e == null) return;
        scheduler.reportDone(e);
    }

    @Override
    public void run() {
        System.out.println("[DroneSubsystem] Drone subsystem started");

        while (running) {
            try {
                Event task = scheduler.requestTask(); // blocks until work
                synchronized (fromFire) {
                    fromFire.offer(task);
                    fromFire.notifyAll();
                }
            } catch (InterruptedException e) {
                System.out.println("[DroneSubsystem] Interrupted, shutting down");
                running = false;
                Thread.currentThread().interrupt();
            }
        }

        for (Thread drone : drones) {
            drone.interrupt();
        }
        System.out.println("[DroneSubsystem] Drone subsystem ended");
    }
}
