package ru.job4j.ex;

public class PasswordValidator {
    private static final String[] FORBIDDEN = {"qwerty", "12345", "password", "admin", "user"};
        /**
         * Метод проверят пароль по правилам:
         *  1. Если password null, то выбросить исключение "Password can't be null";
         *  2. Длина пароля находится в диапазоне [8, 32],
         *     если нет то "Password should be length [8, 32]";
         *  3. Пароль содержит хотя бы один символ в верхнем регистре,
         *     если нет то "Password should contain at least one uppercase letter");
         *  4. Пароль содержит хотя бы один символ в нижнем регистре,
         *     если нет то "Password should contain at least one lowercase letter";
         *  5. Пароль содержит хотя бы одну цифру,
         *     если нет то"Password should contain at least one figure");
         *  6. Пароль содержит хотя бы один спец. символ (не цифра и не буква),
         *     если нет то "Password should contain at least one special symbol");
         *  7. Пароль не содержит подстрок без учета регистра: qwerty, 12345, password, admin, user.
         *     Без учета регистра, значит что, например, ни qwerty ни QWERTY ни qwErty и т.п.
         *     если да, то "Password shouldn't contain substrings: qwerty, 12345, password, admin, user".
         * @param password Пароль
         * @return Вернет пароль или выбросит исключение.
         */
    public static String validate(String password) {
        if ( password == null ) {
            throw new IllegalArgumentException( "Password can't be null" );
        }


        boolean hasDigit = false;
        boolean hasSpecial = false;

        if (password.length() < 8 || password.length() > 32  ){
            throw  new IllegalArgumentException("Password should be length [8, 32]");
        }


        boolean hasUpCase = false;
        for (char symbol : password.toCharArray()) {
                if (Character.isUpperCase(symbol)){
                    hasUpCase = true;
                    break;
                }
        }
        if (!hasUpCase){
                throw new IllegalArgumentException("Password should contain at least one uppercase letter");
        }

        boolean hasLowCase = false;
        for (char symbol : password.toCharArray()) {
            if (Character.isLowerCase(symbol)){
                hasLowCase = true;
                break;
            }
        }
        if (!hasLowCase) {
            throw new IllegalArgumentException("Password should contain at least one lowercase letter");
        }


        for (char symbol : password.toCharArray()) {
            if (Character.isDigit(symbol)){
                hasDigit = true;
                break;
            }
        }
        if (!hasDigit){
            throw new IllegalArgumentException("Password should contain at least one figure");
        }

        for (char symbol : password.toCharArray()) {
            if (!Character.isDigit(symbol) && !Character.isLetter(symbol) ){
                hasSpecial = true;
                break;
            }
        }
        if (!hasSpecial){
            throw new IllegalArgumentException("Password should contain at least one special symbol");
        }


        for (String forbiten : FORBIDDEN){
            if (password.toLowerCase().contains(forbiten.toLowerCase())){
                throw  new IllegalArgumentException( "Password shouldn't contain substrings: qwerty, 12345, password, admin, user" );
            }
        }

        return password;
    }

}
