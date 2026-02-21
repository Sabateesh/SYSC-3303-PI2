enum DroneEvent{
    fireAssigned,
    arrivedToFire,
    needRefill,
    jobFinished,
    arrivedToOrigin,
    error
}

enum DroneState{
    idle,
    enRoute,
    droppingAgent,
    returnForRefill,
    returnOrigin
}

class DroneContext{
    final String droneName;

    DroneContext(String droneName){
        this.droneName = droneName;
    }

    void log(String msg){
        System.out.println(msg);
    }
}
public class DroneStateMachine {
    private DroneState state = DroneState.idle;
    private final DroneContext ctx;

    public DroneStateMachine(String droneName){
        this.ctx = new DroneContext(droneName);
    }

    public synchronized DroneState getState(){
        return state;
    }

    private void transitionTo(DroneState next, DroneEvent cause){
        ctx.log("[FSM] " + state + " --(" + cause + ")--> " + next);
        state = next;
    }

    public synchronized void handleEvent(DroneEvent ev){
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
                else if (ev == DroneEvent.jobFinished){ //not used?
                    transitionTo(DroneState.returnOrigin, ev);
                }
                else{
                    ctx.log("[FSM] Ignored " + ev + " (state=Dispatched)");
                }
                break;

            case droppingAgent:
                if (ev == DroneEvent.needRefill){
                    transitionTo(DroneState.returnForRefill, ev);
                }
                else if (ev == DroneEvent.jobFinished){
                    transitionTo(DroneState.returnOrigin, ev);
                }
                else{
                    ctx.log("[FSM] Ignored " + ev + " (state=Refilling)");
                }
                break;

            case returnForRefill:
                if (ev == DroneEvent.fireAssigned){
                    transitionTo(DroneState.enRoute, ev);
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
