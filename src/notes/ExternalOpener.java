package notes;

import java.io.IOException;
import java.nio.file.Path;
import java.awt.Desktop;
import java.nio.file.Files;

/**
 * Hands files to the operating system so they open in the user's default
 * program, using java.awt.Desktop. This is kept in its own class so Attachment
 * doesn't depend on the desktop library directly, and the support check lives
 * in one place.
 */
public class ExternalOpener {

    /** Private because every method is static, so no ExternalOpener object is ever needed. */
    private ExternalOpener() {
    }

    /**
     * Returns true if this computer lets Java open files in their default programs.
     * Some environments (servers, some Linux setups) don't, and Desktop would throw
     * an exception there.
     */
    public static boolean isSupported() {

        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
    }

    /**
     * Opens the file in its default program.
     * Throws IOException if the file doesn't exist or no program is set up for that
     * file type, and UnsupportedOperationException if this computer can't open
     * files from Java at all.
     */
    public static void open(Path file) throws IOException {

        if (!isSupported()) {
            throw new UnsupportedOperationException("Opening files isn't supported on this computer.");
        }
        if (!Files.exists(file)) {
            throw new IOException("\"" + file.getFileName() + "\" doesn't exist, or isn't recognized by the system.");
        }
        Desktop.getDesktop().open(file.toFile());
    }
}
