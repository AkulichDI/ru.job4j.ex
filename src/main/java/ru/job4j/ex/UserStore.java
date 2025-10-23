package ru.job4j.ex;

public class UserStore {
    public static User findUser(User[] users, String login) throws UserNotFoundException {

            for (User user : users) {
                if (user.getUsername().equals(login)){
                    return user;
                }
            }
            throw new UserNotFoundException("Пользователь не найден");

    }

    public static boolean validate(User user) throws UserInvalidException {
        if (!user.isValid()) {
            throw new UserInvalidException("Valid is false");
        }
        if (user.getUsername().length() < 3) {
            throw new UserInvalidException("Username too short");
        }
        return true;

    }

    public static void main(String[] args) {
        User[] users = {
                new User("Pe", true)
        };
        try {
            User user = findUser(users, "Petr Arsentev");
            if (validate(user)) {
                System.out.println("This user has an access");
            }
        } catch (UserInvalidException e) {
            System.out.println("Пользователь не валиден");
        } catch (UserNotFoundException e) {
            System.out.println("Пользователь не найден");
        }
    }


}
