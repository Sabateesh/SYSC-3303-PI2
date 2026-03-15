import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import javax.swing.*;
public class Main {
    public static void main(String[] args) throws Exception {
        List<Zone> zones = Zone.loadFromCSV("SYSC3303-Project-Group5/sample_zone_file.csv");
        Scheduler scheduler = new Scheduler();
        new Thread(scheduler).start();
        FireIncidentSubsystemGUI gui = new FireIncidentSubsystemGUI(zones, scheduler);

        SwingUtilities.invokeLater(() -> gui.setVisible(true));

        gui.setStatus(true);
        //wait until the simulation is completed (all fires are extinguished and all drones are at home)
        gui.setStatus(false);

    }
}
