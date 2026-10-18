package notes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A NoteCollection is one folder on disk holding a group of related items.
 * It keeps every item in one List of CollectionItem, and it is the class that
 * knows how items connect:
 * it turns a note's link titles into real items (resolveReferences) and finds
 * which notes link to an item (getBacklinks).
 * Titles are compared ignoring capitalization everywhere in this class, so
 * [[packing list]] finds "Packing List".
 */
public class NoteCollection {

    /** Every note is saved as a plain text file with this extension. */
    public static final String NOTE_EXTENSION = ".txt";

    /** The collection's name, which is also its folder's name. */
    private final String name;

    /** The folder this collection's files are stored in. */
    private final Path folder;

    /**
     * Notes and attachments together in one list of CollectionItem, so the menu
     * can list and open them all the same way.
     */
    private final List<CollectionItem> items;

    /**
     * Creates an empty collection that lives in the given folder. Call load() to
     * fill it from disk.
     */
    public NoteCollection(String name, Path folder) {
        this.name = name;
        this.folder = folder;
        this.items = new ArrayList<>();
    }

    /** The collection's name, as shown in the menu. */
    public String getName() {
        return name;
    }

    /** The folder this collection's files are stored in. */
    public Path getFolder() {
        return folder;
    }

    /**
     * Every item in the collection, as a read-only list so items can only be
     * changed through this class's methods.
     */
    public List<CollectionItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Creates a new, empty note with the given title, saves it to disk right away,
     * and adds it to the collection.
     * Returns the new note so the caller can set its body.
     * Throws IllegalArgumentException if the title is blank, has characters a file
     * name can't contain, or is already used.
     */
    public Note addNote(String title) throws IOException {
        String cleanTitle = NoteStorage.validateName(title);
        if (findByTitle(cleanTitle).isPresent()) {
            throw new IllegalArgumentException(name + " already has an item named \"" + cleanTitle + "\".");
        }
        Path file = folder.resolve(cleanTitle + NOTE_EXTENSION);

        // A file with this name can exist on disk without being in the list (for
        // example a file that failed to load).
        // Refusing here means a new note can never overwrite it.
        if (Files.exists(file)) {
            throw new IllegalArgumentException(
                    "A file named \"" + file.getFileName() + "\" already exists in " + name + ".");
        }

        Note note = new Note(cleanTitle, file, LocalDateTime.now());
        // Save before adding, so a failed save doesn't leave a note in the list with no file behind it
        NoteStorage.writeNote(note);
        addItem(note);
        return note;
    }

    /**
     * Adds an item that was already created somewhere else (a note read from disk,
     * or later an imported attachment).
     * Throws IllegalArgumentException if the collection already has an item with
     * the same title.
     */
    public void addItem(CollectionItem item) {
        if (findByTitle(item.getTitle()).isPresent()) {
            throw new IllegalArgumentException(name + " already has an item named \"" + item.getTitle() + "\".");
        }
        items.add(item);
    }

    /**
     * Removes the item with this title from the collection and deletes its file
     * from disk.
     * Returns false if there was no item with that title. Links to the item in
     * other notes are left as they are
     * and will show up as missing.
     */
    public boolean removeItem(String title) throws IOException {
        Optional<CollectionItem> found = findByTitle(title);
        if (found.isEmpty()) {
            return false;
        }
        CollectionItem item = found.get();
        // Delete the file first; if that fails, the item stays in the list and still matches the disk
        NoteStorage.delete(item.getFile());
        items.remove(item);
        return true;
    }

    /**
     * Returns the item whose title matches (ignoring capitalization and surrounding
     * spaces), or an empty Optional if there isn't one.
     */
    public Optional<CollectionItem> findByTitle(String title) {
        if (title == null) {
            return Optional.empty();
        }
        String wanted = title.trim();
        for (CollectionItem item : items) {
            if (item.getTitle().equalsIgnoreCase(wanted)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /** Every item in the collection that is a Note. */
    public List<Note> getNotes() {
        List<Note> notes = new ArrayList<>();
        for (CollectionItem item : items) {
            if (item instanceof Note note) {
                notes.add(note);
            }
        }
        return notes;
    }

    /**
     * Every item in the collection that is an Attachment (any file that isn't a
     * note).
     */
    public List<Attachment> getAttachments() {
        List<Attachment> attachments = new ArrayList<>();
        for (CollectionItem item : items) {
            if (item instanceof Attachment attachment) {
                attachments.add(attachment);
            }
        }
        return attachments;
    }

    /**
     * Turns a note's link titles into the actual items they point to, in the order
     * the links appear.
     * Titles that don't match any item (broken links) are left out;
     * getMissingReferences lists those.
     */
    public List<CollectionItem> resolveReferences(Note note) {
        List<CollectionItem> resolved = new ArrayList<>();
        for (String title : note.getReferences()) {
            Optional<CollectionItem> found = findByTitle(title);
            // The same item can be linked twice with different capitalization, e.g.
            // [[packing list]] and "Packing List"
            if (found.isPresent() && !resolved.contains(found.get())) {
                resolved.add(found.get());
            }
        }
        return resolved;
    }

    /**
     * The link titles in a note that don't match any item in this collection, so
     * the menu can show them as missing.
     */
    public List<String> getMissingReferences(Note note) {
        List<String> missing = new ArrayList<>();
        for (String title : note.getReferences()) {
            if (findByTitle(title).isEmpty()) {
                missing.add(title);
            }
        }
        return missing;
    }

    /**
     * Every note in this collection that links to the target item. A note linking
     * to itself isn't counted.
     * A note only stores what it links to, not what links to it, so backlinks are
     * found by checking every note's links. That's quick for a collection this
     * size, and it means there's no second list that could fall out of sync.
     */
    public List<Note> getBacklinks(CollectionItem target) {
        List<Note> backlinks = new ArrayList<>();
        for (Note note : getNotes()) {
            if (note == target) {
                continue;
            }
            for (String title : note.getReferences()) {
                if (title.equalsIgnoreCase(target.getTitle())) {
                    backlinks.add(note);
                    break; // One matching link is enough; stop checking this note
                }
            }
        }
        return backlinks;
    }

    /**
     * Writes every note to disk. Attachments don't need saving; they are already
     * files in the folder.
     * Changes are saved after every change to prevent file loss upon crash or other
     * unexpected issues.
     */
    public void save() throws IOException {
        NoteStorage.createFolder(folder);
        for (Note note : getNotes()) {
            NoteStorage.writeNote(note);
        }
    }

    /**
     * Clears the collection and refills it from the files in its folder.
     * Every .txt file is read as a note and every other file as an attachment. A
     * file that can't be read is skipped
     * instead of stopping the whole load;
     * the returned list describes each skipped file so the menu can show a warning.
     * An empty list means everything loaded.
     */
    public List<String> load() throws IOException {
        items.clear();
        List<String> problems = new ArrayList<>();

        if (!Files.isDirectory(folder)) {
            NoteStorage.createFolder(folder); // A brand-new collection has no folder yet
            return problems;
        }

        for (Path path : NoteStorage.listFiles(folder)) {
            if (Files.isDirectory(path)) {
                continue; // Collections don't contain folders
            }

            String fileName = path.getFileName().toString();
            if (fileName.toLowerCase().endsWith(NOTE_EXTENSION)) {
                try {
                    addItem(NoteStorage.readNote(path));
                } catch (IOException | IllegalArgumentException e) {
                    problems.add("Skipped " + fileName + ": " + e.getMessage());
                }
            } else {
                // Every other file is an attachment (image, audio, PDF, ...)
                try {
                    addItem(NoteStorage.readAttachment(path));
                } catch (IOException | IllegalArgumentException e) {
                    problems.add("Skipped " + fileName + ": " + e.getMessage());
                }
            }
        }
        return problems;
    }
}
