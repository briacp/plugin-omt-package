package net.briac.omegat.plugin.omt;

import org.omegat.util.Log;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class DirectoryFilter implements DirectoryStream.Filter<Path> {

    private List<Pattern> excludePatterns = new ArrayList<>();

    public DirectoryFilter(List<String> excludePatterns) {
        for (String e : excludePatterns) {
            this.excludePatterns.add(Pattern.compile(e));
        }
    }

    @Override
    public boolean accept(Path entry) throws IOException {
        //Log.log(String.format("filter\tentry:[%s]", entry));
        for (Pattern excludePattern : excludePatterns) {
            if (excludePattern.matcher(entry.toString()).find()) {
                Log.log(String.format("Excluded\t%s", entry));
                return false;
            }
        }
        return true;
    }
}
