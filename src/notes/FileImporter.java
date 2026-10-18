package notes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Brings a file from elsewhere on the computer into a collection.
 * A .txt file becomes a Note (so it can be edited and linked); anything else is
 * copied in as an Attachment. The original file is never changed, since the
 * user may still want it where it was.
 */
public class FileImporter {

    /**
     * Imports one file into the collection and returns the new item.
     * Throws IllegalArgumentException if the source isn't a file or its name is
     * already used in the collection,
     * and IOException if reading or copying fails.
     */
    public CollectionItem importInto(NoteCollection collection, Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("No file found at " + source);
        }

        String fileName = source.getFileName().toString();
        if (fileName.toLowerCase().endsWith(NoteCollection.NOTE_EXTENSION)) {
            return importAsNote(collection, source);
        }
        return importAsAttachment(collection, source);
    }

    /**
     * Reads a text file and creates a Note from it. The note's title is the file
     * name without ".txt".
     * Any [[links]] already in the text work right away.
     * .txt files are imported this way because if they were imported as another
     * Attachment object then upon reloading saved information, they would be
     * recognized as Notes but be missing information like the reference line.
     */
    private Note importAsNote(NoteCollection collection, Path source) throws IOException {
        String fileName = source.getFileName().toString();
        String title = fileName.substring(0, fileName.length() - NoteCollection.NOTE_EXTENSION.length());

        // Read the text before creating the note, so a file that can't be read doesn't
        // leave an empty note behind. Windows line endings (\r\n) are changed to \n to
        // match how notes are stored everywhere else.
        String text = Files.readString(source, StandardCharsets.UTF_8).replace("\r\n", "\n");
        // Trailing line breaks are removed because they wouldn't survive a save and
        // reload anyway (Files.readAllLines drops empty lines at the end of a file),
        // so the note looks the same before and after
        while (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }

        Note note = collection.addNote(title); // Checks the title, refuses duplicates, and creates the file
        note.setBody(text);
        NoteStorage.writeNote(note); // Saves it in note format, with the refs: line
        return note;
    }

    /**
     * Copies a non-text file into the collection's folder and adds it as an
     * Attachment.
     * The attachment's title is the full file name, including its extension.
     */
    private Attachment importAsAttachment(NoteCollection collection, Path source) throws IOException {
        String title = NoteStorage.validateName(source.getFileName().toString());
        if (collection.findByTitle(title).isPresent()) {
            throw new IllegalArgumentException(collection.getName() + " already has an item named \"" + title + "\".");
        }

        Path copy = copyIntoFolder(source, collection.getFolder());
        Attachment attachment = new Attachment(title, copy, LocalDateTime.now());
        collection.addItem(attachment);
        return attachment;
    }

    /**
     * Copies a file into a folder, keeping its name, and returns the copy's path.
     * Refuses to overwrite a file that's already there.
     */
    private Path copyIntoFolder(Path source, Path folder) throws IOException {
        Path target = folder.resolve(source.getFileName().toString());
        if (Files.exists(target)) {
            throw new IllegalArgumentException(
                    "A file named \"" + target.getFileName() + "\" is already in that collection's folder.");
        }
        NoteStorage.createFolder(folder);
        return Files.copy(source, target); // Without REPLACE_EXISTING, copy never overwrites
    }
}
