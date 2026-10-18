package notes;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;

/**
 * ReferenceParser is an external class that handles the process of finding
 * links. This allows the handling of links to be debugged from one place and
 * also makes it so imported notes can match the pattern for links I've
 * established for the program.
 */
public class ReferenceParser {

    /**
     * Private so a ReferenceParser can't be created, as its purpose is for the use
     * of its methods.
     */
    private ReferenceParser() {
    }

    /**
     * Matches a link written as [[Title]]. The brackets are escaped with \\
     * because [ and ] have a special meaning in a regex. The parentheses capture
     * the title as group 1 so it can be read without the brackets. The ? in .+?
     * makes the match lazy (as short as possible); without it, "[[a]] and [[b]]"
     * would be read as one link titled "a]] and [[b".
     * It's compiled once here instead of in each method because compiling a
     * pattern is the slow part.
     */
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[\\[(.+?)\\]\\]");

    /**
     * This method will find references within the body of a note and then return
     * them as a set of strings that a note can use to display the references
     * contained within it.
     * A LinkedHashSet keeps the titles in the order they appear and lists a link
     * written twice only once. Titles are trimmed and empty ones ([[ ]]) are
     * skipped so stray spaces don't create broken links.
     */
    public static Set<String> extractReferences(String body) {

        if (body == null) {
            Set<String> empty = new HashSet<>();
            return empty;
        }

        Matcher matcher = LINK_PATTERN.matcher(body);
        Set<String> results = new LinkedHashSet<>();

        while (matcher.find()) {

            var link = matcher.group(1).trim();
            if (!link.isEmpty()) {
                results.add(link);
            }

        }

        return results;
    }

    /**
     * This method takes the body of a note and a link that the user wants removed
     * from the note as parameters and then filters through the body replacing
     * instances of the link with a text only counterpart.
     * The title is kept as plain text instead of being deleted, so the sentence it
     * was in still reads correctly.
     */
    public static String deleteReferences(String body, String delete) {

        if (body == null) {
            String empty = "";
            return empty;
        }

        Matcher matcher = LINK_PATTERN.matcher(body);
        String newBody = matcher.replaceAll(match -> {
            String linkTitle = match.group(1).trim();
            // Ignores capitalization to match how NoteCollection.findByTitle looks up links
            if (linkTitle.equalsIgnoreCase(delete.trim())) {
                // quoteReplacement is needed because replaceAll treats $ and \ in the
                // replacement as special, which would break titles containing them
                return Matcher.quoteReplacement(match.group(1));
            }
            // Not the link being removed, so it goes back unchanged
            return Matcher.quoteReplacement(match.group(0));
        });
        return newBody;
    }

}
