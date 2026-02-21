import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//GUI Scaffold
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
        super("PI2 - Firefighting Drone Swarm");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 650);
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
        droneTable.getColumnModel().getColumn(2).setCellRenderer(new ProgressCellRenderer()); //TODO
        JPanel dronesPanel = new JPanel(new BorderLayout(6, 6));
        dronesPanel.setBorder(new TitledBorder("Drones"));
        dronesPanel.add(new JScrollPane(droneTable), BorderLayout.CENTER);

        //lenged for drone states
        JPanel legendPanel = buildDroneLegend();
        dronesPanel.add(legendPanel, BorderLayout.SOUTH);

        //events table
        String[] eventCols = {"Time", "Zones", "Type", "Severity", "Status"};
        eventTableModel = new DefaultTableModel(eventCols, 0){
            @Override
            public boolean isCellEditable(int row, int col){
                return false;
            }
        };
        JTable eventTable = new JTable(eventTableModel);
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
        JPanel summerybar = new JPanel(new FlowLayout(FlowLayout.LEFT, 20,4));
        summerybar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        activeFiresLabel = new JLabel("Active Fires: ");
        activeFiresLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD,14));
        activeFiresLabel.setForeground(new Color(180,50,50));
        droneSummeryLabel = new JLabel("Drones: 0 Total");
        droneSummeryLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        summerybar.add(activeFiresLabel);
        summerybar.add(Box.createHorizontalStrut(30));
        summerybar.add(droneSummeryLabel);
        add(summerybar,BorderLayout.NORTH);

        //bottom status bar
        statusLabel= new JLabel("Status: Waiting for sim to start...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN,12));
        add(statusLabel,BorderLayout.SOUTH);
    }

    //register a drone so it appears in the table
    public void registerDrone(String droneName, float waterCapacity){
        SwingUtilities.invokeLater(() -> {
            droneTableModel.addRow(new Object[]{
                droneName,
                DroneState.IDLE.getLabel(),
                (int)(waterCapacity*ProgressCellRenderer.precision),
                "Base"
            });
            droneMarkers.put(droneName, new DroneMarker(droneName,-1,DroneState.IDLE));
            refreshSummary();
        });
    }
    //update drones state, watr lvl and assigned zone
    public void updateDroneState(String droneName, DroneState state, float waterLevel, int zoneId){
        SwingUtilities.invokeLater(() -> {
            for(int r = 0; r < droneTableModel.getRowCount(); r++){
                if(droneName.equals(droneTableModel.getValueAt(r, 0))){
                    droneTableModel.setValueAt(state.getLabel(), r, 1);
                    droneTableModel.setValueAt((int)(waterLevel*ProgressCellRenderer.precision), r, 2);
                    droneTableModel.setValueAt(zoneId > 0 ? "Zone " + zoneId : "Base", r, 3);
                    break;
                }
            }
            droneMarkers.put(droneName, new DroneMarker(droneName,zoneId,state));
            zonesPanel.repaint();
            refreshSummary();
        });
    }
    public int addEvent(String time, int zoneId, String eventType, String severity) {
        final int[] rowIdx = new int[1];
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                rowIdx[0] = addEventInternal(time, zoneId, eventType, severity);
            } else {
                SwingUtilities.invokeAndWait(() ->
                        rowIdx[0] = addEventInternal(time, zoneId, eventType, severity));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rowIdx[0];
    }
    private int addEventInternal(String time, int zoneId, String eventType, String severity) {
        eventTableModel.addRow(new Object[]{
                time,
                String.valueOf(zoneId),
                eventType,
                severity,
                "Pending"
        });
        zoneFireStatus.put(zoneId, FireStatus.Active);
        zoneSeverity.put(zoneId, severity);
        zonesPanel.repaint();
        refreshSummary();
        return eventTableModel.getRowCount() - 1;
    }

    //update status of event row
    public void updateEventStatus(int rowIndex, String newStatus){
        SwingUtilities.invokeLater(()->{
            if(rowIndex >= 0 && rowIndex < eventTableModel.getRowCount()){
                eventTableModel.setValueAt(newStatus, rowIndex, 4);
            }
        });
    }

    //mark zone's fire as extringushed
    public void setFireExtinguished(int zoneID){
        SwingUtilities.invokeLater(()-> {
            zoneFireStatus.put(zoneID, FireStatus.Extinguished);
            zonesPanel.repaint();
            refreshSummary();
        });
    }

    //clear zones fire status
    public void clearZoneFire(int zoneId){
        SwingUtilities.invokeLater(()-> {
            zoneFireStatus.put(zoneId,FireStatus.None);
            zoneSeverity.remove(zoneId);
            zonesPanel.repaint();
            refreshSummary();
        });
    }
    //set bottom status bar
    public void setStatus(String text){
        SwingUtilities.invokeLater(()-> statusLabel.setText("Status:" + text));
    }
    //internal helpers
    private void refreshSummary(){
        long activeFires = zoneFireStatus.values().stream()
            .filter(s -> s == FireStatus.Active).count();
        activeFiresLabel.setText("Active Fires:" + activeFires);
        int total = droneTableModel.getRowCount();
        long idle = 0, InRoute = 0 , dropping = 0;
        for (int r = 0; r < total; r++) {
            String state = (String) droneTableModel.getValueAt(r, 1);
            if (DroneState.IDLE.getLabel().equals(state)) idle++;
            else if (DroneState.InRoute.getLabel().equals(state)) InRoute++;
            else if (DroneState.DroppingAgent.getLabel().equals(state)) dropping++;
        }
        droneSummeryLabel.setText(String.format(
            "Drones: %d total | %d idle | %d en route | %d dropping", total, idle, InRoute, dropping));
    }

    //load zones 
    private List<ZoneRect> loadZones() {
        List<ZoneRect> z = new ArrayList<>();
        z.add(new ZoneRect(1, 30, 30, 240, 160));
        z.add(new ZoneRect(2, 290, 30, 230, 160));
        z.add(new ZoneRect(3, 30, 210, 240, 160));
        z.add(new ZoneRect(4, 290, 210, 230, 160));
        z.add(new ZoneRect(5, 160, 390, 230, 130));
        return z;
    }

    private JPanel buildDroneLegend(){
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 10,2));
        legend.setBackground(new Color(245,245,245));
        for(DroneState ds : DroneState.values()){
            JLabel swatch = new JLabel("■ " + ds.getLabel());
            swatch.setForeground(ds.getColor());
            swatch.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            legend.add(swatch);
        }
        return legend;
    }

    //marker data for drowing a drone on the zone map
    static class DroneMarker{
        final String name;
        final int zoneId;
        final DroneState state;
        DroneMarker(String name,int zoneId,DroneState state){
            this.name=name;
            this.zoneId=zoneId;
            this.state=state;
        }
    }
    //rectangles representing a zone on the map
    static class ZoneRect{
        final int id,x,y,w,h;
        ZoneRect(int id, int x,int y,int w, int h){
            this.id=id;
            this.x=x;
            this.y=y;
            this.w=w;
            this.h=h;
        }
        int centreX(){return x+w/2;}
        int centreY(){return y+h/2;}
    }
    //zone map panel
       static class ZonesPanel extends JPanel {
        private final List<ZoneRect> zones;
        private final Map<Integer, FireStatus> fireStatus;
        private final Map<Integer, String> severity;
        private final Map<String, DroneMarker> drones;

        ZonesPanel(List<ZoneRect> zones,
                   Map<Integer, FireStatus> fireStatus,
                   Map<Integer, String> severity,
                   Map<String, DroneMarker> drones) {
            this.zones = zones;
            this.fireStatus = fireStatus;
            this.severity = severity;
            this.drones = drones;
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(560, 560));
        }
       @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(235, 235, 235));
            for (int x = 0; x < getWidth(); x += 25) g2.drawLine(x, 0, x, getHeight());
            for (int y = 0; y < getHeight(); y += 25) g2.drawLine(0, y, getWidth(), y);

            //draw zones
            for (ZoneRect z : zones) {
                FireStatus fs = fireStatus.getOrDefault(z.id, FireStatus.None);
                switch (fs) {
                    case Active:
                        g2.setColor(new Color(255, 200, 200));   
                        break;
                    case Extinguished:
                        g2.setColor(new Color(200, 255, 200));   
                        break;
                    default:
                        g2.setColor(new Color(200, 220, 255));  
                        break;
                }
                g2.fillRect(z.x, z.y, z.w, z.h);

                Color borderColor;
                switch (fs) {
                    case Active:      borderColor = new Color(200, 50, 50);  break;
                    case Extinguished: borderColor = new Color(50, 160, 50); break;
                    default:          borderColor = new Color(60, 90, 160);  break;
                }
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(z.x, z.y, z.w, z.h);

                // Zone label
                g2.setColor(Color.BLACK);
                g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
                g2.drawString("Z(" + z.id + ")", z.x + 6, z.y + 18);

                // Fire indicator icon
                if (fs == FireStatus.Active) {
                    g2.setColor(new Color(220, 50, 30));
                    g2.setFont(getFont().deriveFont(Font.BOLD, 18f));
                    g2.drawString("🔥", z.x + z.w - 30, z.y + 22);
                    // Severity label
                    String sev = severity.getOrDefault(z.id, "");
                    if (!sev.isEmpty()) {
                        g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
                        g2.setColor(new Color(160, 30, 30));
                        g2.drawString(sev, z.x + 6, z.y + 34);
                    }
                } else if (fs == FireStatus.Extinguished) {
                    g2.setColor(new Color(40, 140, 40));
                    g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
                    g2.drawString("✓ Extinguished", z.x + 6, z.y + 34);
                }
            }
            // draw drone markers
            int droneOffset = 0;
            for (DroneMarker dm : drones.values()) {
                ZoneRect target = null;
                if (dm.zoneId > 0) {
                    for (ZoneRect z : zones) {
                        if (z.id == dm.zoneId) { target = z; break; }
                    }
                }
                int dx, dy;
                if (target != null) {
                    dx = target.centreX() + droneOffset * 25 - 10;
                    dy = target.y + target.h - 28;
                } else {
                    dx = 15 + droneOffset * 65;
                    dy = getHeight() - 35;
                }
                g2.setColor(dm.state.getColor());
                g2.fillRoundRect(dx, dy, 50, 20, 8, 8);
                g2.setColor(Color.BLACK);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(dx, dy, 50, 20, 8, 8);
                g2.setFont(getFont().deriveFont(Font.BOLD, 10f));
                g2.setColor(Color.WHITE);
                String shortName = dm.name.replaceAll("[^0-9]", "");
                g2.drawString("D(" + shortName + ")", dx + 5, dy + 14);
                droneOffset++;
            }
            g2.dispose();
        }
    }
    //cell renderer for drone state column
    static class DroneStateCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            if (value != null) {
                String label = value.toString();
                for (DroneState ds : DroneState.values()) {
                    if (ds.getLabel().equals(label)) {
                        c.setForeground(ds.getColor().darker());
                        setFont(getFont().deriveFont(Font.BOLD));
                        break;
                    }
                }
            }
            return c;
        }
    }
    //cell renderer for event state column
    static class EventStatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            if (value != null) {
                String status = value.toString().toLowerCase();
                if (status.contains("extinguished")) {
                    c.setForeground(new Color(40, 140, 40));
                } else if (status.contains("progress") || status.contains("dropping")) {
                    c.setForeground(new Color(200, 120, 20));
                } else if (status.contains("dispatched") || status.contains("in route")) {
                    c.setForeground(new Color(50, 100, 200));
                } else if (status.contains("pending")) {
                    c.setForeground(new Color(150, 150, 150));
                } else {
                    c.setForeground(Color.BLACK);
                }
                setFont(getFont().deriveFont(Font.BOLD));
            }
            return c;
        }
    }

    //a table cell renderer that displays a JProgressBar
    static class ProgressCellRenderer extends JProgressBar implements TableCellRenderer {
        public static final int precision = 10;

        ProgressCellRenderer() {
            super();
            super.setMaximum((int)(Drone.TANK_SIZE*precision));
            setStringPainted(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            String currentVal = String.format("%.1f", ((float)getValue()/(float)precision));
            String maxVal = String.format("%.1f", Drone.TANK_SIZE);
            setString(currentVal+"/"+maxVal);

            super.paintComponent(g);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int progress = 0;
            if (value instanceof Float) {
                progress = Math.round(((Float) value) * 100f);
            } else if (value instanceof Integer) {
                progress = (int) value;
            }
            setValue(progress);
            return this;
        }
    }

}