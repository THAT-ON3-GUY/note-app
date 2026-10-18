package notes;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.util.stream.*;

/**
 * NoteStorage contains the file reading and writing capabilities of the
 * program. Putting these methods in a static utility class allows them to be
 * referenced in various places throughout the program simplifying the saving
 * and loading process and creating one spot to debugg reading and writing
 * related issues.
 */
public class NoteStorage {

    /**
     * The label at the start of the first line of every note file, before the extra
     * link titles.
     */
    private static final String REFS_PREFIX = "refs:";

    /**
     * Characters Windows doesn't allow in file or folder names. Note titles and
     * collection names become file and folder names, so they can't contain these.
     */
    private static final String ILLEGAL_NAME_CHARS = "\\/:*?\"<>|";

    /**
     * Private constructor since every method is static. I.E. no instance needs to
     * be made
     */
    private NoteStorage() {
    }

    /**
     * Creates a folder and returns its path. createDirectories is used instead of
     * createDirectory because it doesn't fail if the folder already exists, and it
     * also creates missing parent folders (like Documents/NoteApp on the first run).
     */
    public static Path createFolder(Path folder) throws IOException {
        return Files.createDirectories(folder);
    }

    /**
     * Saves a note's extra references and body to its file. The title isn't written
     * inside the file; it comes from the file name. I used .writeString here as I
     * wanted to prioritize preservation of original document spacing.
     * I also used the | character as a way to separate the titles on the refs:
     * line, which is given priority. By placing the refs: line first it makes it
     * easy to extract, divide up, and distinguish from the regular body of a note.
     * This, in addition to the fact that the body and references are normally
     * stored in separate variables in the system, makes saving them this way a
     * non-issue. The | separator is safe because validateName doesn't allow it in
     * titles, so it can never be part of one.
     */
    public static void writeNote(Note note) throws IOException {
        createFolder(note.getFile().getParent());
        String input = REFS_PREFIX + String.join("|", note.getAdditionalRefs()) + "\n" + note.getBody();
        Files.writeString(note.getFile(), input, StandardCharsets.UTF_8);

    }

    /**
     * Reads a note file from disk and builds a Note object from it.
     * If the first line doesn't start with "refs:" (a plain .txt file the user made
     * or imported), the whole file is treated as the body. This fallback is what
     * lets ordinary text files be imported or dropped into a collection's folder
     * and still load as notes.
     */
    public static Note readNote(Path file) throws IOException {

        String name = file.getFileName().toString();

        if (name.lastIndexOf(".") == -1) {
            throw new IOException("Not a note file (it has no file extension): " + file.getFileName());
        }

        name = name.substring(0, name.lastIndexOf("."));

        List<String> noteDetails = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> refs = new ArrayList<>();
        int bodyStart = 0; // Index of the first body line: 1 if the file has a refs line, 0 if it doesn't

        // An empty file is just an empty note, so there's nothing to parse
        if (!noteDetails.isEmpty() && noteDetails.get(0).startsWith(REFS_PREFIX)) {
            var refsArray = noteDetails.get(0).substring(REFS_PREFIX.length()).split("\\|");

            for (String piece : refsArray) {
                String ref = piece.trim();
                if (!ref.isEmpty()) {
                    refs.add(ref);
                }
            }
            bodyStart = 1;
        }

        var createdAt = readCreationTime(file);

        Note newNote = new Note(name, file, createdAt, refs);

        var bodyString = String.join("\n", noteDetails.subList(bodyStart, noteDetails.size()));
        newNote.setBody(bodyString);

        return newNote;
    }

    /**
     * Builds an Attachment for a non-text file that's already in a collection's
     * folder.
     * The title is the full file name, including its extension.
     */
    public static Attachment readAttachment(Path file) throws IOException {
        return new Attachment(file.getFileName().toString(), file, readCreationTime(file));
    }

    /**
     * Finds when a file was created according to the operating system, turns it
     * into an Instant (an exact moment in UTC), then converts that to this
     * computer's local time.
     * The date isn't stored inside the note file, so copying, importing or
     * unzipping a file resets it to that moment.
     */
    private static LocalDateTime readCreationTime(Path file) throws IOException {
        return LocalDateTime.ofInstant(
                Files.readAttributes(file, BasicFileAttributes.class).creationTime().toInstant(),
                ZoneId.systemDefault());
    }

    /**
     * Returns every file and folder directly inside a folder, sorted by name so
     * collections and items always appear in the same order.
     * The stream is closed with try-with-resources because Files.list keeps the
     * folder open, and on Windows a folder that's open can't be deleted.
     */
    public static List<Path> listFiles(Path folder) throws IOException {
        try (Stream<Path> entries = Files.list(folder)) {
            return entries.sorted().toList();
        }
    }

    /**
     * Checks that a name (a note title or a collection name) can safely be used as
     * a file or folder name, and returns it with surrounding spaces removed.
     * It doesn't check for duplicates, since that depends on where the name is
     * going; NoteCollection and Library each check their own items.
     * Throws IllegalArgumentException with a message describing the problem.
     * validateName lives here because what it's doing is actually part of the
     * saving and loading process: it makes sure file names are compatible with
     * what Windows allows a file to be named.
     */
    public static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A name can't be empty.");
        }
        String cleanName = name.trim();

        for (char c : cleanName.toCharArray()) {
            // A \ or / would be read as a folder separator, and Windows refuses to
            // save a file whose name has any of the others
            if (ILLEGAL_NAME_CHARS.indexOf(c) != -1) {
                throw new IllegalArgumentException(
                        "A name can't contain any of these characters: " + ILLEGAL_NAME_CHARS);
            }
        }
        // Windows silently drops a dot at the end of a file name, and "." / ".." mean
        // "this folder" / "the parent folder"
        if (cleanName.endsWith(".")) {
            throw new IllegalArgumentException("A name can't end with a period.");
        }
        return cleanName;
    }

    /**
     * Deletes a file, or a folder and everything inside it.
     * A folder has to be empty before it can be deleted, so for a folder this
     * calls itself on each thing inside first (which handles subfolders at any
     * depth), then deletes the now-empty folder. deleteIfExists runs for files and
     * folders alike, which is why it's after the if instead of in an else.
     */
    public static void delete(Path path) throws IOException {
        // I used the Link Option argument here to stop the program from deleting
        // files outside the library
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            for (Path child : listFiles(path)) {
                delete(child);
            }
        }
        Files.deleteIfExists(path);
    }
}
