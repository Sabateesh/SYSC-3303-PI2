import java.net.DatagramSocket;


enum DroneEvent{
    fireAssigned,
    arrivedToFire,
    needMoreWater,
    jobFinished,
    arrivedToOrigin,
    error
}

enum DroneState{
    idle,
    enRoute,
    droppingAgent,
    refilling,
    returnOrigin
}

class DroneContext{
    final String droneName;
    final DatagramSocket socket;

    DroneContext(String droneName, DatagramSocket socket){
        this.droneName = droneName;
        this.socket = socket;
    }

    void log(String msg){
        System.out.println(msg);
    }
}
public class DroneStateMachine {
    private DroneState state = DroneState.idle;
    private final DroneContext ctx;

    public DroneStateMachine(DroneContext ctx){
        this.ctx = ctx;
    }

    public synchronized DroneState getState(){
        return state;
    }

    private void transitionTo(DroneState next, DroneEvent cause){
        ctx.log("[FSM] " + state + " --(" + cause + ")--> " + next);
        state = next;
    }

    public synchronized void handleEvent(DroneEvent ev, String payload){
        switch(state){
            case idle:
                if (ev == DroneEvent.fireAssigned){
                    transitionTo(DroneState.enRoute, ev);
                } else{
                    ctx.log("[FSM] Ignored " + ev + " (state=IDLE)");
                }
                break;

            case enRoute:
                if (ev == DroneEvent.arrivedToFire) {
                    transitionTo(DroneState.droppingAgent, ev);
                }
                else if (ev == DroneEvent.jobFinished){
                    transitionTo(DroneState.returnOrigin, ev);
                }
                else{
                    ctx.log("[FSM] Ignored " + ev + " (state=Dispatched)");
                }
                break;

            case droppingAgent:
                if (ev == DroneEvent.needMoreWater){
                    transitionTo(DroneState.returnOrigin, ev);
                }
                else if (ev == DroneEvent.jobFinished){
                    transitionTo(DroneState.returnOrigin, ev);
                }
                else{
                    ctx.log("[FSM] Ignored " + ev + " (state=Refilling)");
                }
                break;

            case refilling:
                if (ev == DroneEvent.fireAssigned){
                    transitionTo(DroneState.enRoute, ev);
                }
                else if (ev == DroneEvent.jobFinished){
                    transitionTo(DroneState.idle, ev);
                }
                else{
                    ctx.log("[FSM] Ignored " + ev + " (state=Refilling)");
                }
                break;

            case returnOrigin:
                if (ev == DroneEvent.arrivedToOrigin){
                    transitionTo(DroneState.idle, ev);
                }
                else{
                    ctx.log("[FSM] Ignored " + ev + " (state=Refilling)");
                }
                break;
        }
    }
}
