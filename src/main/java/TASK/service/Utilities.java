package TASK.service;

public class Utilities {

    private static final int MIN_CHAR = 2;
    private static final int MAX_CHAR = 17;

    public static boolean isIdValid(String id) {
        int length = id.length();
        return (id.matches("[a-zA-Z0-9]+") && length > MIN_CHAR && length < MAX_CHAR);
    }
}