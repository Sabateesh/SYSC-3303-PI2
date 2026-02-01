import java.util.Queue;

class Scheduler extends SchedulerServer {
    public Scheduler(Queue<Event> fromFire, Queue<String> toFire) {
        super(fromFire, toFire);
    }

    @Override
    public String getConfirmation(){
        try{
            return super.getConfirmation();
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            return "Interrupted while waiting for confirmation";
        }
    }
}