import org.acdap.osusynchro.FileManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.acdap.osusynchro.FileManager.Beatmap;

class FileManagerTest {

    @Test
    void getAllBeatmaps() {
        Path testDir = Paths.get("./src/test/java/testsongs");
        ArrayList<Beatmap> exp = new ArrayList<>(Arrays.asList(
                new Beatmap(22374, "name - map"),
                new Beatmap(33119, "subdirectory"),
                new Beatmap(33842, "duplicate - name"),
                new Beatmap(37292, "abc 123 - a)141!"),
                new Beatmap(43701, "duplicate - name")
        ));

        assertArrayListEquals(exp, FileManager.getAllBeatmaps(testDir));
    }

    @Test
    void getMissingLocal() {
        // Matching IDs
        ArrayList<Beatmap> l = new ArrayList<>(Arrays.asList(
                new Beatmap(22374, "name - map"),
                new Beatmap(33119, "subdirectory"),
                new Beatmap(33842, "duplicate - name"),
                new Beatmap(37292, "abc 123 - a)141!"),
                new Beatmap(43701, "duplicate - name")
        ));
        ArrayList<Beatmap> r = new ArrayList<>(Arrays.asList(
                new Beatmap(22374, "name - map"),
                new Beatmap(33119, "subdirectory"),
                new Beatmap(33842, "duplicate - name"),
                new Beatmap(37292, "abc 123 - a)141!"),
                new Beatmap(43701, "duplicate - name")
        ));
        ArrayList<Beatmap> exp = new ArrayList<>();

        assertArrayListEquals(exp, FileManager.getMissingLocal(l, r));

        // Missing from local
        l = new ArrayList<>(Arrays.asList(
                new Beatmap(33119, "subdirectory"),
                new Beatmap(37292, "abc 123 - a)141!"),
                new Beatmap(43701, "duplicate - name"),
                new Beatmap(999999, "big")
        ));
        r = new ArrayList<>(Arrays.asList(
                new Beatmap(22374, "name - map"),
                new Beatmap(33119, "subdirectory"),
                new Beatmap(33842, "duplicate - name"),
                new Beatmap(37292, "abc 123 - a)141!"),
                new Beatmap(43701, "duplicate - name")
        ));
        exp = new ArrayList<>(Arrays.asList(
                new Beatmap(22374, "name - map"),
                new Beatmap(33842, "duplicate - name")
        ));

        assertArrayListEquals(exp, FileManager.getMissingLocal(l, r));

        // Missing from remote
        l = new ArrayList<>(Arrays.asList(
                new Beatmap(22374, "name - map"),
                new Beatmap(33119, "subdirectory"),
                new Beatmap(33842, "duplicate - name"),
                new Beatmap(37292, "abc 123 - a)141!"),
                new Beatmap(43701, "duplicate - name")
        ));
        r = new ArrayList<>(Arrays.asList(
                new Beatmap(22374, "name - map"),
                new Beatmap(33119, "subdirectory"),
                new Beatmap(37292, "abc 123 - a)141!")
        ));
        exp = new ArrayList<>();

        assertArrayListEquals(exp, FileManager.getMissingLocal(l, r));

    }

    @Test
    void zipBeatmaps() throws IOException {
        Path testDir = Paths.get("./src/test/java/testsongs");
        ArrayList<Beatmap> zipIds = new ArrayList<>(Arrays.asList(
                new Beatmap(33119, "subdirectory"),
                new Beatmap(33842, "duplicate - name"),
                new Beatmap(43701, "duplicate - name")
        ));

        Path zipPath = FileManager.zipBeatmaps(testDir, zipIds);
        assertTrue(Files.exists(zipPath));
        assertTrue(Files.size(zipPath) > 0);
    }

    private <T> void assertArrayListEquals(ArrayList<T> a, ArrayList<T> b) {
        assertEquals(a.size(), b.size(), "Arraylist sizes are different.");
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i), b.get(i), "Element " + i + " differs.");
        }
    }
}