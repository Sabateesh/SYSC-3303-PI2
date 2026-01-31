import java.util.ArrayList;
import java.util.List;

public class DroneSubsystem {

    private Scheduler scheduler;
    private List<Drone> drones;
    private boolean workToDo;

    public DroneSubsystem(Scheduler scheduler){
        drones = new ArrayList<>();
        workToDo = false;
        scheduler = scheduler;
    }

    public synchronized boolean checkForWork(){
        return scheduler.isEmpty();
    }
}
