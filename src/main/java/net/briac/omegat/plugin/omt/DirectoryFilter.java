package net.briac.omegat.plugin.omt;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;

public class DirectoryFilter implements DirectoryStream.Filter<Path> {

    private final List<Pattern> excludePatterns = new ArrayList<>();
    private final Path projectRoot;

    public DirectoryFilter(Path projectRoot, List<String> excludePatterns) {
        this.projectRoot = projectRoot;
        for (String e : excludePatterns) {
            this.excludePatterns.add(Pattern.compile(e));
        }
    }

    @Override
    public boolean accept(Path entry) throws IOException {
        String matchEntry = FilenameUtils.normalizeNoEndSeparator(projectRoot.relativize(entry).toString(), true);
        for (Pattern excludePattern : excludePatterns) {
            if (excludePattern.matcher(matchEntry).find()) {
                ManageOMTPackage.LOGGER.log(Level.INFO, String.format("Exclude entry [%s] from regex [%s]", matchEntry, excludePattern.pattern()));
                return false;
            }
        }
        return true;
    }
}
