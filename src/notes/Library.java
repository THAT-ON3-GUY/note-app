package notes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Library takes each collection folder in the library 
 * and maps it to a NoteCollection.
 * Library is where these folders are created, found, and deleted.
 */
public class Library {

    /** The NoteApp folder. Every folder inside it is one collection. */
    private final Path rootFolder;

    /**
     * Collections keyed by their name in lowercase, so looking one up ignores
     * capitalization the same way note titles do. A TreeMap keeps them sorted by
     * name, so the menu always lists them alphabetically.
     */
    private final Map<String, NoteCollection> collections = new TreeMap<>();

    /**
     * Nothing is read from disk until loadCollections is called, so the caller
     * decides how to handle any problems loading.
     */
    public Library(Path rootFolder) {
        this.rootFolder = rootFolder;
    }

    /**
     * Reads every folder in the library as a collection, replacing whatever was
     * loaded before.
     * A file that can't be read doesn't stop the load. Its problem is returned as
     * a warning instead (starting with the collection's name, so the user knows
     * where to look), so one damaged note doesn't hide the rest of the library.
     * Throws IOException only if the library folder itself can't be used.
     */
    public List<String> loadCollections() throws IOException {

        collections.clear();
        // On the first run the library folder doesn't exist yet
        NoteStorage.createFolder(rootFolder);
        List<String> problems = new ArrayList<>();

        for (Path collection : NoteStorage.listFiles(rootFolder)) {
            if (!Files.isDirectory(collection)) {
                // Only folders are collections. Loose files, like exported .zips (which
                // are saved here by default), are skipped
                continue;
            }
            String name = collection.getFileName().toString();
            NoteCollection temp = new NoteCollection(name, collection);

            for (String problem : temp.load()) {
                problems.add(name + ": " + problem);
            }
            collections.put(name.toLowerCase(), temp);
        }
        return problems;
    }

    /**
     * Creates a new, empty collection with its own folder in the library and
     * returns it.
     * Throws IllegalArgumentException if the name can't be used as a folder name or
     * the library already has a collection with that name.
     */
    public NoteCollection createCollection(String name) throws IOException {
        String cleanName = NoteStorage.validateName(name);
        String key = cleanName.toLowerCase();

        if (collections.containsKey(key)) {
            throw new IllegalArgumentException("The library already has a collection named \"" + cleanName + "\".");
        }
        Path folder = rootFolder.resolve(cleanName);
        // Something with this name is on disk but isn't a loaded collection; don't take it over
        if (Files.exists(folder)) {
            throw new IllegalArgumentException(
                    "A folder or file named \"" + cleanName + "\" already exists in the library.");
        }

        NoteStorage.createFolder(folder);
        NoteCollection collection = new NoteCollection(cleanName, folder);
        collections.put(key, collection);
        return collection;
    }

    /**
     * Finds a collection by name, ignoring capitalization and surrounding spaces.
     * Returns an Optional instead of null so callers have to handle the case where
     * there's no collection with that name.
     */
    public Optional<NoteCollection> getCollection(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(collections.get(name.trim().toLowerCase()));
    }

    /**
     * Permanently deletes a collection's folder and every file in it. Returns
     * false if there's no collection with that name.
     * The folder is deleted before the collection is removed from the map, so if
     * deleting fails the library still matches what's on disk.
     */
    public boolean deleteCollection(String name) throws IOException {
        Optional<NoteCollection> found = getCollection(name);

        if (found.isEmpty()) {
            return false;
        }
        NoteCollection collection = found.get();

        NoteStorage.delete(collection.getFolder());
        collections.remove(collection.getName().toLowerCase());
        return true;
    }

    /**
     * Every collection, sorted by name. Returns a copy so changing the list
     * doesn't change the library.
     */
    public List<NoteCollection> listCollections() {
        return new ArrayList<>(collections.values());
    }
}
