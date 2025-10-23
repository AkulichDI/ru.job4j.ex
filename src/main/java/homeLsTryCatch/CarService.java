package homeLsTryCatch;

public class CarService {

    public static Car findCar (Car[] cars, String model) throws CarNotFoundException {
        for (Car car : cars){
            if (car.getModel().equals(model)){
                return car;
            }
        }
        throw new CarNotFoundException("Нет такой машины");
    }
    public static boolean check (Car car) throws CarInvalidException {
        if (!car.isReady()){
            throw new CarInvalidException("Машина не прошла проверку");
        }if (car.getModel().length() < 2){
            throw new CarInvalidException("Наименование модели меньше 2 символов");
        }
        return true;

    }

    public static void main(String[] args) {

        Car[] cars = new Car[]{new Car("Tesla Y", true)};


    }

}
