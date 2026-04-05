import java.io.*;
import java.net.*;
import java.util.*;

public class FireIncidentSubsystem implements Runnable {
    private DatagramSocket socket;
    private final List<Event> events;
    private final String eventpath;
    private final String threadName;
    private volatile boolean simulationEnded = false;

    private static final int SCHEDULER_PORT = 5000;
    private static final int FIRE_PORT = 5001;
    private static final String HOST = "localhost";

    //constr for fireincidentsubsystem
    public FireIncidentSubsystem(String eventpath) throws Exception {
        this.eventpath = eventpath;
        this.threadName = "FireIncidentSubsystem";
        this.events = new ArrayList<>();
        // socket created in run()
    }

    //load events
    public void loadEvents() {
        System.out.println("[" + threadName + "] Loading events from: " + eventpath);
        try (BufferedReader reader = new BufferedReader(new FileReader(eventpath))) {
            String line;
            boolean isFirstLine = true;
            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                parseEventLine(line);
            }
            System.out.println("[" + threadName + "] Loaded " + events.size() + " events");
            for (Event event : events) {
                System.out.println("[" + threadName + "] " + event);
            }
        } catch (IOException e) {
            System.err.println("[" + threadName + "] Error reading event file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void parseEventLine(String line) {
        try {
            String[] parts = line.trim().split("[,\\s]+");
            if (parts.length < 4) {
                System.err.println("[" + threadName + "] invalid event line format: " + line);
                return;
            }
            String time = parts[0].trim();
            int zoneId = Integer.parseInt(parts[1].trim());
            Event.EventType eventType = Event.parseEventType(parts[2].trim());
            Event.Severity severity = Event.parseSeverity(parts[3].trim());
            Event.FaultType faultType = parts.length > 4 ? Event.parseFaultType(parts[4].trim()) : Event.FaultType.NONE;
            Event event = new Event(time, zoneId, eventType, severity, faultType);
            events.add(event);
        } catch (Exception e) {
            System.err.println("[" + threadName + "] error parsing event line '" + line + "': " + e.getMessage());
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

    public List<Event> getEvents() {
        return events;
    }

    //parse timestamp string "HH:mm:ss"
    public long parseTimeToMillis(String time){
        try{
            String[] parts = time.split("[:.]");
            int hours = Integer.parseInt(parts[0]);
            int min = Integer.parseInt(parts[1]);
            int sec = Integer.parseInt(parts[2]);
            int millis = 0;
            if (parts.length > 3){
                millis = Integer.parseInt(parts[3]);
            }
            return ((hours * 3600L) + (min * 60L) + sec) + (millis/1000);
        } catch(Exception e){
            System.err.println("[" + threadName + "] error parsing time: " + time);
            return 0;
        }
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSockets(FIRE_PORT);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        loadEvents();
        try {
            sendMessage(new Message(Message.Type.START, null, ""));
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (events.isEmpty()) {
            System.out.println("[" + threadName + "] No events to send.");
            return;
        }
        //send events spaced out by timestamps
        long previousTimeMills = parseTimeToMillis(events.get(0).getTime());

        try {
            Thread.sleep(2000); //wait a while before starting simulation
        } catch (InterruptedException e) {
            System.out.println("[" + threadName + "] interrupted while waiting (prelim");
        }

        //send all events to the scheduler
        for (Event event : events) {
            long currentTimeMills = parseTimeToMillis(event.getTime());
            long delay = currentTimeMills - previousTimeMills;
            //wait for the delay
            if(delay > 0){
                try{
                    System.out.println("[" + threadName + "] waiting " + delay + "s until next event...");
                    Thread.sleep(delay* Scheduler.simulationSpeed);
                } catch ( InterruptedException e){
                    System.out.println("[" + threadName + "] interrupted while waiting");
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            event.deliverEvent(Event.State.PENDING);
            System.out.println("[" + threadName + "] Sending event: " + event);
            try {
                sendMessage(new Message(Message.Type.EVENT, event, ""));
                Message ack = receiveMessage();
                if (ack.getType() == Message.Type.END_SIMULATION) {
                    System.out.println("[FireIncidentSubsystem] Stop signal received from scheduler");
                    simulationEnded = true;
                    break;
                }
                while (ack.getType() == Message.Type.ACK
                        && (ack.getNote().startsWith("Done") || ack.getNote().startsWith("Partial"))) {
                    System.out.println("[FireIncidentSubsystem] Completion received early: " + ack);
                    ack = receiveMessage();
                    if (ack.getType() == Message.Type.END_SIMULATION) {
                        System.out.println("[FireIncidentSubsystem] Stop signal received from scheduler");
                        simulationEnded = true;
                        break;
                    }
                }
                if (simulationEnded) {
                    break;
                }
                System.out.println("[FireIncidentSubsystem] ACK received: " + ack);
            } catch (Exception e) {
                e.printStackTrace();
            }
            previousTimeMills = currentTimeMills;
        }
        try {
            sendMessage(new Message(Message.Type.END_SIMULATION, null, ""));
        } catch (Exception e) {
            e.printStackTrace();
        }
        //receive ALL confirmations
        for (int i = 0; i < events.size(); i++) {
            try {
                Message confirmation = receiveMessage();
                if (confirmation.getType() == Message.Type.END_SIMULATION) {
                    System.out.println("[FireIncidentSubsystem] Stop signal received from scheduler");
                    break;
                }
                System.out.println("[FireIncidentSubsystem] Confirmation received: " + confirmation);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        socket.close();
    }

    public static void main(String[] args) throws Exception {
        String eventPath = "SYSC3303-Project-Group5/Sample_event_file.csv";
        FireIncidentSubsystem fire = new FireIncidentSubsystem(eventPath);
        new Thread(fire).start();
    }
}