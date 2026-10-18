package notes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Starting point of the program. Finds the library folder, loads every
 * collection in it, then hands control to the Menu.
 */
public class NoteApp {

    /**
     * Loads the library, lists any files that couldn't be loaded, then starts the
     * menu. Load problems are shown as warnings instead of stopping the program,
     * so one damaged file doesn't lock the user out of all their other notes.
     */
    public static void main(String[] args) {
        // The library lives in the user's Documents folder, e.g.
        // C:\Users\name\Documents\NoteApp on Windows. user.home is used instead of a
        // fixed path because it finds the current user's home folder on any computer.
        Path libraryFolder = Path.of(System.getProperty("user.home"), "Documents", "NoteApp");

        Library library = new Library(libraryFolder);

        try {
            List<String> problems = library.loadCollections();
            if (!problems.isEmpty()) {
                System.out.println("Some files couldn't be loaded:");
                for (String problem : problems) {
                    System.out.println("  - " + problem);
                }
                System.out.println();
            }
        } catch (IOException e) {
            // Without a usable library folder there's nothing the program can do, so explain and stop
            System.out.println("Error: couldn't open the library folder at " + libraryFolder);
            System.out.println(e.getMessage());
            return;
        }

        new Menu(library).run();
    }
}
