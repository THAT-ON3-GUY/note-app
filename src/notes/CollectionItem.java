package notes;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Creating this abstract CollectionItem class allows me to save different kinds
 * of files inside the same collection all inside one easy to reference List.
 * This also lets the Menu call open() without checking what kind of item it has.
 */
public abstract class CollectionItem {

    // These are protected instead of private so Note and Attachment can use them
    // directly (for example in their open() methods) without going through getters.

    /** The item's name, taken from its file name. Links find items by this title. */
    protected String title;

    /** Where the item is stored on disk. */
    protected Path file;

    /** When the item's file was created. Shown when a note is opened. */
    protected LocalDateTime createdAt;

    /**
     * Protected because only subclasses should create items. A plain
     * CollectionItem wouldn't know how to open itself.
     */
    protected CollectionItem(String title, Path file, LocalDateTime createdAt) {
        this.title = title;
        this.file = file;
        this.createdAt = createdAt;
    }

    /** The item's title, which is also its file name (notes leave off ".txt"). */
    public String getTitle() {
        return title;
    }

    /** The path to the item's file on disk. */
    public Path getFile() {
        return file;
    }

    /** When the item's file was created. */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Opens the item in the way that suits its type: a Note prints itself to the
     * console, and an Attachment launches the computer's default program.
     * It's abstract so every kind of item has to decide what opening means, which
     * lets Menu open any item without checking what kind it is.
     * Throws IOException if the item's file can't be opened.
     */
    public abstract void open() throws IOException;
}
