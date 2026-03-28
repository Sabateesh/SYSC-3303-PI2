import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

//GUI Scaffold
public class SchedulerGUI extends JFrame {
     //drone states
    public enum DroneStateGui {
        IDLE("Idle", new Color(100,180,100)),
        InRoute("In Route", new Color(60,130,220)),
        DroppingAgent("Dropping Agent", new Color(220,140,40)),
        Returning("Returning", new Color(160,100,200)),
        Refilling("Refilling", new Color(80,180,180)),FaultStuck("Stuck (Fault)", new Color(220,50,50)),  FaultNozzle("Nozzle Jam (Offline)", new Color(160,20,20)),
        CommFailure("Comm Failure", new Color(190, 40, 40)),
        DroneStuckFault("Drone Stuck", new Color(168, 49, 49)),
        ArrivalSensorFault("Arrival Sensor Fault", new Color(196, 78, 0)),
        NozzleStuckFault("Nozzle Stuck", new Color(145, 34, 164));
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
    private final DefaultTableModel logTableModel;

    //zone map state
    private final List<ZoneRect> zones;
    private final List<Zone> zoneData;
    private volatile List<Drone> drones;
    private volatile List<Event> events;
    private Scheduler scheduler;

    private final Map<Integer,FireStatus> zoneFireStatus;
    private final Map<Integer,String> zoneSeverity;
    private final ZonesPanel zonesPanel;
    //drone tracking on map
    private final Map<String,DroneMarker> droneMarkers;
    //summery labels
    private final JLabel activeFiresLabel;
    private final JLabel droneSummeryLabel;
    private final JLabel statusLabel;
    private final JLabel faultSummaryLabel;
    private JComboBox<String> faultDroneSelector;
    private JComboBox<String> faultTypeSelector;


    public SchedulerGUI(List<Zone> zoneData, Scheduler scheduler) {
        super("PI4 - Firefighting Drone Swarm");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 750);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        try {
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        } catch (Exception e) {
            System.out.println("Error occurred while changing JSwing MetalLookAndFeel");
        }

        this.drones = new LinkedList<>();
        this.events = new LinkedList<>();
        this.scheduler = scheduler;
        scheduler.setGui(this);

        //init state maps
        zoneFireStatus = new HashMap<>();
        zoneSeverity = new HashMap<>();
        droneMarkers = new HashMap<>();


        //zones panel
        this.zoneData = zoneData;
        zones = loadZones();
        zonesPanel = new ZonesPanel(zones, zoneFireStatus, zoneSeverity, droneMarkers);
        zonesPanel.setBorder(new TitledBorder("Zone Map"));

        JPanel rightPanel = new JPanel(new GridLayout(2,1,8,8));
        
        //drone table
        String[] droneCols = {"Drone", "State", "Water (L)", "Zone", "Battery", "Fault"};
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
        droneTable.getColumnModel().getColumn(5).setCellRenderer(new FaultCellRenderer());
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

        rightPanel.add(dronesPanel);
        rightPanel.add(eventsPanel);
        rightPanel.setPreferredSize(new Dimension(450,0));
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 4));
        bottomPanel.setPreferredSize(new Dimension(0, 160));

        String[] logCols = {"Timestamp", "Event"};
        logTableModel = new DefaultTableModel(logCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable logTable = new JTable(logTableModel);
        logTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logTable.setRowHeight(18);
        logTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        logTable.getColumnModel().getColumn(0).setMaxWidth(160);
        logTable.setRowSelectionAllowed(false);
        JScrollPane logScroll = new JScrollPane(logTable);
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(new TitledBorder("Event Log (Timestamped)"));
        logPanel.add(logScroll, BorderLayout.CENTER);

        JPanel faultPanel = new JPanel();
        faultPanel.setLayout(new BoxLayout(faultPanel, BoxLayout.Y_AXIS));
        faultPanel.setBorder(new TitledBorder("Fault Injection"));
        faultPanel.setPreferredSize(new Dimension(300, 0));
        faultDroneSelector = new JComboBox<>();
        faultTypeSelector = new JComboBox<>(new String[]{"Stuck Mid-Flight", "Nozzle Jammed"});
        JButton injectBtn = new JButton("Inject Fault");
        injectBtn.setBackground(new Color(220, 60, 60));
        injectBtn.setForeground(Color.WHITE);
        injectBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        injectBtn.addActionListener(e -> injectFault());
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row1.add(new JLabel("Drone:")); row1.add(faultDroneSelector);
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row2.add(new JLabel("Fault:")); row2.add(faultTypeSelector);
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row3.add(injectBtn);
        faultPanel.add(row1); faultPanel.add(row2); faultPanel.add(row3);

        bottomPanel.add(logPanel, BorderLayout.CENTER);
        bottomPanel.add(faultPanel, BorderLayout.EAST);

        add(zonesPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);


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
        summerybar.add(Box.createHorizontalStrut(20));
        faultSummaryLabel = new JLabel("Faults: 0");
        faultSummaryLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        faultSummaryLabel.setForeground(new Color(200, 40, 40));
        summerybar.add(faultSummaryLabel);
        add(summerybar,BorderLayout.NORTH);

        //bottom status bar
        statusLabel= new JLabel("Status: Waiting for sim to start... (Make sure to start 'FireIncidentSubsystem')");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN,12));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);
    }

    public synchronized void paintDrone(Drone d, int r) {
        if(r>=droneTableModel.getRowCount()) droneTableModel.setRowCount(r+1);
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
            case DroneState.commFailure:
                dsg = DroneStateGui.CommFailure;
                break;
            case DroneState.droneStuckFault:
                dsg = DroneStateGui.DroneStuckFault;
                break;
            case DroneState.arrivalSensorFault:
                dsg = DroneStateGui.ArrivalSensorFault;
                break;
            case DroneState.nozzleStuckFault:
                dsg = DroneStateGui.NozzleStuckFault;
                break;
            case DroneState.faultStuck:
                dsg = DroneStateGui.FaultStuck;
                break;
            case DroneState.faultNozzle:
                dsg = DroneStateGui.FaultNozzle;
                break;
            default:
                dsg = DroneStateGui.IDLE;
                break;
        }

        droneTableModel.setValueAt(dsg.getLabel(), r, 1);
        droneTableModel.setValueAt((int)(d.getWaterRemaining()*ReservoirRenderer.precision), r, 2);

        // Show routing context in the Zone column
        String zoneDisplay;
        switch (d.getDroneState()) {
            case DroneState.enRoute:
                zoneDisplay = "→ Zone " + d.getTargetZoneId();
                break;
            case DroneState.returnOrigin:
            case DroneState.returnForRefill:
                zoneDisplay = (d.getCurrentZoneId() > 0 ? "Zone " + d.getCurrentZoneId() : "Base") + " → Base";
                break;
            case DroneState.faultStuck:
                zoneDisplay = "STUCK" + (d.getTargetZoneId() > 0 ? " → Zone " + d.getTargetZoneId() : "");
                break;
            case DroneState.faultNozzle:
                zoneDisplay = "OFFLINE" + (d.getCurrentZoneId() > 0 ? " @ Zone " + d.getCurrentZoneId() : "");
                break;
            default:
                zoneDisplay = d.getCurrentZoneId() > 0 ? "Zone " + d.getCurrentZoneId() : "Base";
                break;
        }
        droneTableModel.setValueAt(zoneDisplay, r, 3);
        droneTableModel.setValueAt(d.batteryPercent(), r, 4);
        String faultText;
        switch (d.getDroneState()) {
            case DroneState.faultStuck:  faultText = "STUCK"; break;
            case DroneState.faultNozzle: faultText = "NOZZLE JAM"; break;
            default:                     faultText = "None"; break;
        }
        droneTableModel.setValueAt(faultText, r, 5);


        final int baseSlotX = 20 + r * 20;
        final int baseSlotY = 500;  
        int fromPx = baseSlotX, fromPy = baseSlotY;
        int toPx   = baseSlotX, toPy   = baseSlotY;
        if (d.getDroneState() == DroneState.enRoute) {
            if (d.getCurrentZoneId() > 0) {
                for (ZoneRect z : zones) {
                    if (z.id == d.getCurrentZoneId()) { fromPx = z.fireX(); fromPy = z.fireY(); break; }
                }
            } else {
                fromPx = baseSlotX; fromPy = baseSlotY;
            }
            for (ZoneRect z : zones) {
                if (z.id == d.getTargetZoneId()) { toPx = z.fireX(); toPy = z.fireY(); break; }
            }
        } else if (d.getDroneState() == DroneState.returnOrigin || d.getDroneState() == DroneState.returnForRefill) {
            if (d.getCurrentZoneId() > 0) {
                for (ZoneRect z : zones) {
                    if (z.id == d.getCurrentZoneId()) { fromPx = z.fireX(); fromPy = z.fireY(); break; }
                }
            }
            toPx = baseSlotX; toPy = baseSlotY;
        } else if (d.getDroneState() == DroneState.faultStuck) {
            if (d.getCurrentZoneId() > 0) {
                for (ZoneRect z : zones) { if (z.id == d.getCurrentZoneId()) { fromPx = z.fireX(); fromPy = z.fireY(); break; } }
            } else { fromPx = baseSlotX; fromPy = baseSlotY; }
            if (d.getTargetZoneId() > 0) {
                for (ZoneRect z : zones) { if (z.id == d.getTargetZoneId()) { toPx = z.fireX(); toPy = z.fireY(); break; } }
            } else { toPx = fromPx; toPy = fromPy; }
        } else if (d.getDroneState() == DroneState.faultNozzle) {
            if (d.getCurrentZoneId() > 0) {
                for (ZoneRect z : zones) { if (z.id == d.getCurrentZoneId()) { fromPx = toPx = z.fireX(); fromPy = toPy = z.fireY(); break; } }
            }
        } else if (d.getCurrentZoneId() > 0) {
            for (ZoneRect z : zones) {
                if (z.id == d.getCurrentZoneId()) { fromPx = toPx = z.fireX(); fromPy = toPy = z.fireY(); break; }
            }
        }




        long animStart = d.getAnimStartTime();
        droneMarkers.put(d.getDroneName(), new DroneMarker(
                d.getDroneName(), d.getCurrentZoneId(), d.getTargetZoneId(), dsg,
                fromPx, fromPy, toPx, toPy, animStart, d.getLastAnimDurationMs()));

        updateFaultDroneSelector();

    }

    private void updateFaultDroneSelector() {
        Set<String> current = new HashSet<>();
        for (int i = 0; i < faultDroneSelector.getItemCount(); i++) current.add(faultDroneSelector.getItemAt(i));
        for (Drone d : drones) {
            String name = d.getDroneName();
            if (!current.contains(name) && d.getDroneState() != DroneState.faultNozzle) {
                faultDroneSelector.addItem(name);
                current.add(name);
            }
        }
    }

    public synchronized void paintDrone(Drone d) {
        int r = drones.indexOf(d);
        if(r!=-1) SwingUtilities.invokeLater(()->{
            paintDrone(d, r);
            zonesPanel.repaint();
            refreshSummary();
        });
    }

    public synchronized void paintAllDrones() {
        droneTableModel.setRowCount(drones.size());
        droneMarkers.clear();

        for(int r=0; r<drones.size(); r++) {
            paintDrone(drones.get(r), r);
        }

        zonesPanel.repaint();
        refreshSummary();
    }

    //register a drone so it appears in the table
    public synchronized void registerDrone(Drone drone){
        if(!drones.contains(drone)) drones.add(drone);
        SwingUtilities.invokeLater(this::paintAllDrones);
    }

    public synchronized void paintEvent(Event e, int r) {
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
                zoneSeverity.put(e.getZoneID(), e.getSeverity().toString());
                break;
            case Event.State.PARTIAL_EXTINGUISHED:
                eventTableModel.setValueAt(new EventProgressRenderer.ProgressData((int)(((e.getWaterRequired()-e.getWaterLeft())*100)/e.getWaterRequired()), "Partially Extinguished"), r, 4);
                zoneFireStatus.put(e.getZoneID(), FireStatus.Active);
                zoneSeverity.put(e.getZoneID(), e.getSeverity().toString());
                break;
            default:
                eventTableModel.setValueAt(new EventProgressRenderer.ProgressData((int)(((e.getWaterRequired()-e.getWaterLeft())*100)/e.getWaterRequired()), "Unknown"), r, 4);
                zoneFireStatus.put(e.getZoneID(), FireStatus.Active);
                break;
        }

    }

    public synchronized void paintEvent(Event e) {
        int r = events.indexOf(e);
        if(r!=-1) {
            SwingUtilities.invokeLater(()->paintEvent(e,r));
            zonesPanel.repaint();
            refreshSummary();
        }
    }

    public synchronized void paintAllEvents() {
        eventTableModel.setRowCount(events.size());

        for(int r=0; r<events.size(); r++) {
            paintEvent(events.get(r), r);
        }

        zonesPanel.repaint();
        refreshSummary();
    }

    public synchronized void addEvent(Event event) {
        if(!events.contains(event)) events.add(event);
        logEvent("Fire detected: Zone " + event.getZoneID() + " | Severity: " + event.getSeverity());
        SwingUtilities.invokeLater(this::paintAllEvents);
    }

    public synchronized void updateEvent(Event event) {
        // Find the event and update it
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).equals(event)) {
                events.set(i, event);
                break;
            }
        }
        logEvent("Event update: Zone " + event.getZoneID() + " → " + event.currentState().toString());
        SwingUtilities.invokeLater(this::paintAllEvents);
    }

    public synchronized void updateDrone(Scheduler.DroneStatus ds) {
        // note: droneId,battery,currentZoneId,water,targetZoneId,lastAnimDurationMs,animStartTime
        String droneId = ds.id;
        float batteryRemaining = ds.battery;
        int currentZoneId = ds.zoneId;
        float waterRemaining = ds.water;
        int targetZoneId = ds.targetZoneId;
        long lastAnimDurationMs = ds.lastAnimDurationMs;
        long animStartTime = ds.animStartTime;
        String stateStr = ds.state;


        // Find or create drone
        Drone drone = null;
        for (Drone d : drones) {
            if (d.getDroneName().equals(droneId)) {
                drone = d;
                break;
            }
        }
        if (drone == null) {
            // Create a dummy drone for GUI
            drone = new Drone(droneId, this.zoneData, currentZoneId, waterRemaining, batteryRemaining, targetZoneId, lastAnimDurationMs, animStartTime, stateStr);
            registerDrone(drone);
        } else {
            // Update drone fields
            // But Drone may not have setters, so perhaps call paintDrone with updated
            // Since Drone is complex, perhaps re-register or something.
            // For simplicity, assume drones are updated via state, but since no state, perhaps remove and add.
            drones.remove(drone);
            drone = new Drone(droneId, this.zoneData, currentZoneId, waterRemaining, batteryRemaining, targetZoneId, lastAnimDurationMs, animStartTime, stateStr);
            registerDrone(drone);
        }
    }

    //set bottom status bar
    public void setStatus(boolean status){
        String text = status ? "▶ Simulation started" : "■ Simulation complete";
        logEvent(status ? "Simulation STARTED" : "Simulation COMPLETE");
        SwingUtilities.invokeLater(()-> {
            statusLabel.setText(text);
            if(status)
                statusLabel.setForeground(new Color(36, 147, 0));
            else
                statusLabel.setForeground(new Color(147, 0, 0));
        });
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
        long idle = 0, InRoute = 0 , dropping = 0 ;
        long fStuck = 0, fNozzle = 0;

        for (Drone d : drones) {
            if (d.getDroneState() == DroneState.idle) idle++;
            if (d.getDroneState() == DroneState.enRoute) InRoute++;
            if (d.getDroneState() == DroneState.droppingAgent) dropping++;
            if (d.getDroneState() == DroneState.faultStuck) fStuck++;
            if (d.getDroneState() == DroneState.faultNozzle) fNozzle++;
        }
        droneSummeryLabel.setText(String.format(
            "Drones: %d total | %d idle | %d en route | %d dropping", total, idle, InRoute, dropping));
        long totalFaults = fStuck + fNozzle;
        faultSummaryLabel.setText(totalFaults > 0
                ? String.format("Faults: %d (%d stuck, %d nozzle)", totalFaults, fStuck, fNozzle)
                : "Faults: 0");
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
        int h = (int) ((raw.y2 - raw.y1) * scale);
        int y = panelH - padding - (int)(raw.y2 * scale);  
        int w = (int) ((raw.x2 - raw.x1) * scale);
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

    //marker data for drawing a drone on the zone map
    static class DroneMarker {
        final String name;
        final int zoneId;
        final int targetZoneId;
        final DroneStateGui state;
        final int fromPixelX, fromPixelY;
        final int toPixelX,   toPixelY;
        final long animStartTime;
        final long animDurationMs;
        boolean isFaulted() { return state == DroneStateGui.FaultStuck || state == DroneStateGui.FaultNozzle; }



        DroneMarker(String name, int zoneId, int targetZoneId, DroneStateGui state,
                    int fromPixelX, int fromPixelY, int toPixelX, int toPixelY,
                    long animStartTime, long animDurationMs) {
            this.name = name;
            this.zoneId = zoneId;
            this.targetZoneId = targetZoneId;
            this.state = state;
            this.fromPixelX = fromPixelX;
            this.fromPixelY = fromPixelY;
            this.toPixelX = toPixelX;
            this.toPixelY = toPixelY;
            this.animStartTime = animStartTime;
            this.animDurationMs = animDurationMs;
        }

        float animProgress() {
            boolean moving = state == DroneStateGui.InRoute
                    || state == DroneStateGui.Returning
                    || state == DroneStateGui.Refilling;
            if (state == DroneStateGui.FaultStuck) {
                if (animDurationMs <= 0) return 0.5f;
                float t = (System.currentTimeMillis() - animStartTime) / (float) animDurationMs;
                return Math.min(1f, Math.max(0f, t));
            }
            if (!moving || animDurationMs <= 0) return 1f;
            float t = (System.currentTimeMillis() - animStartTime) / (float) animDurationMs;
            return Math.min(1f, Math.max(0f, t));
        }
        int currentPixelX() { return (int)(fromPixelX + (toPixelX - fromPixelX) * animProgress()); }
        int currentPixelY() { return (int)(fromPixelY + (toPixelY - fromPixelY) * animProgress()); }
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
        int fireX() {
            Random r = new Random(id * 9371L);
            int margin = 22;
            return x + margin + r.nextInt(Math.max(1, w - 2 * margin));
        }
        int fireY() {
            Random r = new Random(id * 9371L);
            r.nextInt(); // skip x value
            int margin = 22;
            return y + margin + r.nextInt(Math.max(1, h - 2 * margin));
        }
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
            new javax.swing.Timer(50, e -> repaint()).start();
        }
       @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // earthy background
            g2.setColor(new Color(210, 200, 175));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(195, 185, 160));
            for (int x = 0; x < getWidth(); x += 25) g2.drawLine(x, 0, x, getHeight());
            for (int y = 0; y < getHeight(); y += 25) g2.drawLine(0, y, getWidth(), y);

            // river winding down the right side of the map
            java.awt.geom.GeneralPath riverPath = new java.awt.geom.GeneralPath();
            riverPath.moveTo(455, 0);
            riverPath.curveTo(480, 70,  430, 130, 460, 200);
            riverPath.curveTo(490, 270, 440, 320, 465, 390);
            riverPath.curveTo(490, 450, 445, 490, 460, 560);
            // draw river bank (wide, lighter)
            g2.setColor(new Color(160, 200, 230, 180));
            g2.setStroke(new BasicStroke(18f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(riverPath);
            // draw river water (narrower, darker)
            g2.setColor(new Color(90, 155, 210, 210));
            g2.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(riverPath);
            // river shimmer highlights
            g2.setColor(new Color(200, 230, 255, 120));
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(riverPath);

            //draw zones as forested land
            for (ZoneRect z : zones) {
                FireStatus fs = fireStatus.getOrDefault(z.id, FireStatus.None);
                drawForest(g2, z, fs);

                // border
                Color borderColor;
                switch (fs) {
                    case Active:       borderColor = new Color(180, 50, 20);  break;
                    case Extinguished: borderColor = new Color(80, 90, 70);   break;
                    default:           borderColor = new Color(60, 110, 50);  break;
                }
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(z.x, z.y, z.w, z.h);

                // Zone label (white shadow for readability)
                g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
                g2.setColor(new Color(0,0,0,80));
                g2.drawString("Z(" + z.id + ")", z.x + 7, z.y + 19);
                g2.setColor(Color.WHITE);
                g2.drawString("Z(" + z.id + ")", z.x + 6, z.y + 18);

                // Fire indicator icon — position matches drone destination
                if (fs == FireStatus.Active) {
                    int fx = z.fireX(), fy = z.fireY();
                    g2.setFont(getFont().deriveFont(Font.BOLD, 20f));
                    g2.drawString("🔥", fx - 10, fy + 8);
                    String sev = severity.getOrDefault(z.id, "");
                    if (!sev.isEmpty()) {
                        g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
                        g2.setColor(new Color(255, 240, 200));
                        g2.drawString(sev, z.x + 6, z.y + 34);
                    }
                } else if (fs == FireStatus.Extinguished) {
                    g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
                    g2.setColor(new Color(220, 240, 210));
                    g2.drawString("✓ Out", z.x + 6, z.y + 34);
                }
            }
            // draw flight-path lines (en-route = blue, returning = purple)
            for (DroneMarker dm : drones.values()) {
                boolean enRoute  = dm.state == DroneStateGui.InRoute;
                boolean returning = dm.state == DroneStateGui.Returning || dm.state == DroneStateGui.Refilling;
                boolean stuck = dm.state == DroneStateGui.FaultStuck;
                if (!enRoute && !returning && !stuck) continue;
                Color lineColor;
                if (stuck) lineColor = new Color(220, 50, 50, 180);
                else if (enRoute) lineColor = new Color(60, 130, 220, 180);
                else lineColor = new Color(150, 90, 200, 160);
                g2.setColor(lineColor);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        0, new float[]{8, 5}, 0));
                g2.drawLine(dm.fromPixelX, dm.fromPixelY, dm.toPixelX, dm.toPixelY);
                // arrowhead at destination end
                double angle = Math.atan2(dm.toPixelY - dm.fromPixelY, dm.toPixelX - dm.fromPixelX);
                int ax = dm.toPixelX, ay = dm.toPixelY;
                int[] arrowX = {ax, ax-(int)(12*Math.cos(angle-0.4)), ax-(int)(12*Math.cos(angle+0.4))};
                int[] arrowY = {ay, ay-(int)(12*Math.sin(angle-0.4)), ay-(int)(12*Math.sin(angle+0.4))};
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(lineColor);
                g2.fillPolygon(arrowX, arrowY, 3);
            }

            for (DroneMarker dm : drones.values()) {
                if (dm.state != DroneStateGui.DroppingAgent) continue;
                long t = System.currentTimeMillis();
                int cx = dm.toPixelX, cy = dm.toPixelY;
                for (int i = 0; i < 4; i++) {
                    float phase = ((t + i * 200) % 800) / 800f;
                    int dropX = cx - 7 + (i % 2) * 9;
                    int dropY = cy + 12 + (int)(phase * 22);
                    int alpha = (int)(200 * (1f - phase));
                    int size  = Math.max(2, 7 - (int)(phase * 4));
                    g2.setColor(new Color(40, 120, 220, alpha));
                    g2.fillOval(dropX - size / 2, dropY, size, size);
                }
            }

            for (DroneMarker dm : drones.values()) {
                int cx = dm.currentPixelX();
                int cy = dm.currentPixelY();
                String shortName = "D" + dm.name.replaceAll("[^0-9]", "");
                String label;
                if (dm.isFaulted()) label = shortName + (dm.state == DroneStateGui.FaultStuck ? " !" : " X");
                else if (dm.state == DroneStateGui.InRoute) label = shortName + "→" + dm.targetZoneId;
                else label = shortName;

                if (dm.isFaulted()) drawFaultDroneIcon(g2, cx, cy, dm.state.getColor(), label, dm.state);
                else drawDroneIcon(g2, cx, cy, dm.state.getColor(), label);
            }
            g2.dispose();
        }

        /** Fill a zone with a forest floor and scattered pine trees */
        private void drawForest(Graphics2D g2, ZoneRect z, FireStatus fs) {
            Color ground;
            switch (fs) {
                case Active:       ground = new Color(200, 150, 100); break;
                case Extinguished: ground = new Color(110, 115, 95);  break;
                default:           ground = new Color(120, 175, 85);  break;
            }
            g2.setColor(ground);
            g2.fillRect(z.x, z.y, z.w, z.h);

            // posRng drives positions only — never used for color so positions never shift
            Random posRng = new Random(z.id * 6791L);
            Random colRng = new Random(z.id * 3137L);
            int margin = 14;
            int treeCount = 7;
            int fx = z.fireX(), fy = z.fireY();
            for (int i = 0; i < treeCount; i++) {
                int tx = z.x + margin + posRng.nextInt(Math.max(1, z.w - 2 * margin));
                int ty = z.y + margin + posRng.nextInt(Math.max(1, z.h - 2 * margin));
                double dist = Math.sqrt((tx - fx) * (tx - fx) + (ty - fy) * (ty - fy));
                boolean nearFire = dist < 38;
                Color canopy;
                if (fs == FireStatus.Active && nearFire) {
                    canopy = new Color(210 + colRng.nextInt(40), 70 + colRng.nextInt(50), 10);
                } else if (fs == FireStatus.Extinguished && nearFire) {
                    canopy = new Color(30, 28, 25); // black charred stumps
                } else {
                    int gr = 110 + colRng.nextInt(60);
                    canopy = new Color(25 + colRng.nextInt(30), gr, 20 + colRng.nextInt(25));
                }
                drawTree(g2, tx, ty, canopy);
            }
        }

        /** Draw a small pine tree centred at (cx, cy) */
        private void drawTree(Graphics2D g2, int cx, int cy, Color canopy) {
            g2.setColor(new Color(110, 75, 40));
            g2.fillRect(cx - 2, cy + 3, 4, 6);
            int[] px1 = {cx, cx - 8, cx + 8};
            int[] py1 = {cy - 10, cy + 4, cy + 4};
            int[] px2 = {cx, cx - 6, cx + 6};
            int[] py2 = {cy - 16, cy - 4, cy - 4};
            g2.setColor(canopy);
            g2.fillPolygon(px1, py1, 3);
            g2.fillPolygon(px2, py2, 3);
            g2.setColor(canopy.darker());
            g2.setStroke(new BasicStroke(0.5f));
            g2.drawPolygon(px1, py1, 3);
        }

        private void drawDroneIcon(Graphics2D g2, int cx, int cy, Color color, String label) {
            int arm = 11;
            int rw = 14, rh = 7;
            g2.setColor(new Color(70, 70, 70));
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx - arm, cy - arm, cx + arm, cy + arm);
            g2.drawLine(cx + arm, cy - arm, cx - arm, cy + arm);
            int[][] rp = {
                {cx - arm - rw/2, cy - arm - rh/2},
                {cx + arm - rw/2, cy - arm - rh/2},
                {cx - arm - rw/2, cy + arm - rh/2},
                {cx + arm - rw/2, cy + arm - rh/2}
            };
            for (int[] r : rp) {
                g2.setColor(new Color(210, 210, 210, 210));
                g2.fillOval(r[0], r[1], rw, rh);
                g2.setColor(new Color(100, 100, 100));
                g2.setStroke(new BasicStroke(0.8f));
                g2.drawOval(r[0], r[1], rw, rh);
            }
            g2.setColor(color);
            g2.fillOval(cx - 8, cy - 8, 16, 16);
            g2.setColor(color.darker());
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(cx - 8, cy - 8, 16, 16);
            g2.setFont(getFont().deriveFont(Font.BOLD, 9f));
            FontMetrics fm = g2.getFontMetrics();
            int lw = fm.stringWidth(label);
            g2.setColor(new Color(30, 30, 30, 200));
            g2.fillRoundRect(cx - lw/2 - 2, cy + 10, lw + 4, 11, 4, 4);
            g2.setColor(Color.WHITE);
            g2.drawString(label, cx - lw/2, cy + 19);
        }
        private void drawFaultDroneIcon(Graphics2D g2, int cx, int cy, Color color, String label, DroneStateGui faultState) {
            long t = System.currentTimeMillis();
            float pulse = (float)(0.5 + 0.5 * Math.sin(t / 300.0));
            int glowAlpha = (int)(60 + 80 * pulse);
            int glowSize = (int)(22 + 6 * pulse);
            g2.setColor(new Color(255, 40, 40, glowAlpha));
            g2.fillOval(cx - glowSize, cy - glowSize, glowSize * 2, glowSize * 2);
            int arm = 11; int rw = 14, rh = 7;
            g2.setColor(new Color(100, 40, 40));
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(cx-arm, cy-arm, cx+arm, cy+arm);
            g2.drawLine(cx+arm, cy-arm, cx-arm, cy+arm);
            int[][] rp = {{cx-arm-rw/2,cy-arm-rh/2},{cx+arm-rw/2,cy-arm-rh/2},{cx-arm-rw/2,cy+arm-rh/2},{cx+arm-rw/2,cy+arm-rh/2}};
            for (int[] r : rp) {
                g2.setColor(new Color(200,180,180,210)); g2.fillOval(r[0],r[1],rw,rh);
                g2.setColor(new Color(150,80,80)); g2.setStroke(new BasicStroke(0.8f)); g2.drawOval(r[0],r[1],rw,rh);
            }
            g2.setColor(color); g2.fillOval(cx-8,cy-8,16,16);
            g2.setColor(color.darker()); g2.setStroke(new BasicStroke(1.5f)); g2.drawOval(cx-8,cy-8,16,16);
            if (faultState == DroneStateGui.FaultNozzle) {
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(cx-5, cy-5, cx+5, cy+5);
                g2.drawLine(cx+5, cy-5, cx-5, cy+5);
            } else {
                g2.setColor(new Color(255, 220, 40));
                int[] triX = {cx, cx-6, cx+6}; int[] triY = {cy-6, cy+4, cy+4};
                g2.fillPolygon(triX, triY, 3);
                g2.setColor(new Color(80, 60, 0));
                g2.setFont(getFont().deriveFont(Font.BOLD, 8f));
                g2.drawString("!", cx-2, cy+3);
            }
            g2.setFont(getFont().deriveFont(Font.BOLD, 9f));
            FontMetrics fm = g2.getFontMetrics();
            int lw = fm.stringWidth(label);
            g2.setColor(new Color(120, 20, 20, 220)); g2.fillRoundRect(cx-lw/2-2, cy+12, lw+4, 11, 4, 4);
            g2.setColor(new Color(255, 200, 200)); g2.drawString(label, cx-lw/2, cy+21);
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
                        setBackground(new Color(200, 120, 20, 50));
                    } else if (status.contains("dispatched")) {
                        setForeground(new Color(50, 100, 200));
                        setBackground(new Color(50, 100, 200, 50));
                    } else if (status.contains("pending")) {
                        setForeground(new Color(150, 150, 150));
                        setBackground(new Color(150, 150, 150, 50));
                    } else if (status.contains("partially")) {
                        setForeground(new Color(200, 150, 20));
                        setBackground(new Color(200, 150, 20, 50));
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
    public void logEvent(String message) {
        String ts = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());
        SwingUtilities.invokeLater(() -> logTableModel.addRow(new Object[]{ts, message}));
    }
    private void injectFault() {
        String selectedDrone = (String) faultDroneSelector.getSelectedItem();
        if (selectedDrone == null || selectedDrone.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No drone selected.", "Fault Injection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String faultType = (String) faultTypeSelector.getSelectedItem();
        String faultCode = faultType.contains("Stuck") ? "STUCK" : "NOZZLE";
        logEvent("FAULT INJECTED: " + faultCode + " on " + selectedDrone);
        if (scheduler != null) {
            try { scheduler.injectFault(selectedDrone, faultCode); }
            catch (Exception ex) { logEvent("ERROR injecting fault: " + ex.getMessage()); }
        }
    }
    static class FaultCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            if (value != null) {
                String fault = value.toString();
                if (fault.equals("STUCK")) {
                    c.setForeground(new Color(220, 120, 20)); c.setBackground(new Color(255, 245, 220));
                    setFont(getFont().deriveFont(Font.BOLD)); setText("\u26A0 STUCK");
                } else if (fault.equals("NOZZLE JAM")) {
                    c.setForeground(new Color(180, 20, 20)); c.setBackground(new Color(255, 220, 220));
                    setFont(getFont().deriveFont(Font.BOLD)); setText("\u2716 NOZZLE JAM");
                } else {
                    c.setForeground(new Color(100, 160, 100)); c.setBackground(Color.WHITE);
                    setFont(getFont().deriveFont(Font.PLAIN)); setText("\u2714 None");
                }
            }
            return c;
        }
    }

}
























