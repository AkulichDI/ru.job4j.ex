package homeLsTryCatch;

public class CarInvalidException extends CarNotFoundException{
    public CarInvalidException (String message){
        super(message);
    }
}
