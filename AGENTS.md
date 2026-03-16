# AI Coding Agent Guidelines for SYSC3303 Fire Drone Simulation

## Architecture Overview
This is a multi-threaded Java simulation of a drone-based fire response system. Key components:
- **FireIncidentSubsystem**: Loads fire events from CSV, sends them to scheduler with timed delays
- **Scheduler**: Manages event queues and confirmations using synchronized LinkedList queues
- **DroneSubsystem**: Coordinates 3 drones, each running in its own thread
- **SchedulerGUI**: Swing-based GUI for visualization
- **Drone**: Individual drone logic with state machine, water/battery management

Communication uses shared `Queue<Event>` and `Queue<String>` between subsystems. All components run as separate threads started from `Main.java`.

## Key Patterns & Conventions
- **State Machines**: Drones use `DroneStateMachine` with states (idle, enRoute, droppingAgent, returnForRefill, returnOrigin) and events (fireAssigned, arrivedToFire, etc.)
- **Simulation Constants**: 
  - `Scheduler.simulationSpeed = 10` (multiplier for delays; 1000 = real-time)
  - Drone constants: `DROP_RATE = 2` L/s, `TANK_SIZE = 15` L, `BATTERY_SIZE = 50` min, `DRONE_SPEED = 120` units/min
- **CSV Parsing**: Events and zones loaded with header skip, comma/whitespace split; see `Event.parseEventLine()` and `Zone.loadFromCSV()`
- **Threading**: Each subsystem implements `Runnable`, uses `Thread.sleep()` for delays, interrupts for shutdown
- **GUI Integration**: Drones call `gui.paintDrone(this)` and `gui.paintEvent(event)` during state transitions

## Critical Workflows
- **Build & Run**: Open in IntelliJ IDEA, run `src/Main.java`. Paths are relative to project root (e.g., `SYSC3303-Project-Group5/Sample_event_file.csv`)
- **Testing**: JUnit tests in `DroneStateMachineJUnitTest.java` and `FireIncidentSubsystemJUnitTest.java`
- **Debugging**: Console logs prefixed with `[ThreadName]`, use thread dumps for concurrency issues

## Integration Points
- **Data Sources**: `Sample_event_file.csv` (time, zoneId, type, severity), `sample_zone_file.csv` (id, x1,y1,x2,y2)
- **Cross-Component Calls**: Scheduler queues accessed via `sendEvent()`, `requestTask()`, `reportDone()`, `getConfirmation()`
- **Error Handling**: Exceptions logged to stderr, invalid CSV lines skipped with warnings

Reference: `src/Main.java` for startup, `src/Drone.java` for drone logic, `src/Scheduler.java` for queue management</content>
<parameter name="filePath">C:\Users\faraa\OneDrive\Desktop\SYSC-3303-PI1\AGENTS.md
