import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class FaultDetectionJUnitTest {

    private Scheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        scheduler = new Scheduler();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (scheduler != null) {
            DatagramSocket socket = getPrivateField(scheduler, "socket", DatagramSocket.class);
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    @Test
    void nozzleJam_isDetectedOnlyWhenDropping() throws Exception {
        String droneId = "Drone-0";

        Event assigned = new Event("14:03:15", 3, Event.EventType.FIRE_DETECTED, Event.Severity.HIGH, Event.FaultType.NONE);
        assigned.setWaterLeft(30f);
        Event reported = new Event("14:03:15", 3, Event.EventType.FIRE_DETECTED, Event.Severity.HIGH, Event.FaultType.NOZZLE_STUCK_OPEN);
        reported.setWaterLeft(30f);
        reported.deliverEvent(Event.State.PARTIAL_EXTINGUISHED);

        Map<String, Event> activeAssignments = getPrivateField(scheduler, "activeAssignments", Map.class);
        activeAssignments.put(droneId, assigned);

        Map<String, Scheduler.DroneStatus> statuses = getPrivateField(scheduler, "droneStatuses", Map.class);
        statuses.put(droneId, new Scheduler.DroneStatus(droneId, 50f, 3, 15f, 0, 1000, System.currentTimeMillis(), "droppingAgent", ""));

        Message partial = new Message(Message.Type.PARTIAL_DONE, reported, droneId);
        invokePrivate(scheduler, "handleMessage", new Class[]{Message.class}, partial);

        Set<String> failed = getPrivateField(scheduler, "failedDrones", Set.class);
        assertTrue(failed.contains(droneId), "Nozzle jam should fail the drone only in dropping state");

        assertFalse(activeAssignments.containsKey(droneId), "Faulted drone must be unassigned from current fire");

        List<Event> queuedEvents = getPrivateField(scheduler, "events", List.class);
        assertFalse(queuedEvents.isEmpty(), "Faulted fire should be re-queued for reassignment");
        assertEquals(Event.State.PENDING, queuedEvents.get(0).currentState(), "Reassigned fire must remain visible as pending");
        assertEquals(Event.FaultType.NONE, queuedEvents.get(0).getFaultType(), "Reassigned fire should clear fault injection metadata");
    }

    @Test
    void nozzleJam_isNotDetectedWhenDroneIsEnRoute() throws Exception {
        String droneId = "Drone-1";

        Event assigned = new Event("14:10:00", 7, Event.EventType.FIRE_DETECTED, Event.Severity.MODERATE, Event.FaultType.NONE);
        assigned.setWaterLeft(20f);
        Event reported = new Event("14:10:00", 7, Event.EventType.FIRE_DETECTED, Event.Severity.MODERATE, Event.FaultType.NOZZLE_STUCK_OPEN);
        reported.setWaterLeft(20f);
        reported.deliverEvent(Event.State.PARTIAL_EXTINGUISHED);

        Map<String, Event> activeAssignments = getPrivateField(scheduler, "activeAssignments", Map.class);
        activeAssignments.put(droneId, assigned);

        Map<String, Scheduler.DroneStatus> statuses = getPrivateField(scheduler, "droneStatuses", Map.class);
        statuses.put(droneId, new Scheduler.DroneStatus(droneId, 50f, 0, 15f, 7, 1200, System.currentTimeMillis(), "enRoute", ""));

        Message partial = new Message(Message.Type.PARTIAL_DONE, reported, droneId);
        invokePrivate(scheduler, "handleMessage", new Class[]{Message.class}, partial);

        Set<String> failed = getPrivateField(scheduler, "failedDrones", Set.class);
        assertFalse(failed.contains(droneId), "Nozzle jam must not be inferred while drone is en route");
        assertTrue(activeAssignments.containsKey(droneId), "Drone should remain assigned when not in dropping state");
    }

    @Test
    void stuckMidFlight_isDetectedByArrivalTimeout() throws Exception {
        String droneId = "Drone-2";

        Event assigned = new Event("15:20:30", 5, Event.EventType.FIRE_DETECTED, Event.Severity.HIGH, Event.FaultType.NONE);
        assigned.deliverEvent(Event.State.DISPATCHED);

        Map<String, Event> activeAssignments = getPrivateField(scheduler, "activeAssignments", Map.class);
        activeAssignments.put(droneId, assigned);

        Map<String, Scheduler.DroneStatus> statuses = getPrivateField(scheduler, "droneStatuses", Map.class);
        statuses.put(droneId, new Scheduler.DroneStatus(droneId, 48f, 0, 15f, 5, 1500, System.currentTimeMillis(), "enRoute", ""));

        Map<String, Object> expectedArrivals = getPrivateField(scheduler, "expectedArrivals", Map.class);
        Object expected = buildExpectedArrival(5, true, System.currentTimeMillis() - 1);
        expectedArrivals.put(droneId, expected);

        invokePrivate(scheduler, "checkArrivalTimeouts", new Class[]{});

        Set<String> failed = getPrivateField(scheduler, "failedDrones", Set.class);
        assertTrue(failed.contains(droneId), "Arrival timeout should mark drone as stuck fault");

        assertFalse(activeAssignments.containsKey(droneId), "Timed-out drone must be unassigned from active fire");

        List<Event> queuedEvents = getPrivateField(scheduler, "events", List.class);
        assertFalse(queuedEvents.isEmpty(), "Timed-out fire should be re-queued for another drone");
        assertEquals(Event.State.PENDING, queuedEvents.get(0).currentState(), "Re-queued event should be pending");
    }

    private Object buildExpectedArrival(int zoneId, boolean armed, long deadlineMs) throws Exception {
        Class<?> clazz = Class.forName("Scheduler$ExpectedArrival");
        Constructor<?> ctor = clazz.getDeclaredConstructor(int.class);
        ctor.setAccessible(true);
        Object expected = ctor.newInstance(zoneId);

        Field armedField = clazz.getDeclaredField("armed");
        armedField.setAccessible(true);
        armedField.setBoolean(expected, armed);

        Field deadlineField = clazz.getDeclaredField("deadlineMs");
        deadlineField.setAccessible(true);
        deadlineField.setLong(expected, deadlineMs);

        return expected;
    }

    @SuppressWarnings("unchecked")
    private <T> T getPrivateField(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private Object invokePrivate(Object target, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}

