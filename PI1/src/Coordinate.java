public class Coordinate {
    private final int x;
    private final int y;

    //constr for coord
    public Coordinate(int x, int y){
        this.x =x;
        this.y=y;
    }
    //getters
    public int getX(){
        return x;
    }
    public int getY(){
        return y;
    }
    //calculate euclidean distance to another coordinate
    public double distanceTo(Coordinate other){
        int dx = this.x - other.x;
        int dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
    //parse coord from string format
    public static Coordinate parseCoordinate(String coordinateString){
        try{
            String cleaned = coordinateString.trim().replaceAll("[()]", "");
            String[] parts = cleaned.split("[,;]");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid coordinate format: " + coordinateString);
            }
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            return new Coordinate(x, y);
        } catch(NumberFormatException e){
            throw new IllegalArgumentException("invalid coordiante format" + coordinateString, e);
        }
    }
    @Override
    public String toString(){
        return String.format("(%d, %d)", x, y);
    }
    @Override
    public boolean equals(Object obj){
        if(this==obj) return true;
        if(obj==null||getClass()!=obj.getClass()) return false;
        Coordinate that = (Coordinate) obj;
        return x == that.x &&y == that.y;
    }
    @Override
    public int hashCode(){
        return 31 * x + y;
    }

}
