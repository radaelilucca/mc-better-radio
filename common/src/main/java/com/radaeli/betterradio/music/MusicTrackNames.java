package com.radaeli.betterradio.music;

import java.util.Locale;
import java.util.Set;

/** Creates readable fallback titles for concrete music files without localized song metadata. */
public final class MusicTrackNames {
    private static final Set<String> LOWERCASE_WORDS = Set.of(
            "a", "an", "and", "as", "at", "but", "by", "for", "in", "of", "on", "or", "the", "to", "via");

    private MusicTrackNames() { }

    public static String backgroundTitle(MusicTrack track) {
        String id = track.id();
        int pathStart = id.indexOf(':') + 1;
        String path = id.substring(pathStart);
        int fileStart = path.lastIndexOf('/') + 1;
        String fileName = path.substring(fileStart).replace('_', ' ').replace('-', ' ').trim();
        if (fileName.isEmpty()) return id;

        String title = titleCase(fileName);
        String gamePath = "music/game/";
        int gamePathStart = path.indexOf(gamePath);
        if (gamePathStart < 0) return title;

        String gameTrackPath = path.substring(gamePathStart + gamePath.length());
        int categoryEnd = gameTrackPath.indexOf('/');
        if (categoryEnd < 0) return title;

        String category = gameTrackPath.substring(0, categoryEnd).replace('_', ' ').replace('-', ' ').trim();
        return category.isEmpty() ? title : titleCase(category) + " - " + title;
    }

    private static String titleCase(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) title.append(' ');
            String word = words[i];
            if (i > 0 && LOWERCASE_WORDS.contains(word)) {
                title.append(word);
            } else {
                title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return title.toString();
    }
}
