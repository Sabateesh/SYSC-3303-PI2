import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

//GUI Scaffold
public class FireIncidentSubsystemGUI extends JFrame {
    public FireIncidentSubsystemGUI() {
        super("PI1 - Fire Incident System GUI (Scaffold)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        ZonesPanel zonesPanel = new ZonesPanel(sampleZones());
        zonesPanel.setBorder(new TitledBorder("Zones (Placeholder)"));
        JPanel rightPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        JPanel dronesPanel = buildDronesPanel();
        dronesPanel.setBorder(new TitledBorder("Drones (Placeholder)"));
        JPanel eventsPanel = buildEventsPanel();
        eventsPanel.setBorder(new TitledBorder("Events (Placeholder)"));
        rightPanel.add(dronesPanel);
        rightPanel.add(eventsPanel);
        add(zonesPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
        JLabel status = new JLabel("Status: GUI Scaffold (static) — not connected to CSV/threads in PI1");
        status.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        add(status, BorderLayout.SOUTH);
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
            g2.drawString("PI1 placeholder: zones are hardcoded; later read from zone CSV.", 10, getHeight() - 12);
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
