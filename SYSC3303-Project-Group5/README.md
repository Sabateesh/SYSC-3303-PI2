# SYSC3303A — Iteration 4, Group 5
## Included Files
- `README.md` (contains info and instructions)
- `Sample_event_file.csv` (contains sample events)
- `sample_zone_file.csv` (contains sample events)
- `P4StateMachine_Drone.png` (Drone state machine)
- `src/Main.java` (main program)
- `src/Drone.java` (represents a single drone which is assigned an event)
- `src/DroneSubsystem.java` (the drone subsystem with multiple drones and which assigned tasks to drones)
- `src/DroneStateMachine.java` (the state machine for the drones)
- `src/DroneStateMachineJUnitTest.java` (tests for the state machine for the drones)
- `src/Event.java` (represents an event, read from the csv file)
- `src/FireIncidentSubsystem.java` (the fire incident subsystem)
- `src/SchedulerGUI.java` (the gui for the user to interact with the system)
- `src/FireIncidentSubsystemJUnitTest.java` (test class for fire incident subsystem)
- `src/Message.java`
- `src/Scheduler.java` (scheduler for the tasks)
- `src/UdpCommunicationJUnitTest.java` (tests for UDP communication)
- `src/Zone.java` (zone class to keep track of individual zones)
- `src/PacketLossDetectionJUnitTest.java` (tests for packet losses)

## Detailed Instructions
1. Open the project in IntelliJ Idea
2. Run the `Main.java` file. This starts the Scheduler + GUI
3. Run the `DroneSubsystem.java` file. This starts the Drone Subsystem
4. Run the `FireIncidentSubsystem.java` file. This starts the Fire Incident Subsystem

## Responsibilities
- Implemented drone faults and drone fault detection, crc checksum redundancy in UDP messages, marking drones as faulty when detected (Faraaz, 101298165)
- Diagram, updated the drone state machine (Aymen, 101326354)
- Update drone subsystem UDP communications, packet handling and registration with scheduler (Aymen, 101326354)
- Drone fault handling, returning faulty drones to base station, redistributing tasks when drone failure occurs, etc (Ibrahim)
- GUI  changes and , updated GUI to show drone faults handling (Sabateesh, 101259947)
