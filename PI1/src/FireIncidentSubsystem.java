import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class FireIncidentSubsystem implements Runnable{
    private final Queue<Event> outgoing;
    private final Queue<String> incoming;

    private final List<Event> events;
    private final String eventpath;
    private final String threadName;

    //constr for fireincidentsubsystem
    public FireIncidentSubsystem(String eventpath,
                                 Queue<Event> outgoing,
                                 Queue<String> incoming) {
        this.eventpath = eventpath;
        this.outgoing = outgoing;
        this.incoming = incoming;
        this.events = new ArrayList<>();
        this.threadName = "FireIncidentSubsystem";
    }
    //load events
    public void loadEvents(){
        System.out.println("[" + threadName + "] Loading events from: " + eventpath);
        try(BufferedReader reader = new BufferedReader(new FileReader(eventpath))){
            String line;
            boolean isFirstLine = true;
            while((line = reader.readLine()) != null){
                if(isFirstLine){
                    isFirstLine = false;
                    continue;
                }
                line=line.trim();
                if(line.isEmpty()){
                    continue;
                }
                parseEventLine(line);
            }
            System.out.println("[" + threadName + "] Loaded " + events.size() + " events");
            for(Event event : events){
                System.out.println("[" + threadName + "] " + event);
            }
            }catch(IOException e) {
                System.err.println("[" + threadName + "] Error reading event file: " + e.getMessage());
                e.printStackTrace();
            }
        }
        private void parseEventLine(String line){
            try {
                String[] parts = line.split(",");
                if(parts.length < 4){
                    System.err.println("[" + threadName + "] invlid event line format: " + line);
                    return;
                }
                String time = parts[0].trim();
                int zoneId = Integer.parseInt(parts[1].trim());
                Event.EventType eventType = Event.parseEventType(parts[2].trim());
                Event.Severity severity = Event.parseSeverity(parts[3].trim());
                Event event = new Event(time, zoneId, eventType, severity);
                events.add(event);
            } catch (Exception e) {
                System.err.println("[" + threadName + "] error parsing event line '" + line + "': " + e.getMessage());
            }
        }
        private void sendEventToScheduler(Event event) {
            synchronized (outgoing) {
                outgoing.offer(event);
                outgoing.notifyAll();
            }
            System.out.println("[" + threadName + "] Sent to Scheduler: " + event);
        }
        private void checkConfirmations() {
            synchronized (incoming) {
                while (!incoming.isEmpty()) {
                    String message = incoming.poll();
                    System.out.println("[" + threadName + "] Received confirmation: " + message);
                }
            }
        }
        @Override
    public void run(){
        System.out.println("[" + threadName + "] Starting Fire Incident Subsystem");
        loadEvents();
        System.out.println("[" + threadName + "] Beginning event processing");
        for(Event event:events){
            sendEventToScheduler(event);
            try{
                Thread.sleep(1000);
            }catch(InterruptedException e){
                System.err.println("[" + threadName + "] Thread interrupted");
                Thread.currentThread().interrupt();
                break;
            }
            checkConfirmations();
        }
        System.out.println("[" + threadName + "] All events sent. Waiting for final confirmations");
        for (int i = 0; i < 5; i++) {
            checkConfirmations();
            try {
                Thread.sleep(500);
            }catch(InterruptedException e) {
                System.err.println("[" + threadName + "] Thread interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("[" + threadName + "] Fire Incident Subsystem terminating");
    }
}