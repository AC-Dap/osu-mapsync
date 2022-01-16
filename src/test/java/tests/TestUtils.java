package tests;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestUtils {

    public static <T> void assertArrayListEquals(ArrayList<T> a, ArrayList<T> b) {
        assertEquals(a.size(), b.size(), "Arraylist sizes are different.");
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i), b.get(i), "Element " + i + " differs.");
        }
    }
}
