public class Drone implements Runnable {

    private final Event event;
    private final SchedulerServer scheduler;

    public Drone(Event event, SchedulerServer scheduler) {
        this.event = event;
        this.scheduler = scheduler;
    }

    @Override
    public void run() {
        System.out.println("[Drone] Servicing fire at zone " + event.getZoneID());

        try {
            Thread.sleep(2000); //place holder for drone time calculation later
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        scheduler.reportDone(event);
        System.out.println("[Drone] Completed fire at zone " + event.getZoneID());
    }
}
