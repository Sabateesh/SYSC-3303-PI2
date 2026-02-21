import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FireIncidentSubsystem implements Runnable {
    private final Scheduler scheduler;
    private final List<Event> events;
    private final String eventpath;
    private final String threadName;

    //constr for fireincidentsubsystem
    public FireIncidentSubsystem(String eventpath, Scheduler scheduler) {
        this.eventpath = eventpath;
        this.scheduler = scheduler;
        this.threadName = "FireIncidentSubsystem";
        this.events = new ArrayList<>();

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
    public List<Event> getEvents() {
        return events;
    }

    @Override
    public void run() {
        //load events
        if (events.isEmpty()) {
            loadEvents();
        }
        if (events.isEmpty()) {
            System.out.println("[FireIncidentSubsystem] No events to send.");
            return;
        }
        //send all events to the scheduler
        for (Event event : events) {
            scheduler.sendEvent(event);
        }
        //receive ALL confirmations
        for (int i = 0; i < events.size(); i++) {
            String confirmation = scheduler.getConfirmation();
            System.out.println("[FireIncidentSubsystem] Confirmation received: " + confirmation);
        }
    }
}
