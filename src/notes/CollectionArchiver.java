package notes;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Packs a collection into a single .zip file so it can be shared, and unpacks a shared .zip
 * back into a new collection in the library. Uses java.util.zip.
 */
public class CollectionArchiver {

    /** Used to name exported files and to check that a file being unzipped really is a .zip. */
    private static final String ZIP_EXTENSION = ".zip";

    /**
     * Saves the collection, then writes every file in its folder into "CollectionName.zip"
     * inside the destination folder. Returns the path of the new .zip file.
     * Throws IllegalArgumentException if the destination isn't a folder or the .zip file already exists.
     */
    public Path zip(NoteCollection collection, Path destinationFolder) throws IOException {
        if (!Files.isDirectory(destinationFolder)) {
            throw new IllegalArgumentException("No folder found at " + destinationFolder);
        }
        Path zipFile = destinationFolder.resolve(collection.getName() + ZIP_EXTENSION);
        if (Files.exists(zipFile)) {
            throw new IllegalArgumentException(zipFile.getFileName() + " already exists in that folder.");
        }

        collection.save(); // Make sure every note on disk matches what's in memory before packing

        //CREATE_NEW fails instead of overwriting if the file appeared since the check above
        try (OutputStream fileOut = Files.newOutputStream(zipFile, StandardOpenOption.CREATE_NEW);
             ZipOutputStream zipOut = new ZipOutputStream(fileOut)) {
            for (Path file : NoteStorage.listFiles(collection.getFolder())) {
                if (!Files.isRegularFile(file)) {
                    continue; // Collections don't contain folders
                }
                zipOut.putNextEntry(new ZipEntry(file.getFileName().toString()));
                Files.copy(file, zipOut);
                zipOut.closeEntry();
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(zipFile); // Don't leave a half-written .zip behind
            throw e;
        }
        return zipFile;
    }

    /**
     * Creates a new collection named after the .zip file (e.g. "Camping.zip" -> "Camping"),
     * extracts every file into it, loads it, and returns it.
     * Throws IllegalArgumentException if the file isn't a .zip or the library already has a collection with that name.
     * If anything goes wrong partway through, the half-made collection is deleted.
     */
    public NoteCollection unzip(Path zipFile, Library library) throws IOException {
        if (zipFile == null || !Files.isRegularFile(zipFile)) {
            throw new IllegalArgumentException("No file found at " + zipFile);
        }
        String fileName = zipFile.getFileName().toString();
        if (!fileName.toLowerCase().endsWith(ZIP_EXTENSION)) {
            throw new IllegalArgumentException(fileName + " isn't a .zip file.");
        }
        String name = fileName.substring(0, fileName.length() - ZIP_EXTENSION.length());

        NoteCollection collection = library.createCollection(name); // Checks the name and makes the folder

        try (InputStream fileIn = Files.newInputStream(zipFile);
             ZipInputStream zipIn = new ZipInputStream(fileIn)) {
            ZipEntry entry;
            int filesExtracted = 0;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                Path target = safeTarget(collection.getFolder(), entry.getName());
                Files.copy(zipIn, target); // Copies just this entry; throws if two entries have the same name
                filesExtracted++;
            }
            //A file that isn't really a zip reads as a zip with no entries instead of causing an error
            if (filesExtracted == 0) {
                throw new IllegalArgumentException(fileName + " is empty or isn't a valid .zip file.");
            }
            collection.load();
        } catch (IOException | RuntimeException e) {
            library.deleteCollection(collection.getName()); // Remove the half-made collection, then report the problem
            throw e;
        }
        return collection;
    }

    /**
     * Works out where a zip entry should be extracted and makes sure it stays inside the collection's folder.
     * A malicious zip can contain names like "../../Windows/evil.exe" ("zip slip"); this refuses them.
     */
    private Path safeTarget(Path folder, String entryName) throws IOException {
        //Only the last part of the entry's name is used, so folders inside the zip are flattened into the collection
        Path fileNameOnly = Path.of(entryName).getFileName();
        if (fileNameOnly == null) {
            throw new IOException("Unsafe file name in zip: " + entryName);
        }
        Path target = folder.resolve(fileNameOnly.toString()).normalize();
        if (!target.getParent().equals(folder.normalize())) {
            throw new IOException("Unsafe file name in zip: " + entryName);
        }
        return target;
    }
}
