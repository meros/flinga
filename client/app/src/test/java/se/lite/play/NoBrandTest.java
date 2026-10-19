package se.lite.play;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * B8 (owner: "Skippa namnet flinga i appen"): the name appears nowhere in a string the app shows. The
 * launcher label (res/values/strings.xml), log tags and the link scheme keep it. This reads the sources, so a new string
 * with the name fails at once, whichever screen it is on; the harness (`Screens.checkBrand`) checks what
 * is drawn.
 */
public class NoBrandTest {
    @Test
    public void noStringTheAppShowsNamesTheApp() throws Exception {
        File root = new File("src/main/java");
        List<String> found = new ArrayList<>();
        Pattern lit = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
        try (java.util.stream.Stream<java.nio.file.Path> files = Files.walk(root.toPath())) {
            for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                int n = 0;
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    n++;
                    String t = line.trim();
                    if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) continue; // comments
                    if (line.contains("Log.")) continue; // log tags are not shown
                    if (line.contains("static final String SCHEME")) continue; // the links' scheme (DeepLink, G6) is a protocol name, never drawn
                    Matcher m = lit.matcher(line);
                    while (m.find()) {
                        if (m.group(1).toLowerCase(java.util.Locale.ROOT).contains("flinga")) found.add(f.getFileName() + ":" + n + " \"" + m.group(1) + "\"");
                    }
                }
            }
        }
        assertTrue("B8: the name is in a string the app shows: " + found, found.isEmpty());
    }
}
