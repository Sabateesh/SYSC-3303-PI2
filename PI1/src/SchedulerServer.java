import java.util.Queue;

public class SchedulerServer implements Runnable {
    public static final int simulationSpeed = 1000; //1000 for real-time, do 10 or less to quickly test

    private final Queue<Event> fromFire;
    private final Queue<String> toFire;

    public SchedulerServer(Queue<Event> fromFire, Queue<String> toFire) {
        this.fromFire = fromFire;
        this.toFire = toFire;

    }

    public boolean isEmpty() {
        synchronized (fromFire) {
            return fromFire.isEmpty();
        }
    }

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
        synchronized (toFire) {
            toFire.offer("Fire serviced: zone=" + e.getZoneID() + ", severity=" + e.getSeverity());
            toFire.notifyAll();
        }
    }
    public void sendEvent(Event e){
        if (e == null) return;
        synchronized (fromFire) {
            fromFire.offer(e);
            fromFire.notifyAll();
        }
    }
    public String getConfirmation() throws InterruptedException {
        synchronized (toFire) {
            while (toFire.isEmpty()) {
                toFire.wait();
            }
            return toFire.poll();
        }
    }
    @Override
    public void run() {
        System.out.println("[Scheduler] Scheduler thread started (PI1 queue mode)");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("[Scheduler] Scheduler stopped");
    }
}

