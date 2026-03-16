import java.util.List;
import javax.swing.*;
public class Main {
    public static void main(String[] args) throws Exception {
        List<Zone> zones = Zone.loadFromCSV("SYSC3303-Project-Group5/sample_zone_file.csv");
        Scheduler scheduler = new Scheduler();
        new Thread(scheduler).start();
        SchedulerGUI gui = new SchedulerGUI(zones, scheduler);

        SwingUtilities.invokeLater(() -> gui.setVisible(true));

    }
}
