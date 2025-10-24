package hlfinalOOP;

public class Airport {
    public static void main(String[] args) {
        final Airbus airbus = new Airbus("A320");
        System.out.println(airbus);
        airbus.toString(); // Можно удалить, если toString не печатает ничего
        airbus.printModel();
        airbus.printCountEngine();
        System.out.println(airbus);
        airbus.setName("A380");
        airbus.printModel();
        airbus.printCountEngine();
        System.out.println(airbus);
    }

}
