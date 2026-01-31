import java.util.Queue;

class Scheduler extends SchedulerServer {
    public Scheduler(Queue<Event> fromFire, Queue<String> toFire) {
        super(fromFire, toFire);
    }
}