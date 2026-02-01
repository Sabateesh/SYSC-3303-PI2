# SYSC3303A — Iteration 1
Overview
-

<<<<<<< HEAD
Included Files
- `src/Main.java` (main program)
- `src/Drone.java` (represents a single drone which is assigned an event)
- `src/DroneSubsystem.java` (the drone subsystem with multiple drones and which assigned tasks to drones)
- `src/Event.java` (represents an event, read from the csv file)
- `src/FireIncidentSubsystem.java` (the fire incident subsystem)
- `src/FireIncidentSubsystemGUI.java` (gu)
- `src/FireIncidentSubsystemJUnitTest.java`
- `src/Message.java`
- `src/Scheduler.java`
- `src/SchedulerServer.java`

=======
>>>>>>> 49a0166a20b3c6887d7c56eda685771c2235b8b9
What’s Implemented
-
FireIncidentSubsystem.java-Sabateesh
-
- Reads event input file
- Parses each line into an Event object (time, zone ID, event type, severity)
- Sends events to Scheduler using sendEvent(Event e)
- Waits for confirmations using getConfirmation()

GUI Scaffold (Swing)- Sabateesh
-
- Static placeholder GUI showing zones/drones/events sections

JUnit 5 Tests -Sabateesh
-
- Tests parsing correctness
- Tests communication round-trip with a simulated subsystem thread (run_sendsEvents_andReceivesConfirmations_roundTrip

