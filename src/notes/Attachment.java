package notes;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * A non-text file stored in a collection, such as an image, audio clip or PDF.
 * Opening it hands the file to the computer's default program for that type.
 */
public class Attachment extends CollectionItem {

    /**
     * Worked out once when the attachment is made, since the file name (and so
     * its extension) never changes.
     */
    private final FileType type;

    /**
     * The title is the file's full name including its extension, e.g.
     * "campsite-map.png".
     */
    public Attachment(String title, Path file, LocalDateTime createdAt) {
        super(title, file, createdAt);
        this.type = FileType.fromFileName(title);
    }

    /** The kind of file this is (image, audio, ...), based on its extension. */
    public FileType getType() {
        return type;
    }

    /**
     * Opens the file in the computer's default program (image viewer, media player,
     * etc.).
     */
    @Override
    public void open() throws IOException {
        ExternalOpener.open(file);
    }
}
