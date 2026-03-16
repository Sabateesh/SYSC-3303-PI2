import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
class DroneStateMachineJUnitTest {
    private DroneStateMachine fsm;
    @BeforeEach
    void setUp() {
        fsm = new DroneStateMachine("TestDrone");
    }
    // initial state
    @Test
    void initialState_isIdle() {
        assertEquals(DroneState.idle, fsm.getState());
    }
    @Test
    void idle_fireAssigned_transitionsToEnRoute() {
        fsm.handleEvent(DroneEvent.fireAssigned);
        assertEquals(DroneState.enRoute, fsm.getState());
    }
    @Test
    void idle_ignoresArrivedToFire() {
        fsm.handleEvent(DroneEvent.arrivedToFire);
        assertEquals(DroneState.idle, fsm.getState());
    }
     @Test
    void idle_ignoresJobFinished() {
        fsm.handleEvent(DroneEvent.jobFinished);
        assertEquals(DroneState.idle, fsm.getState());
    }
    @Test
    void idle_ignoresNeedRefill() {
        fsm.handleEvent(DroneEvent.needRefill);
        assertEquals(DroneState.idle, fsm.getState());
    }
    @Test
    void enRoute_arrivedToFire_transitionsToDroppingAgent() {
        fsm.handleEvent(DroneEvent.fireAssigned);
        fsm.handleEvent(DroneEvent.arrivedToFire);
        assertEquals(DroneState.droppingAgent, fsm.getState());
    }
    @Test
    void enRoute_ignoresFireAssigned() {
        fsm.handleEvent(DroneEvent.fireAssigned);
        fsm.handleEvent(DroneEvent.fireAssigned);
        assertEquals(DroneState.enRoute, fsm.getState());
    }
    @Test
    void droppingAgent_jobFinished_transitionsToReturnOrigin() {
        fsm.handleEvent(DroneEvent.fireAssigned);
        fsm.handleEvent(DroneEvent.arrivedToFire);
        fsm.handleEvent(DroneEvent.jobFinished);
        assertEquals(DroneState.returnOrigin, fsm.getState());
    }
    @Test
    void droppingAgent_needRefill_transitionsToReturnForRefill() {
        fsm.handleEvent(DroneEvent.fireAssigned);
        fsm.handleEvent(DroneEvent.arrivedToFire);
        fsm.handleEvent(DroneEvent.needRefill);
        assertEquals(DroneState.returnForRefill, fsm.getState());
    }
    @Test
    void droppingAgent_ignoresFireAssigned() {
        fsm.handleEvent(DroneEvent.fireAssigned);
        fsm.handleEvent(DroneEvent.arrivedToFire);
        fsm.handleEvent(DroneEvent.fireAssigned);
        assertEquals(DroneState.droppingAgent, fsm.getState());
    }
    @Test
    void returnForRefill_fireAssigned_transitionsToEnRoute() {
        fsm.handleEvent(DroneEvent.fireAssigned);
        fsm.handleEvent(DroneEvent.arrivedToFire);
        fsm.handleEvent(DroneEvent.needRefill);
        fsm.handleEvent(DroneEvent.fireAssigned);
        assertEquals(DroneState.enRoute, fsm.getState());
    }
    @Test
    void returnForRefill_ignoresJobFinished() {
        fsm.handleEvent(DroneEvent.fireAssigned);
        fsm.handleEvent(DroneEvent.arrivedToFire);
        fsm.handleEvent(DroneEvent.needRefill);
        fsm.handleEvent(DroneEvent.jobFinished);
        assertEquals(DroneState.returnForRefill, fsm.getState());
    }
    @Test
    void returnOrigin_arrivedToOrigin_transitionsToIdle() {
        fsm.handleEvent(DroneEvent.fireAssigned);
        fsm.handleEvent(DroneEvent.arrivedToFire);
        fsm.handleEvent(DroneEvent.jobFinished);
        fsm.handleEvent(DroneEvent.arrivedToOrigin);
        assertEquals(DroneState.idle, fsm.getState());
    }
    @Test
    void returnOrigin_ignoresFireAssigned() {
        fsm.handleEvent(DroneEvent.fireAssigned);
        fsm.handleEvent(DroneEvent.arrivedToFire);
        fsm.handleEvent(DroneEvent.jobFinished);
        fsm.handleEvent(DroneEvent.fireAssigned);
        assertEquals(DroneState.returnOrigin, fsm.getState());
    }  
    @Test
    void fullCycle_fireCompleted() {
        assertEquals(DroneState.idle, fsm.getState());
        fsm.handleEvent(DroneEvent.fireAssigned);
        assertEquals(DroneState.enRoute, fsm.getState());
        fsm.handleEvent(DroneEvent.arrivedToFire);
        assertEquals(DroneState.droppingAgent, fsm.getState());
        fsm.handleEvent(DroneEvent.jobFinished);
        assertEquals(DroneState.returnOrigin, fsm.getState());
        fsm.handleEvent(DroneEvent.arrivedToOrigin);
        assertEquals(DroneState.idle, fsm.getState());
    }
    @Test
    void fullCycle_withRefill() {
        assertEquals(DroneState.idle, fsm.getState());
        fsm.handleEvent(DroneEvent.fireAssigned);
        assertEquals(DroneState.enRoute, fsm.getState());
        fsm.handleEvent(DroneEvent.arrivedToFire);
        assertEquals(DroneState.droppingAgent, fsm.getState());
        fsm.handleEvent(DroneEvent.needRefill);
        assertEquals(DroneState.returnForRefill, fsm.getState());
        fsm.handleEvent(DroneEvent.fireAssigned);
        assertEquals(DroneState.enRoute, fsm.getState());
        fsm.handleEvent(DroneEvent.arrivedToFire);
        assertEquals(DroneState.droppingAgent, fsm.getState());
        fsm.handleEvent(DroneEvent.jobFinished);
        assertEquals(DroneState.returnOrigin, fsm.getState());
        fsm.handleEvent(DroneEvent.arrivedToOrigin);
        assertEquals(DroneState.idle, fsm.getState());
    }
       @Test
        void multipleFires_backToBack() {
        // fire 1
        fsm.handleEvent(DroneEvent.fireAssigned);
        fsm.handleEvent(DroneEvent.arrivedToFire);
        fsm.handleEvent(DroneEvent.jobFinished);
        fsm.handleEvent(DroneEvent.arrivedToOrigin);
        assertEquals(DroneState.idle, fsm.getState());
        // fire 2
        fsm.handleEvent(DroneEvent.fireAssigned);
        fsm.handleEvent(DroneEvent.arrivedToFire);
        fsm.handleEvent(DroneEvent.needRefill);
        fsm.handleEvent(DroneEvent.fireAssigned);
        fsm.handleEvent(DroneEvent.arrivedToFire);
        fsm.handleEvent(DroneEvent.jobFinished);
        fsm.handleEvent(DroneEvent.arrivedToOrigin);
        assertEquals(DroneState.idle, fsm.getState());
    }
}



