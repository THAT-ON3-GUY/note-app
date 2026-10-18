package notes;

import java.util.List;

/**
 * The kinds of files a collection can hold as attachments. Text files aren't
 * here because importing a .txt file turns it into a Note instead of an
 * Attachment. Each type lists the file extensions that belong to it, so
 * fromFileName can look a file up.
 */
public enum FileType {
    IMAGE("png", "jpg", "jpeg", "gif", "bmp", "webp"),
    AUDIO("mp3", "wav", "m4a", "flac", "ogg"),
    VIDEO("mp4", "mov", "avi", "mkv", "webm"),
    DOCUMENT("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx"),
    // OTHER has no extensions because it's never matched directly; it's the
    // fallback fromFileName returns for anything not listed above
    OTHER();

    /** The extensions (lowercase, without the dot) that count as this type. */
    private final List<String> extensions;

    /**
     * Takes the extensions as varargs so each constant can list as many as it
     * needs. List.of makes the list unchangeable once the enum is set up.
     */
    FileType(String... extensions) {
        this.extensions = List.of(extensions);
    }

    /**
     * Works out a file's type from its name, e.g. "campsite-map.PNG" -> IMAGE.
     * Returns OTHER if the name has no extension or the extension isn't in any
     * list. The extension is lowercased first so ".PNG" and ".png" match.
     * It loops over values() so adding a new type only means adding a constant;
     * this method doesn't need to change.
     */
    public static FileType fromFileName(String fileName) {

        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex == -1) {
            return OTHER;
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase();
        
        for (FileType type : FileType.values()) {
            if (type.extensions.contains(extension)) {
                return type;
            }
        }

        return OTHER;
    }
}
