import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

//GUI Scaffold
public class FireIncidentSubsystemGUI extends JFrame {
     //drone states
    public enum DroneStateGui {
        IDLE("Idle", new Color(100,180,100)),
        InRoute("In Route", new Color(60,130,220)),
        DroppingAgent("Dropping Agent", new Color(220,140,40)),
        Returning("Returning", new Color(160,100,200)),
        Refilling("Refilling", new Color(80,180,180));
        private final String label;
        private final Color color;
        DroneStateGui(String lable, Color color){
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
    private final List<Zone> zoneData;
    private final List<Drone> drones;
    private final List<Event> events;

    private final Map<Integer,FireStatus> zoneFireStatus;
    private final Map<Integer,String> zoneSeverity;
    private final ZonesPanel zonesPanel;
    //drone tracking on map
    private final Map<String,DroneMarker> droneMarkers;
    //summery labels
    private final JLabel activeFiresLabel;
    private final JLabel droneSummeryLabel;
    private final JLabel statusLabel;
        
    public FireIncidentSubsystemGUI(List<Zone> zoneData) {
        super("PI2 - Firefighting Drone Swarm");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        this.drones = new LinkedList<>();
        this.events = new LinkedList<>();

        //init state maps
        zoneFireStatus = new HashMap<>();
        zoneSeverity = new HashMap<>();
        droneMarkers = new HashMap<>();
        

        //zones panel
        this.zoneData = zoneData;
        zones = loadZones();
        zonesPanel = new ZonesPanel(zones, zoneFireStatus, zoneSeverity, droneMarkers);
        zonesPanel.setBorder(new TitledBorder("Zone Map"));

        JPanel rightpPanel = new JPanel(new GridLayout(2,1,8,8));
        
        //drone table
        String[] droneCols = {"Drone", "State", "Water (L)", "Zone", "Battery"};
        droneTableModel = new DefaultTableModel(droneCols, 0){
            @Override
            public boolean isCellEditable(int row, int col){
                return false;
            }
        };
        JTable droneTable = new JTable(droneTableModel);
        droneTable.getTableHeader().setReorderingAllowed(false);
        droneTable.getTableHeader().setResizingAllowed(false);
        droneTable.setRowSelectionAllowed(false);
        droneTable.setColumnSelectionAllowed(false);
        droneTable.setCellSelectionEnabled(false);

        droneTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN,12));
        droneTable.setRowHeight(22);
        droneTable.getColumnModel().getColumn(1).setCellRenderer(new DroneStateCellRenderer());
        droneTable.getColumnModel().getColumn(2).setCellRenderer(new ReservoirRenderer());
        droneTable.getColumnModel().getColumn(4).setCellRenderer(new ProgressCellRenderer());
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
        eventTable.getTableHeader().setReorderingAllowed(false);
        eventTable.getTableHeader().setResizingAllowed(false);
        eventTable.setRowSelectionAllowed(false);
        eventTable.setColumnSelectionAllowed(false);
        eventTable.setCellSelectionEnabled(false);

        eventTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        eventTable.setRowHeight(20);
        eventTable.getColumnModel().getColumn(4).setCellRenderer(new EventProgressRenderer());
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

    public void paintDrone(Drone d, int r) {
        droneTableModel.setValueAt(d.getDroneName(), r, 0);

        DroneStateGui dsg;
        switch (d.getDroneState()) {
            case DroneState.enRoute:
                dsg = DroneStateGui.InRoute;
                break;
            case DroneState.droppingAgent:
                dsg = DroneStateGui.DroppingAgent;
                break;
            case DroneState.returnForRefill:
                dsg = DroneStateGui.Refilling;
                break;
            case DroneState.returnOrigin:
                dsg = DroneStateGui.Returning;
                break;
            default:
                dsg = DroneStateGui.IDLE;
                break;
        }

        droneTableModel.setValueAt(dsg.getLabel(), r, 1);
        droneTableModel.setValueAt((int)(d.getWaterRemaining()*ReservoirRenderer.precision), r, 2);
        droneTableModel.setValueAt(d.getCurrentZoneId() > 0 ? "Zone " + d.getCurrentZoneId() : "Base", r, 3);
        droneTableModel.setValueAt(d.batteryPercent(), r, 4);

        droneMarkers.put(d.getDroneName(), new DroneMarker(d.getDroneName(), d.getCurrentZoneId(), dsg));
    }

    public void paintDrone(Drone d) {
        int r = drones.indexOf(d);
        if(r!=-1) SwingUtilities.invokeLater(()->{
            paintDrone(d, r);
            zonesPanel.repaint();
            refreshSummary();
        });
    }

    public void paintAllDrones() {
        droneTableModel.setRowCount(drones.size());
        droneMarkers.clear();

        for(int r=0; r<drones.size(); r++) {
            paintDrone(drones.get(r), r);
        }

        zonesPanel.repaint();
        refreshSummary();
    }

    //register a drone so it appears in the table
    public void registerDrone(Drone drone){
        if(!drones.contains(drone)) drones.add(drone);
        SwingUtilities.invokeLater(this::paintAllDrones);
    }

    public void paintEvent(Event e, int r) {
        eventTableModel.setValueAt(e.getTime(), r, 0);
        eventTableModel.setValueAt(String.valueOf(e.getZoneID()), r, 1);
        eventTableModel.setValueAt(e.getEventType().toString(), r, 2);
        eventTableModel.setValueAt(e.getSeverity().toString(), r, 3);

        switch(e.currentState()) {
            case Event.State.INACTIVE:
                eventTableModel.setValueAt(new EventProgressRenderer.ProgressData(0, "Inactive"), r, 4);
                zoneFireStatus.put(e.getZoneID(), FireStatus.None);
                break;
            case Event.State.EXTINGUISHED:
                eventTableModel.setValueAt(new EventProgressRenderer.ProgressData(100, "Extinguished"), r, 4);
                zoneFireStatus.put(e.getZoneID(), FireStatus.Extinguished);
                break;
            case Event.State.PENDING:
                eventTableModel.setValueAt(new EventProgressRenderer.ProgressData(0, "Pending"), r, 4);
                zoneFireStatus.put(e.getZoneID(), FireStatus.Active);
                zoneSeverity.put(e.getZoneID(), e.getSeverity().toString());
                break;
            case Event.State.DROPPING:
                eventTableModel.setValueAt(new EventProgressRenderer.ProgressData((int)(((e.getWaterRequired()-e.getWaterLeft())*100)/e.getWaterRequired()), "Dropping"), r, 4);
                zoneFireStatus.put(e.getZoneID(), FireStatus.Active);
                break;
            case Event.State.DISPATCHED:
                eventTableModel.setValueAt(new EventProgressRenderer.ProgressData((int)(((e.getWaterRequired()-e.getWaterLeft())*100)/e.getWaterRequired()), "Dispatched"), r, 4);
                zoneFireStatus.put(e.getZoneID(), FireStatus.Active);
                break;
            default:
                eventTableModel.setValueAt(new EventProgressRenderer.ProgressData((int)(((e.getWaterRequired()-e.getWaterLeft())*100)/e.getWaterRequired()), "Unknown"), r, 4);
                zoneFireStatus.put(e.getZoneID(), FireStatus.Active);
                break;
        }

    }

    public void paintEvent(Event e) {
        int r = events.indexOf(e);
        if(r!=-1) {
            SwingUtilities.invokeLater(()->paintEvent(e,r));
            zonesPanel.repaint();
            refreshSummary();
        }
    }

    public void paintAllEvents() {
        eventTableModel.setRowCount(events.size());

        for(int r=0; r<events.size(); r++) {
            paintEvent(events.get(r), r);
        }

        zonesPanel.repaint();
        refreshSummary();
    }

    public void addEvent(Event event) {
        if(!events.contains(event)) events.add(event);
        SwingUtilities.invokeLater(this::paintAllEvents);
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
        long activeFires = events.stream()
                .filter(e ->
                        e.currentState() != Event.State.EXTINGUISHED &&
                        e.currentState() != Event.State.INACTIVE)
                .count();
        activeFiresLabel.setText("Active Fires:" + activeFires);

        int total = drones.size();
        long idle = 0, InRoute = 0 , dropping = 0;
        for (Drone d : drones) {
            if (d.getDroneState() == DroneState.idle) idle++;
            if (d.getDroneState() == DroneState.enRoute) InRoute++;
            if (d.getDroneState() == DroneState.droppingAgent) dropping++;
        }
        droneSummeryLabel.setText(String.format(
            "Drones: %d total | %d idle | %d en route | %d dropping", total, idle, InRoute, dropping));
    }

    //load zones from csv 
    private List<ZoneRect> loadZones() {
    if (zoneData == null || zoneData.isEmpty()) return new ArrayList<>();

    int maxX = 0, maxY = 0;
    for (Zone z : zoneData) {
        if (z.x2 > maxX) maxX = z.x2;
        if (z.y2 > maxY) maxY = z.y2;
    }

    int panelW = 520;
    int panelH = 520;
    int padding = 20;
    double scaleX = (panelW - 2.0 * padding) / Math.max(maxX, 1);
    double scaleY = (panelH - 2.0 * padding) / Math.max(maxY, 1);
    double scale = Math.min(scaleX, scaleY);

    List<ZoneRect> z = new ArrayList<>();
    for (Zone raw : zoneData) {
        int x = (int) (raw.x1 * scale) + padding;
        int y = (int) (raw.y1 * scale) + padding;
        int w = (int) ((raw.x2 - raw.x1) * scale);
        int h = (int) ((raw.y2 - raw.y1) * scale);
        z.add(new ZoneRect(raw.id, x, y, w, h));
    }
    System.out.println("[GUI] loaded " + z.size() + " zones");
    return z;
}

    private JPanel buildDroneLegend(){
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 10,2));
        legend.setBackground(new Color(245,245,245));
        for(DroneStateGui ds : DroneStateGui.values()){
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
        final DroneStateGui state;
        DroneMarker(String name, int zoneId, DroneStateGui state){
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
                for (DroneStateGui ds : DroneStateGui.values()) {
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

    //a table cell renderer that displays the current water reservoir
    static class ReservoirRenderer extends ProgressCellRenderer {
        public static final int precision = 10;

        ReservoirRenderer() {
            super();
            super.setMaximum((int)(Drone.TANK_SIZE*precision));
        }

        @Override
        protected void paintComponent(Graphics g) {
            String currentVal = String.format("%.1f", ((float)getValue()/(float)precision));
            String maxVal = String.format("%.1f", Drone.TANK_SIZE);
            setString(currentVal+"/"+maxVal);

            super.paintComponent(g);
        }
    }

    //a table cell renderer that displays the current water reservoir
    static class EventProgressRenderer extends ProgressCellRenderer {
        public static class ProgressData {
            private final int value;
            private final String text;

            public ProgressData(int value, String text) {
                this.value = value;
                this.text = text;
            }

            public int getValue() { return value; }
            public String getText() { return text; }
        }

        EventProgressRenderer() {
            super();
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {

            if (value instanceof ProgressData data) {
                setValue(data.getValue());
                setString(data.getText());


                if (data.getText() != null) {
                    String status = data.getText().toLowerCase();
                    if (status.contains("extinguished")) {
                        setForeground(new Color(40, 140, 40));
                        setBackground(new Color(40, 140, 40, 50));
                    } else if (status.contains("dropping")) {
                        setForeground(new Color(200, 120, 20));
                    } else if (status.contains("dispatched")) {
                        setForeground(new Color(50, 100, 200));
                        setBackground(new Color(50, 100, 200, 50));
                    } else if (status.contains("pending")) {
                        setForeground(new Color(150, 150, 150));
                        setBackground(new Color(150, 150, 150, 50));
                    } else {
                        setForeground(Color.BLACK);
                        setBackground(Color.WHITE);
                    }
                    setFont(getFont().deriveFont(Font.BOLD));
                }
            }
            return this;
        }
    }

    //a table cell renderer that displays a JProgressBar
    static class ProgressCellRenderer extends JProgressBar implements TableCellRenderer {
        ProgressCellRenderer() {
            super();
            setStringPainted(true);
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