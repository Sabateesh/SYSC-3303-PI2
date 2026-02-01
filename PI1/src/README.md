# SYSC3303A — Iteration 1
Overview
-

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

