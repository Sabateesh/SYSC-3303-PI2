import java.util.LinkedList;
import java.util.Queue;
public class FireIncidentSubsystemTest {
    public static void main(String[] args) {
        System.out.println("Fire Incident Subsystem Test - PI1");
        Scheduler scheduler = new Scheduler();
        //file path
        String eventpath = "PI1/Sample_event_file.csv";
        String zonepath = "Sample_zone_file.csv";
        //create fire incident subsystem
        FireIncidentSubsystem fireSubsystem = new FireIncidentSubsystem(
                eventpath,
                scheduler
        );
        //create thread
        Thread fireThread = new Thread(fireSubsystem, "FireIncidentSubsystem");
        System.out.println("[Main] Starting Fire Incident Subsystem thread\n");
        fireThread.start();
        //simulate receiving conf from scedualer for testing
        Thread simulatorThread= new Thread(() -> {
            try{
                Thread.sleep(2000);
                synchronized (confirmationQueue){
                    confirmationQueue.offer("Zone 3: Fire extinguished - HIGH severity handled");
                    confirmationQueue.notifyAll();
                }
                Thread.sleep(2000);
                synchronized (confirmationQueue) {
                    confirmationQueue.offer("Zone 7: Drone request completed - MODERATE severity handled");
                    confirmationQueue.notifyAll();
                }
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
            }
        },"SchedulerSimulator");
        simulatorThread.start();

        //monitor event queue to show what's sent
        Thread monitorThread= new Thread(() -> {
            try {
                Thread.sleep(500);
                System.out.println("[Monitor] Checking event queue");
                while (fireThread.isAlive()) {
                    synchronized (eventQueue) {
                        if (!eventQueue.isEmpty()) {
                            Event event = eventQueue.poll();
                            System.out.println("[Monitor] Scheduler received: " + event);
                        }
                    }
                    Thread.sleep(100);
                }
                synchronized (eventQueue) {
                    while (!eventQueue.isEmpty()) {
                        Event event = eventQueue.poll();
                        System.out.println("[Monitor] Scheduler received: " + event);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "EventQueueMonitor");
        monitorThread.start();
        try{
            fireThread.join();
            simulatorThread.join();
            monitorThread.join();
            System.out.println("Fire Incident Subsystem Test Complete");

        } catch (InterruptedException e){
            System.err.println("Main thread interrupted");
            e.printStackTrace();
        }

    }
}
