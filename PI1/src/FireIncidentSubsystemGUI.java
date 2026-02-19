import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//PI2 - dynamic GUI
// - 
public class FireIncidentSubsystemGUI extends JFrame {
     //drone states
        public enum DroneState{
            IDLE("Idle", new Color(100,180,100)),
            InRoute("In Route", new Color(60,130,220)),
            DroppingAgent("Dropping Agent", new Color(220,140,40)),
            Returning("Returning", new Color(160,100,200)),
            Refilling("Refilling", new Color(80,180,180));
            private final String label;
            private final Color color;
            DroneState(String lable, Color color){
                this.label = lable;
                this.color = color;
            }
            public String getLabel(){return label;}
            public Color getColor(){return color;}
        }
        //fire status for zones
        public enum FireStatus{
            None, Active, Extinguished
        }
        //data models
        private final DefaultTableModel droneTableModel;
        private final DefaultTableModel eventTableModel;
        //zone map state
        private final List<ZoneRect> zones;
        private final Map<Integer,FireStatus> zoneFireStatus;
        private final Map<Integer,String> zoneSeverity;
        private final ZonesPanel zonesPanel;
        //drone tracking on map
        private final Map<String,DroneMarker> droneMarkers;
        //summery labels
        private final JLabel activeFiresLabel;
        private final JLabel droneSummeryLabel;
        private final JLabel statusLabel;

    public FireIncidentSubsystemGUI() {
        super("PI2 - FFirefighting Drone Swarm");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        //init state maps
        zoneFireStatus = new HashMap<>();
        zoneSeverity = new HashMap<>();
        droneMarkers = new HashMap<>();

        //zones panel
        zones = loadZones();
        zonesPanel = new ZonesPanel(zones, zoneFireStatus, zoneSeverity, droneMarkers);
        zonesPanel.setBorder(new TitledBorder("Zone Map"));

        JPanel rightpPanel = new JPanel(new GridLayout(2,1,8,8));
        
        //drone table
        String[] droneCols = {"Drone", "State", "Water (L)", "Zone"};
        droneTableModel = new DefaultTableModel(droneCols, 0){
            @Override
            public boolean isCellEditable(int row, int col){
                return false;
            }
        };
        JTable droneTable = new JTable(droneTableModel);
        droneTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN,12));
        droneTable.setRowHeight(22);
        droneTable.getColumnModel().getColumn(1).setCellRenderer(new DroneStateCellRenderer());
        JPanel dronesPanel = new JPanel(new BorderLayout(6, 6));
        dronesPanel.setBorder(new TitledBorder("Drones"));
        dronesPanel.add(new JScrollPane(droneTable), BorderLayout.CENTER);

        //lenged for drone states
        JPanel legendPanel = buildDronesLegend();
        dronesPanel.add(legendPanel, BorderLayout.SOUTH);

        //events table
        String[] eventCols = {"Time", "Zones", "Type", "Severity", "Status"};
        eventTableModel = new DefaultTableModel(eventCols, 0){
            @Override
            public boolean isCellEditable(int row, int col){
                return false;
            }
        };
        JTable evenTable = new JTable(eventTableModel);
        eventTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        eventTable.setRowHeight(20);
        eventTable.getColumnModel().getColumn(4).setCellRenderer(new EventStatusCellRenderer());
        JPanel eventsPanel = new JPanel(new BorderLayout(6, 6));
        eventsPanel.setBorder(new TitledBorder("Fire Incidents"));
        eventsPanel.add(new JScrollPane(eventTable), BorderLayout.CENTER);
        
        rightpPanel.add(dronesPanel);
        rightpPanel.add(eventsPanel);
        rightpPanel.setPreferredSize(new Dimension(420,0));

        add(zonesPanel, BorderLayout.CENTER);
        add(rightpPanel, BorderLayout.EAST);

        //top summery bar
        







    }
    //hardcoded zones for PI1
    private static List<ZoneRect> sampleZones() {
        List<ZoneRect> zones = new ArrayList<>();
        zones.add(new ZoneRect(1, 60, 60, 260, 160));
        zones.add(new ZoneRect(2, 360, 90, 220, 200));
        zones.add(new ZoneRect(3, 160, 270, 320, 160));
        return zones;
    }

    private static JPanel buildDronesPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("Drone 1 — READY — Water: 30L — Battery: 100%");
        model.addElement("Drone 2 — (placeholder)");
        model.addElement("Drone 3 — (placeholder)");
        JList<String> list = new JList<>(model);
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        //placeholder buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(new JButton("Dispatch (placeholder)"));
        buttons.add(new JButton("Recall (placeholder)"));
        buttons.add(new JButton("Refill (placeholder)"));
        panel.add(buttons, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(380, 250));
        return panel;
    }
    private static JPanel buildEventsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        //static placeholder table
        String[] cols = {"Time", "Zone", "Type", "Severity", "Status"};
        Object[][] rows = {
                {"14:00", "1", "FIRE_DETECTED", "LOW", "queued (placeholder)"},
                {"14:05", "2", "DRONE_REQUEST", "HIGH", "queued (placeholder)"},
                {"14:10", "3", "FIRE_DETECTED", "MODERATE", "queued (placeholder)"}
        };
        JTable table = new JTable(rows, cols);
        table.setFillsViewportHeight(true);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        //placeholder controls
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JButton("Load CSV (placeholder)"));
        controls.add(new JButton("Start Simulation (placeholder)"));
        controls.add(new JButton("Clear Events (placeholder)"));
        panel.add(controls, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(380, 250));
        return panel;
    }
    private static class ZonesPanel extends JPanel {
        private final List<ZoneRect> zones;
        ZonesPanel(List<ZoneRect> zones) {
            this.zones = zones;
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(600, 500));
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(230, 230, 230));
            for (int x = 0; x < getWidth(); x += 25) g2.drawLine(x, 0, x, getHeight());
            for (int y = 0; y < getHeight(); y += 25) g2.drawLine(0, y, getWidth(), y);
            for (ZoneRect z : zones) {
                g2.setColor(new Color(200, 220, 255));
                g2.fillRect(z.x, z.y, z.w, z.h);
                g2.setColor(new Color(60, 90, 160));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(z.x, z.y, z.w, z.h);
                g2.setColor(Color.BLACK);
                g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
                g2.drawString("Zone " + z.id, z.x + 8, z.y + 20);
            }
            g2.setColor(Color.DARK_GRAY);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            g2.dispose();
        }
    }
    private static class ZoneRect {
        final int id;
        final int x, y, w, h;
        ZoneRect(int id, int x, int y, int w, int h) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FireIncidentSubsystemGUI().setVisible(true));
    }
}
