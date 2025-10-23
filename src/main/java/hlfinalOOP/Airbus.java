package hlfinalOOP;

public final class Airbus extends Aircraft{
    private static final int COUNT_ENGINE = 2;
    private String name;

    private Airbus(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void printModel() {
        System.out.println("Модель самолета: " + name);
    }

    public void printCountEngine() {
        int count_en = this.name.equals("A380") ? 4 : 2;
            System.out.println("Количество двигателей равно: " + count_en);
    }

    @Override
    public String toString() {
        return "Airbus{"
                + "name='" + name + '\''
                + '}';
    }

}
