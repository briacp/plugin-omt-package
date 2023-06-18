/**************************************************************************
 OmegaT Plugin - OMT Package Manager

 Copyright (C) 2019 Briac Pilpré
 Home page: http://www.omegat.org/
 Support center: http://groups.yahoo.com/group/OmegaT/

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/
package net.briac.omegat.plugin.omt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.commons.lang3.StringUtils;
import org.omegat.core.data.ProjectProperties;
import org.omegat.util.OConsts;

import gen.core.project.Masks;
import gen.core.project.Omegat;
import gen.core.project.Project;

//import org.omegat.util.ProjectFileStorage.DEFAULT_FOLDER_MARKER;

/**
 * Copy of org.omegat.util.ProjectFileStorage just to be able to create an
 * project.properties.
 * <p>
 * If/When the OMT is included in the main app, this class will be deprecated by
 * adding a method such as
 * <p>
 * public static void writeProjectFile(ProjectProperties props)throws Exception{
 * File outFile=new File(props.getProjectRoot(),OConsts.FILE_PROJECT);
 * writeProjectFile(props,outFile); }
 */
public class ProjectFileStorage {
    private static final JAXBContext CONTEXT;
    private static final String DEFAULT_FOLDER_MARKER = org.omegat.util.ProjectFileStorage.DEFAULT_FOLDER_MARKER;

    static {
        try {
            CONTEXT = JAXBContext.newInstance(Omegat.class);
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
    
    private ProjectFileStorage() {
        /* empty */
    }

    // Should be in org.omegat.util.ProjectFileStorage.writeProjectFile
    public static void writeProjectFile(ProjectProperties props, File outFile) throws IOException, JAXBException {
        writeProjectFile(props, outFile, false);
    }

    public static void writeProjectFile(ProjectProperties props, File outFile, boolean skipRepositories) throws IOException, JAXBException {
        String root = outFile.getAbsoluteFile().getParent();

        Omegat om = new Omegat();
        om.setProject(new Project());
        om.getProject().setVersion(OConsts.PROJ_CUR_VERSION);

        om.getProject().setSourceDir(getPathForStoring(root, props.getSourceRoot(), OConsts.DEFAULT_SOURCE));
        om.getProject().setSourceDirExcludes(new Masks());
        om.getProject().getSourceDirExcludes().getMask().addAll(props.getSourceRootExcludes());
        om.getProject().setTargetDir(getPathForStoring(root, props.getTargetRoot(), OConsts.DEFAULT_TARGET));
        om.getProject().setTmDir(getPathForStoring(root, props.getTMRoot(), OConsts.DEFAULT_TM));
        String glossaryDir = getPathForStoring(root, props.getGlossaryRoot(), OConsts.DEFAULT_GLOSSARY);
        om.getProject().setGlossaryDir(glossaryDir);

        // Compute glossary file location: must be relative to glossary root
        String glossaryFile = getPathForStoring(props.getGlossaryRoot(), props.getWriteableGlossary(), null);

        // The method props.isDefaultWriteableGlossaryFile() doesn't work well on WSL
        if (glossaryDir.equalsIgnoreCase(DEFAULT_FOLDER_MARKER)
                && new File(glossaryFile).getName().equals(OConsts.DEFAULT_W_GLOSSARY)) {
            glossaryFile = DEFAULT_FOLDER_MARKER;
        }
        om.getProject().setGlossaryFile(glossaryFile);

        om.getProject().setDictionaryDir(getPathForStoring(root, props.getDictRoot(), OConsts.DEFAULT_DICT));
        om.getProject().setSourceLang(props.getSourceLanguage().toString());
        om.getProject().setTargetLang(props.getTargetLanguage().toString());
        om.getProject().setSourceTok(props.getSourceTokenizer().getCanonicalName());
        om.getProject().setTargetTok(props.getTargetTokenizer().getCanonicalName());
        om.getProject().setSentenceSeg(props.isSentenceSegmentingEnabled());
        om.getProject().setSupportDefaultTranslations(props.isSupportDefaultTranslations());
        om.getProject().setRemoveTags(props.isRemoveTags());
        om.getProject().setExternalCommand(props.getExternalCommand());

        if (!skipRepositories && props.getRepositories() != null && !props.getRepositories().isEmpty()) {
            om.getProject().setRepositories(new Project.Repositories());
            om.getProject().getRepositories().getRepository().addAll(props.getRepositories());
        }

        Marshaller m = CONTEXT.createMarshaller();
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        m.marshal(om, outFile);
    }

    private static String getPathForStoring(String root, String absolutePath, String defaultName) throws IOException {
        if (defaultName != null
                && new File(absolutePath).getCanonicalPath().equals(new File(root, defaultName).getCanonicalPath())) {
            return DEFAULT_FOLDER_MARKER;
        }

        // Fall back to using the input path if all else fails.
        String result = absolutePath;
        try {
            // Path.normalize() will resolve any remaining "../"
            Path absPath = Paths.get(absolutePath).normalize();
            String rel = Paths.get(root).relativize(absPath).toString();
            if (StringUtils.countMatches(rel, ".." + File.separatorChar) <= OConsts.MAX_PARENT_DIRECTORIES_ABS2REL) {
                // Use the relativized path as it is "near" enough.
                result = rel;
            } else {
                result = absPath.toString();
            }
        } catch (IllegalArgumentException e) {
            /* empty, not sure if it still can be thrown */
        }
        return normalizeSlashes(result);
    }

    /**
     * Replace \ with / and remove / from the end if present. Within OmegaT we
     * generally require a / on the end of directories, but for storage we prefer no
     * trailing /.
     */
    static String normalizeSlashes(String path) {
        return withoutTrailingSlash(path.replace('\\', '/'));
    }

    static String withoutTrailingSlash(String path) {
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
