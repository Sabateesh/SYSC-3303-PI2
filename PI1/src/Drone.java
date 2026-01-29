

public class Drone {
    private boolean in_use;
    private int waterReservoir;
    private double x_coordinate;
    private double y_coordinate;
    private int fireID;

    public Drone(){
        in_use = true;
        waterReservoir = 15;
        x_coordinate = 0.0;
        y_coordinate = 0.0;
    }

    public void moveDestination(int x_destionation, int y_destination){
        x_coordinate = x_destionation;
        y_coordinate = y_destination;
    }

    public boolean enoughWaterLeft(int waterNeeded){
        return waterReservoir >= waterNeeded;
    }

    public void assignDrone(int fireID){
        this.fireID = fireID;
    }
}
