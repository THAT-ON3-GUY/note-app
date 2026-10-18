package notes;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.time.format.DateTimeFormatter;

/**
 * A Note represents a regular .txt file that is found within a collection.
 * Note is a child of CollectionItem just like Attachment which allows both to
 * be kept within a NoteCollection.
 * Notes are distinguished from Attachments because of their ability to contain
 * links to other items in a collection.
 * Links are stored as titles instead of references to the objects themselves,
 * so they can be saved as plain text and still work after reloading. The
 * trade-off is that renaming an item would break the links that point to it.
 */
public class Note extends CollectionItem {

    /** The note's text as the user wrote it, including any [[Title]] links. */
    private String body;

    /**
     * Links added from the menu instead of written in the text. They're kept
     * separate so they can be saved on the refs: line without changing what the
     * user wrote. A LinkedHashSet keeps them in the order they were added and
     * ignores exact duplicates.
     */
    private Set<String> additionalRefs;

    /**
     * Used by NoteStorage.readNote when loading a saved note, so the links from
     * its refs: line come back. The body is filled in afterwards with setBody.
     */
    public Note(String title, Path file, LocalDateTime createdAt, List<String> refs) {
        super(title, file, createdAt);
        this.body = "";
        this.additionalRefs = new LinkedHashSet<>(refs);
    }

    /** Used for a brand-new note that has no text or extra links yet. */
    public Note(String title, Path file, LocalDateTime createdAt) {
        super(title, file, createdAt);
        this.body = "";
        this.additionalRefs = new LinkedHashSet<>();
    }

    /** The note's text, including any [[Title]] links written in it. */
    public String getBody() {
        return body;
    }

    /**
     * Replaces the note's text. This only changes the note in memory; the caller
     * saves it with NoteStorage.writeNote right after.
     */
    public void setBody(String body) {
        this.body = body;
    }

    /**
     * Adds the target's title as an extra link, unless the note already has that
     * extra link (ignoring capitalization).
     */
    public void addReference(CollectionItem target) {
        String title = target.getTitle();
        for (String existing : additionalRefs) {
            if (existing.equalsIgnoreCase(title)) {
                return;
            }
        }
        additionalRefs.add(title);
    }

    /**
     * Removes a link from both places links live: the extra links, and any
     * [[title]] in the body (which becomes plain text).
     * Capitalization is ignored, so removing "Packing List" also removes [[packing
     * list]].
     */
    public void removeReference(String title) {
        String wanted = title.trim();
        additionalRefs.removeIf(existing -> existing.equalsIgnoreCase(wanted));
        body = ReferenceParser.deleteReferences(body, wanted);
    }

    /**
     * getReferences builds its list each time it's called instead of
     * storing it as this allows the list of references displayed on a note
     * to be updated in real time. This also works alongside the fact
     * that links can come from two different places. Either a
     * link is written into a note with "[[...]]", or it's added
     * using the menu. The reconstruction of the list is simpler in the long run
     * as modification of a note's links isn't something that happens
     * a large number of times.
     */
    public Set<String> getReferences() {
        Set<String> ref = ReferenceParser.extractReferences(body);
        ref.addAll(additionalRefs);
        return ref;
    }

    /**
     * Only the links added from the menu. It's a read-only view so other classes
     * can display and save these links, but can only change them through
     * addReference and removeReference.
     */
    public Set<String> getAdditionalRefs() {
        Set<String> readOnlyView = Collections.unmodifiableSet(additionalRefs);
        return readOnlyView;
    }

    /**
     * Shows the note in the console: a header with the title and date, the body as
     * written, and any extra links.
     * Only the extra links are listed under "See also", since links written as
     * [[Title]] are already visible in the body.
     */
    @Override
    public void open() {
        final int minWidth = 40;
        String date = (createdAt == null) ? ""
                : "Created " + createdAt.format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a"));

        // Make the border as wide as the widest line, but never narrower than minWidth
        int width = Math.max(minWidth, Math.max(title.length(), date.length()) + 4);
        String heavyLine = "=".repeat(width);
        String lightLine = "-".repeat(width);

        System.out.println();
        System.out.println(heavyLine);
        System.out.println("  " + title);
        if (!date.isEmpty()) {
            System.out.println("  " + date);
        }
        System.out.println(heavyLine);
        System.out.println();

        if (body == null || body.isBlank()) {
            System.out.println("  (This note is empty.)");
        } else {
            for (String line : body.split("\n", -1)) { // -1 keeps blank lines at the end so spacing is preserved
                System.out.println("  " + line);
            }
        }
        System.out.println();

        if (!additionalRefs.isEmpty()) {
            System.out.println(lightLine);
            System.out.println("  See also: " + String.join(", ", additionalRefs));
        }
        System.out.println(heavyLine);
    }

}
