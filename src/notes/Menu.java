package notes;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * The console interface. Everything the user sees and types goes through this class;
 * the other classes only store and change data. There are three levels, each its own loop:
 * main menu (collections) -> collection menu (items) -> note menu (one note and its links).
 * Choosing Back returns to the level above.
 * Every change is saved to disk as soon as it's made, so there's never unsaved
 * work to lose when quitting.
 */
public class Menu {

    /**
     * Reads everything the user types. There's only one because two Scanners on
     * System.in would each buffer input and take lines meant for the other.
     */
    private final Scanner scanner;

    /** The collections the menus show and change. */
    private final Library library;

    /** Brings outside files into a collection. */
    private final FileImporter importer;

    /** Zips collections to share and unzips shared ones. */
    private final CollectionArchiver archiver;

    /**
     * Takes a Library that's already loaded, so Menu only handles input and
     * output. NoteApp decides where the library lives and reports load problems.
     */
    public Menu(Library library) {
        this.library = library;
        this.scanner = new Scanner(System.in); // The only Scanner in the program; never close it
        this.importer = new FileImporter();
        this.archiver = new CollectionArchiver();
    }

    /**
     * Starts the program's menus. Returns when the user chooses Quit.
     * Each menu already handles the errors it expects (bad input, missing files). The catches here are a
     * safety net: if the input stream ends (Ctrl+Z on Windows, Ctrl+D on Mac/Linux) or something unexpected
     * goes wrong, the program says so and exits cleanly instead of printing a stack trace. Nothing is lost,
     * because every change is saved to disk as soon as it's made.
     */
    public void run() {
        System.out.println("Welcome to the NoteApp notetaking system!");
        try {
            showMainMenu();
        } catch (NoSuchElementException e) {
            System.out.println();
            System.out.println("Input ended, so NoteApp is closing.");
        } catch (RuntimeException e) {
            System.out.println();
            System.out.println("Unexpected error: " + e.getMessage());
            System.out.println("NoteApp is closing. Your saved notes are not affected.");
        }
        System.out.println("Goodbye!");
    }

    // ---------------------------------------------------------------
    // Menu levels
    // ---------------------------------------------------------------

    /**
     * Lists every collection by number and loops until the user quits.
     * Options: open a collection, create one, delete one (after confirming), unzip a shared one, quit.
     */
    private void showMainMenu() {
        while (true) {
            List<NoteCollection> collections = library.listCollections();

            printHeader("Library");
            printNumbered(collectionNames(collections), "  (No collections yet. Choose C to create one.)");
            System.out.println();
            System.out.println("[O]pen  [C]reate  [D]elete  [U]nzip a shared collection  [Q]uit");
            String choice = prompt("> ").toLowerCase();

            //Each action is wrapped so one mistake (a bad name, a missing file) prints an error and returns to this menu
            try {
                switch (choice) {
                    case "o" -> {
                        int index = promptNumber("Collection number: ", collections.size());
                        if (index != -1) {
                            showCollectionMenu(collections.get(index));
                        }
                    }
                    case "c" -> {
                        NoteCollection created = library.createCollection(prompt("Name for the new collection: "));
                        System.out.println("Created \"" + created.getName() + "\".");
                        showCollectionMenu(created);
                    }
                    case "d" -> {
                        int index = promptNumber("Collection number to delete: ", collections.size());
                        if (index != -1) {
                            NoteCollection target = collections.get(index);
                            if (confirm("Delete \"" + target.getName() + "\" and every note in it? This can't be undone.")) {
                                library.deleteCollection(target.getName());
                                System.out.println("Deleted \"" + target.getName() + "\".");
                            }
                        }
                    }
                    case "u" -> {
                        Path zipFile = readPath("Path to the .zip file: ");
                        if (zipFile != null) {
                            NoteCollection unzipped = archiver.unzip(zipFile, library);
                            System.out.println("Added the collection \"" + unzipped.getName() + "\".");
                        }
                    }
                    case "q" -> {
                        return;
                    }
                    default -> System.out.println("Please choose one of the letters shown.");
                }
            } catch (IllegalArgumentException | IOException | UnsupportedOperationException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    /**
     * Lists every item in one collection by number and loops until the user goes back.
     * Options: open an item, create a note, delete an item (after confirming), import a file,
     * zip the collection to share it, back.
     */
    private void showCollectionMenu(NoteCollection collection) {
        while (true) {
            List<CollectionItem> items = collection.getItems();

            printHeader(collection.getName());
            printNumbered(itemLabels(items), "  (This collection is empty. Choose N to write a note.)");
            System.out.println();
            System.out.println("[O]pen  [N]ew note  [D]elete  [I]mport a file  [Z]ip to share  [B]ack");
            String choice = prompt("> ").toLowerCase();

            try {
                switch (choice) {
                    case "o" -> {
                        int index = promptNumber("Item number: ", items.size());
                        if (index != -1) {
                            openItem(items.get(index), collection);
                        }
                    }
                    case "n" -> {
                        Note note = collection.addNote(prompt("Title for the new note: "));
                        System.out.println("Created \"" + note.getTitle() + "\".");
                        showNoteMenu(note, collection);
                    }
                    case "d" -> {
                        int index = promptNumber("Item number to delete: ", items.size());
                        if (index != -1) {
                            CollectionItem target = items.get(index);
                            if (confirm("Delete \"" + target.getTitle() + "\"? This can't be undone.")) {
                                collection.removeItem(target.getTitle());
                                System.out.println("Deleted \"" + target.getTitle() + "\".");
                            }
                        }
                    }
                    case "i" -> {
                        Path source = readPath("Path to the file to import: ");
                        if (source != null) {
                            CollectionItem imported = importer.importInto(collection, source);
                            System.out.println("Imported \"" + imported.getTitle() + "\".");
                        }
                    }
                    case "z" -> {
                        Path folder = readPath("Folder to save the .zip in (leave blank for the library folder): ");
                        if (folder == null) {
                            folder = collection.getFolder().getParent();
                        }
                        Path zipFile = archiver.zip(collection, folder);
                        System.out.println("Saved " + zipFile);
                    }
                    case "b" -> {
                        return;
                    }
                    default -> System.out.println("Please choose one of the letters shown.");
                }
            } catch (IllegalArgumentException | IOException | UnsupportedOperationException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    /**
     * Shows one note with its links and loops until the user goes back.
     * Options: replace the text, add to the text, add a link, remove a link, open a linked item, back.
     */
    private void showNoteMenu(Note note, NoteCollection collection) {
        while (true) {
            note.open();
            List<CollectionItem> openable = printLinks(note, collection);
            System.out.println();
            System.out.println("[E]dit text  [W]rite more  [A]dd link  [R]emove link  [O]pen a link  [B]ack");
            String choice = prompt("> ").toLowerCase();

            try {
                switch (choice) {
                    case "e" -> {
                        note.setBody(readMultiLine("Type the new text for this note (it replaces the old text)."
                                + "\nWrite [[Title]] to link to another item. Enter a line with just . to finish."));
                        NoteStorage.writeNote(note);
                    }
                    case "w" -> {
                        String more = readMultiLine("Type the text to add to the end of this note."
                                + "\nWrite [[Title]] to link to another item. Enter a line with just . to finish.");
                        if (!more.isEmpty()) {
                            note.setBody(note.getBody().isEmpty() ? more : note.getBody() + "\n" + more);
                            NoteStorage.writeNote(note);
                        }
                    }
                    case "a" -> {
                        List<CollectionItem> candidates = new ArrayList<>(collection.getItems());
                        candidates.remove(note); // A note doesn't need a link to itself
                        if (candidates.isEmpty()) {
                            System.out.println("There's nothing else in this collection to link to yet.");
                        } else {
                            printNumbered(itemLabels(candidates), "");
                            int index = promptNumber("Link to item number: ", candidates.size());
                            if (index != -1) {
                                note.addReference(candidates.get(index));
                                NoteStorage.writeNote(note);
                            }
                        }
                    }
                    case "r" -> {
                        List<String> references = uniqueIgnoringCase(note.getReferences());
                        if (references.isEmpty()) {
                            System.out.println("This note doesn't have any links.");
                        } else {
                            printNumbered(references, "");
                            int index = promptNumber("Remove link number: ", references.size());
                            if (index != -1) {
                                note.removeReference(references.get(index));
                                NoteStorage.writeNote(note);
                            }
                        }
                    }
                    case "o" -> {
                        int index = promptNumber("Number to open: ", openable.size());
                        if (index != -1) {
                            openItem(openable.get(index), collection);
                        }
                    }
                    case "b" -> {
                        return;
                    }
                    default -> System.out.println("Please choose one of the letters shown.");
                }
            } catch (IllegalArgumentException | IOException | UnsupportedOperationException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    /**
     * Prints a note's connections: the items it links to, any links that don't match an item,
     * and the notes that link to it. Links and backlinks share one numbering so either can be opened.
     * Returns the openable items in the same order as their numbers.
     */
    private List<CollectionItem> printLinks(Note note, NoteCollection collection) {
        List<CollectionItem> links = collection.resolveReferences(note);
        List<String> missing = collection.getMissingReferences(note);
        List<Note> backlinks = collection.getBacklinks(note);
        List<CollectionItem> openable = new ArrayList<>();

        System.out.println("Links:");
        if (links.isEmpty() && missing.isEmpty()) {
            System.out.println("  (none)");
        }
        for (CollectionItem item : links) {
            openable.add(item);
            System.out.println(" " + openable.size() + ") " + itemLabel(item));
        }
        for (String title : missing) {
            System.out.println("  -  " + title + "  (missing)");
        }

        System.out.println("Linked from:");
        if (backlinks.isEmpty()) {
            System.out.println("  (none)");
        }
        for (Note linker : backlinks) {
            openable.add(linker);
            System.out.println(" " + openable.size() + ") " + linker.getTitle());
        }
        return openable;
    }

    /**
     * Opens a note in its own note menu, or opens any other item in its default
     * program. A linked note's menu runs inside the current one, so Back returns
     * to the note you came from.
     */
    private void openItem(CollectionItem item, NoteCollection collection) throws IOException {
        if (item instanceof Note note) {
            showNoteMenu(note, collection);
        } else {
            item.open();
        }
    }

    // ---------------------------------------------------------------
    // Display helpers
    // ---------------------------------------------------------------

    /** Prints a section title with a line under it. */
    private void printHeader(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    /** The names of a list of collections, in the same order. */
    private List<String> collectionNames(List<NoteCollection> collections) {
        List<String> names = new ArrayList<>();
        for (NoteCollection collection : collections) {
            names.add(collection.getName());
        }
        return names;
    }

    /** How an item appears in lists: notes show their title, other files are marked "(file)". */
    private String itemLabel(CollectionItem item) {
        return (item instanceof Note) ? item.getTitle() : item.getTitle() + "  (file)";
    }

    /**
     * Copies link titles into a list, skipping any that only differ by capitalization.
     * A note can link to the same item as [[packing list]] in its text and "Packing List" from the menu;
     * removeReference removes both at once, so they should appear as one choice.
     */
    private List<String> uniqueIgnoringCase(Iterable<String> titles) {
        List<String> unique = new ArrayList<>();
        for (String title : titles) {
            boolean alreadyListed = false;
            for (String existing : unique) {
                if (existing.equalsIgnoreCase(title)) {
                    alreadyListed = true;
                    break;
                }
            }
            if (!alreadyListed) {
                unique.add(title);
            }
        }
        return unique;
    }

    /** The labels for a list of items, in the same order. */
    private List<String> itemLabels(List<? extends CollectionItem> items) {
        List<String> labels = new ArrayList<>();
        for (CollectionItem item : items) {
            labels.add(itemLabel(item));
        }
        return labels;
    }

    // ---------------------------------------------------------------
    // Input helpers (always read whole lines with nextLine)
    // ---------------------------------------------------------------

    /**
     * Prints a message and returns the line the user types, with surrounding spaces
     * removed.
     */
    private String prompt(String message) {
        System.out.print(message);
        return scanner.nextLine().trim();
    }

    /**
     * Asks for a number from 1 to max and returns it as a list index (0 to max -
     * 1).
     * Returns -1 if the input isn't a number or is out of range, after telling the
     * user.
     */
    private int promptNumber(String message, int max) {

        var input = prompt(message);
        int number;
        try {
            number = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("Please enter a number.");
            return -1;
        }
        if (1 <= number && number <= max) {
            return number - 1;
        }
        System.out.println("Please choose a number from 1 to " + max + ".");
        return -1;
    }

    /** Asks a yes/no question and returns true only if the user types y or yes. */
    private boolean confirm(String question) {

        var input = prompt(question + " (y/n) ");
        return input.equalsIgnoreCase("y") || input.equalsIgnoreCase("yes");

    }

    /**
     * Reads several lines of text until the user types a line containing only "."
     * and returns them joined with "\n".
     * Used for writing a note's body.
     */
    private String readMultiLine(String instructions) {

        System.out.println(instructions);

        String line;
        List<String> lines = new ArrayList<>();

        while (true) {
            line = scanner.nextLine();
            if (line.equals(".")) {
                break;
            }
            lines.add(line);
        }
        return String.join("\n", lines);
    }

    /**
     * Prints a list of names as " 1) name" lines, or a message if the list is
     * empty.
     */
    private void printNumbered(List<String> names, String emptyMessage) {

        if (names.isEmpty()) {
            System.out.println(emptyMessage);
        } else {
            for (int i = 0; i < names.size(); i++) {
                System.out.println(" " + (i + 1) + ") " + names.get(i));
            }
        }
    }

    /**
     * Asks for a file or folder path and returns it, or null if the user leaves it blank.
     * Removes surrounding quotes, since Windows' "Copy as path" adds them.
     * Throws IllegalArgumentException (InvalidPathException) if the text can't be a path.
     */
    private Path readPath(String message) {
        String input = prompt(message);
        if (input.startsWith("\"") && input.endsWith("\"") && input.length() >= 2) {
            input = input.substring(1, input.length() - 1).trim();
        }
        return input.isEmpty() ? null : Path.of(input);
    }
}
