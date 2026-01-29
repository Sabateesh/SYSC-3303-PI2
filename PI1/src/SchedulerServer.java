import java.io.*;
import java.net.*;
import java.util.ArrayDeque;
import java.util.Queue;

public class SchedulerServer {

    private static final int PORT = 5000;
    private final Queue<Event> eventQueue = new ArrayDeque<>();
    private volatile ObjectOutputStream fireOut = null;

    public static void main(String[] args) {
        new SchedulerServer().start();
    }

    public void start() {
        System.out.println("[Scheduler] Starting on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket client = serverSocket.accept();
                new Thread(() -> handleClient(client)).start();
            }
        } catch (IOException e) {
            System.err.println("[Scheduler] Server error: " + e.getMessage());
        }
    }

    private void handleClient(Socket socket) {
        String who = socket.getRemoteSocketAddress().toString();
        System.out.println("[Scheduler] Client connected: " + who);

        try (
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            while (true) {
                Object obj = in.readObject();
                if (!(obj instanceof Message msg)) {
                    System.out.println("[Scheduler] Ignored unknown object from " + who);
                    continue;
                }

                switch (msg.getType()) {
                    case EVENT -> handleIncomingEvent(msg, out);
                    case REQUEST_TASK -> handleDroneRequest(out);
                    case DONE -> handleDroneDone(msg);
                    default -> System.out.println("[Scheduler] Unknown msg: " + msg);
                }
            }
        } catch (EOFException eof) {
            System.out.println("[Scheduler] Client disconnected: " + who);
        } catch (Exception e) {
            System.err.println("[Scheduler] Client error (" + who + "): " + e.getMessage());
        }
    }

    private synchronized void handleIncomingEvent(Message msg, ObjectOutputStream out) throws IOException {
        Event e = msg.getEvent();
        if (e == null) return;

        fireOut = out;

        eventQueue.add(e);
        System.out.println("[Scheduler] EVENT queued: " + e);

        out.writeObject(new Message(Message.Type.ACK, e, "Scheduler received event"));
        out.flush();
    }

    private synchronized void handleDroneRequest(ObjectOutputStream droneOut) throws IOException {
        Event next = eventQueue.poll();

        if (next == null) {
            droneOut.writeObject(new Message(Message.Type.NO_TASK, null, "No tasks available"));
            droneOut.flush();
            System.out.println("[Scheduler] Drone requested task -> NO_TASK");
            return;
        }

        droneOut.writeObject(new Message(Message.Type.DISPATCH, next, "Dispatching event"));
        droneOut.flush();
        System.out.println("[Scheduler] Dispatched to drone: " + next);
    }

    private synchronized void handleDroneDone(Message msg) throws IOException {
        Event doneEvent = msg.getEvent();
        System.out.println("[Scheduler] DONE received: " + doneEvent);

        if (fireOut != null && doneEvent != null) {
            fireOut.writeObject(new Message(Message.Type.ACK, doneEvent, "Fire serviced"));
            fireOut.flush();
            System.out.println("[Scheduler] Forwarded ACK to FireIncident");
        }
    }
}
