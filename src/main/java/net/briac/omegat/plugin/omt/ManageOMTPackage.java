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

import org.apache.commons.io.FileUtils;
import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.data.ProjectProperties;
import org.omegat.core.events.IApplicationEventListener;
import org.omegat.gui.main.IMainMenu;
import org.omegat.gui.main.IMainWindow;
import org.omegat.gui.main.ProjectUICommands;
import org.omegat.gui.scripting.IScriptLogger;
import org.omegat.gui.scripting.ScriptItem;
import org.omegat.gui.scripting.ScriptRunner;
import org.omegat.util.*;
import org.omegat.util.gui.OmegaTFileChooser;
import org.omegat.util.gui.UIThreadsUtil;
import org.openide.awt.Mnemonics;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.omegat.core.Core.getMainWindow;

public class ManageOMTPackage {

    public static final String PROPERTY_POST_PACKAGE_SCRIPT = "post-package-script";

    public static final String OMT_EXTENSION = ".omt";
    public static final String IGNORE_FILE = ".empty";
    public static final String CONFIG_FILE = "omt-package-config.properties";

    public static final String PROPERTY_EXCLUDE = "exclude-pattern";
    public static final String DEFAULT_EXCLUDE = "\\.(zip|bak|omt)$";
    public static final String PROPERTY_OPEN_DIR = "open-directory-after-export";
    public static final String PROPERTY_GENERATE_TARGET = "generate-target-files";
    public static final String PROPERTY_PROMPT_DELETE_IMPORT = "prompt-remove-omt-after-import";
    private static final Logger LOGGER = Logger.getLogger(ManageOMTPackage.class.getName());

    protected static final ResourceBundle res = ResourceBundle.getBundle("omt-package", Locale.getDefault());

    private static JMenuItem importOMT;
    private static JMenuItem exportOMT;

    private static Properties pluginProps = new Properties();

    public static void loadPlugins() {

        CoreEvents.registerProjectChangeListener(e -> onProjectStatusChanged(Core.getProject().isProjectLoaded()));

        CoreEvents.registerApplicationEventListener(new IApplicationEventListener() {
            @Override
            public void onApplicationStartup() {
                IMainMenu menu = getMainWindow().getMainMenu();
                JMenu projectMenu = menu.getProjectMenu();

                int startMenuIndex = projectMenu.getItemCount() - 6;

                importOMT = new JMenuItem();
                Mnemonics.setLocalizedText(importOMT, res.getString("omt.menu.import"));
                importOMT.addActionListener(e -> {
                    projectImportOMT();
                });

                projectMenu.add(new JPopupMenu.Separator(), startMenuIndex++);
                projectMenu.add(importOMT, startMenuIndex++);

                exportOMT = new JMenuItem();
                Mnemonics.setLocalizedText(exportOMT, res.getString("omt.menu.export"));
                exportOMT.addActionListener(e -> {
                    projectExportOMT();
                });
                projectMenu.add(exportOMT, startMenuIndex++);
                //projectMenu.add(new JPopupMenu.Separator(), startMenuIndex++);

                onProjectStatusChanged(false);
            }

            @Override
            public void onApplicationShutdown() {
            }
        });
    }

    public static void unloadPlugins() {
        /* empty */
    }

    private static void onProjectStatusChanged(boolean isProjectLoaded) {
        if (exportOMT != null) {
            exportOMT.setEnabled(isProjectLoaded);
        }
        if (importOMT != null) {
            importOMT.setEnabled(!isProjectLoaded);
        }
    }

    public static void projectImportOMT() {
        UIThreadsUtil.mustBeSwingThread();

        loadPluginProps();

        if (Core.getProject().isProjectLoaded()) {
            return;
        }

        ChooseOmtProject ndm = new ChooseOmtProject(res.getString("omt.chooser.import"));

        // ask for OMT file
        int ndmResult = ndm.showOpenDialog(Core.getMainWindow().getApplicationFrame());
        if (ndmResult != OmegaTFileChooser.APPROVE_OPTION) {
            // user press 'Cancel' in project creation dialog
            return;
        }
        final File omtFile = ndm.getSelectedFile();

        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {

                final File projectDir = extractFromOmt(omtFile);
                ProjectUICommands.projectOpen(projectDir);
                return null;
            }

            protected void done() {
                try {

                    if (Boolean.parseBoolean(pluginProps.getProperty(PROPERTY_PROMPT_DELETE_IMPORT, "false"))) {
                        int deletePackage = JOptionPane.showConfirmDialog(
                                getMainWindow().getApplicationFrame(),
                                res.getString("omt.dialog.delete_package"),
                                res.getString("omt.dialog.delete_package.title"),
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE);

                        if (deletePackage == 0) {
                            Log.log("Deleting imported package");
                            if (!omtFile.delete()) {
                                Log.log(String.format("Could not delete the file %s", omtFile.getAbsolutePath()));
                            }
                        } else {
                            Log.log("Keeping imported package");
                        }
                    }

                    get();
                    SwingUtilities.invokeLater(Core.getEditor()::requestFocus);
                } catch (Exception ex) {
                    Log.logErrorRB(ex, "PP_ERROR_UNABLE_TO_READ_PROJECT_FILE");
                    getMainWindow().displayErrorRB(ex, "PP_ERROR_UNABLE_TO_READ_PROJECT_FILE");
                }
            }
        }.execute();
    }

    public static void projectExportOMT() {
        UIThreadsUtil.mustBeSwingThread();

        loadPluginProps();

        if (!Core.getProject().isProjectLoaded()) {
            return;
        }

        // commit the current entry first
        Core.getEditor().commitAndLeave();

        ChooseOmtProject ndm = new ChooseOmtProject(res.getString("omt.chooser.export"));

        // ask for new OMT file
        // default name
        String zipName = Core.getProject().getProjectProperties().getProjectName() + OMT_EXTENSION;

        // By default, save inside the project
        ndm.setSelectedFile(
                new File(Core.getProject().getProjectProperties().getProjectRootDir(), zipName));
        int ndmResult = ndm.showSaveDialog(Core.getMainWindow().getApplicationFrame());
        if (ndmResult != OmegaTFileChooser.APPROVE_OPTION) {
            // user press 'Cancel' in project creation dialog
            return;
        }

        // add .zip extension if there is no
        final File omtFile = ndm.getSelectedFile().getName().toLowerCase(Locale.ENGLISH)
                .endsWith(OMT_EXTENSION) ? ndm.getSelectedFile()
                : new File(ndm.getSelectedFile().getAbsolutePath() + OMT_EXTENSION);

        Log.log(String.format("Exporting OMT \"%s\"", omtFile.getAbsolutePath()));

        // Check and ask if the user wants to overwrite an existing package
        if (omtFile.exists()) {
            int overwritePackage = JOptionPane.showConfirmDialog(
                    getMainWindow().getApplicationFrame(),
                    res.getString("omt.dialog.overwrite_package"),
                    res.getString("omt.dialog.overwrite_package.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (overwritePackage == 0) {
                Log.log("Overwriting existing package");
            } else {
                Log.log("Not overwriting existing package");
                return;
            }

        }

        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception {
                IMainWindow mainWindow = Core.getMainWindow();
                Cursor hourglassCursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
                Cursor oldCursor = mainWindow.getCursor();
                mainWindow.setCursor(hourglassCursor);

                mainWindow.showStatusMessageRB("MW_STATUS_SAVING");

                Core.executeExclusively(true, () -> {
                    Core.getProject().saveProject(true);
                });
                
                if (Boolean.parseBoolean(pluginProps.getProperty(PROPERTY_GENERATE_TARGET, "false"))) {
                    Core.executeExclusively(true, () -> {
                        try {
                            Core.getProject().compileProject(".*");
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                }

                createOmt(omtFile, Core.getProject().getProjectProperties());

                // Display the containing folder on the desktop
                if (Boolean.parseBoolean(pluginProps.getProperty(PROPERTY_OPEN_DIR, "false"))) {
                    Desktop.getDesktop().open(omtFile.getParentFile());
                }

                mainWindow.showStatusMessageRB("MW_STATUS_SAVED");
                mainWindow.setCursor(oldCursor);
                return null;
            }

            protected void done() {
                try {
                    get();
                    SwingUtilities.invokeLater(Core.getEditor()::requestFocus);
                } catch (Exception ex) {
                    Log.logErrorRB(ex, "PP_ERROR_UNABLE_TO_READ_PROJECT_FILE");
                    Core.getMainWindow().displayErrorRB(ex, "PP_ERROR_UNABLE_TO_READ_PROJECT_FILE");
                }
            }
        }.execute();
    }

    private static void loadPluginProps() {
        pluginProps = new Properties();
        File propFile = new File(StaticUtils.getConfigDir(), CONFIG_FILE);
        if (!propFile.exists()) {
            Log.logDebug(LOGGER, "No app plugin properties [{0}], creating one...", propFile.getAbsolutePath());
            try {
                FileUtils.copyInputStreamToFile(
                        ManageOMTPackage.class.getResourceAsStream("/" + CONFIG_FILE),
                        propFile
                );
            } catch (IOException e) {
                Log.log(e);
                return;
            }
        }
        try (FileInputStream inStream = new FileInputStream(propFile)) {
            pluginProps.load(inStream);
            Log.logDebug(LOGGER, "OMT App Plugin Properties");
            pluginProps.list(System.out);
        } catch (IOException e) {
            Log.log(String.format("Could not load plugin property file \"%s\"", propFile.getAbsolutePath()));
        }

        if (!Core.getProject().isProjectLoaded()) {
            // No project specific properties!
            return;
        }

        propFile = new File(Core.getProject().getProjectProperties().getProjectRootDir(), CONFIG_FILE);
        if (propFile.exists()) {
            try (FileInputStream inStream = new FileInputStream(propFile)) {
                pluginProps.load(inStream);
                Log.logDebug(LOGGER, "OMT Project Plugin Properties");
                pluginProps.list(System.out);
            } catch (IOException e) {
                Log.log(String.format("Could not load project plugin property file \"%s\"", propFile.getAbsolutePath()));
            }
        } else {
            Log.logDebug(LOGGER, "No project plugin properties [{0}]", propFile.getAbsolutePath());
        }
    }

    /**
     * It creates project internals from OMT zip file.
     */
    public static File extractFromOmt(File omtFile) throws Exception {
        String omtName = omtFile.getName().replaceAll("\\" + OMT_EXTENSION + "$", "");

        ZipFile zip = new ZipFile(omtFile);

        ZipEntry e = zip.getEntry(OConsts.FILE_PROJECT);
        if (e == null) {
            zip.close();
            throw new Exception(res.getString("omt.invalid.package"));
        }

        // Check if we're inside a project folder
        File projectDir = new File(omtFile.getParent(), OConsts.FILE_PROJECT);

        Log.log(String.format("Checking for project file \"%s\": %s", projectDir.getAbsolutePath(), projectDir.exists()));

        if (projectDir.exists()) {
            Log.log(res.getString("omt.update.package"));
            projectDir = omtFile.getParentFile();
        } else {
            Log.log(res.getString("omt.new.package"));
            projectDir = new File(omtFile.getParentFile(), omtName);
            projectDir.mkdirs();
        }

        for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements(); ) {
            e = en.nextElement();

            File outFile = new File(projectDir, e.getName());

            if (outFile.getName().equals(OConsts.STATUS_EXTENSION) &&
                    outFile.getParentFile().getName().equals(OConsts.DEFAULT_INTERNAL) &&
                    outFile.exists()) {
                // Maybe not overwrite project_save.tmx if it already exists? Ask the user?
                int overwriteSave = JOptionPane.showConfirmDialog(
                        getMainWindow().getApplicationFrame(),
                        res.getString("omt.dialog.overwrite_project_save"),
                        res.getString("omt.dialog.overwrite_project_save.title"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);

                if (overwriteSave == 0) {
                    // Make a backup even if the user want to overwrite, to be on the safe side.
                    Log.log("Overwriting project_save.tmx");
                    final File f = new File(new File(projectDir, OConsts.DEFAULT_INTERNAL), OConsts.STATUS_EXTENSION);
                    Log.log(String.format("Backuping project file \"%s\"", f.getAbsolutePath()));
                    FileUtil.backupFile(f);
                } else {
                    Log.log("Skipping project_save.tmx");
                    continue;
                }
            }

            if (e.isDirectory()) {
                outFile.mkdirs();
            } else {
                if (e.getName().equals(IGNORE_FILE)) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(e)) {
                    try {
                        org.apache.commons.io.FileUtils.copyInputStreamToFile(in, outFile);
                    } catch (IOException ex) {
                        Log.log(String.format("Error unzipping file \"%s\": %s", outFile, ex.getMessage()));
                    }
                }
            }
        }

        zip.close();

        return projectDir;
    }

    public static void createOmt(final File omtZip, final ProjectProperties props) throws Exception {
        Path path = Paths.get(props.getProjectRootDir().getAbsolutePath());
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path must be a directory.");
        }

        List<String> listExcludes = Arrays.asList(pluginProps.getProperty(PROPERTY_EXCLUDE, DEFAULT_EXCLUDE).split(";"));

        DirectoryStream.Filter<Path> filter = new DirectoryFilter(listExcludes);

        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(omtZip));
        Log.log(String.format("Zipping to file [%s]", omtZip.getAbsolutePath()));
        try (ZipOutputStream out = new ZipOutputStream(bos)) {
            addZipDir(out, null, path, props, filter);
        }

        String postPackageScript = pluginProps.getProperty(PROPERTY_POST_PACKAGE_SCRIPT);
        if (postPackageScript != null) {
            runScript(new File(Preferences.getPreference(Preferences.SCRIPTS_DIRECTORY), postPackageScript));
        }

        JOptionPane.showMessageDialog(
                getMainWindow().getApplicationFrame(),
                res.getString("omt.dialog.overwrite_package.created"),
                res.getString("omt.dialog.overwrite_package.created.title"),
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private static void runScript(File scriptFile) {
        if (scriptFile.isFile() && scriptFile.exists()) {
            HashMap<String, Object> bindings = new HashMap<>();
            bindings.put("omtPackageFile", scriptFile);

            bindings.put(ScriptRunner.VAR_CONSOLE, new IScriptLogger() {
                @Override
                public void print(Object o) {
                    Log.log(o.toString());
                }

                @Override
                public void println(Object o) {
                    Log.log(o.toString());
                }

                @Override
                public void clear() {
                    /* empty */
                }
            });

            try {
                String result = ScriptRunner.executeScript(new ScriptItem(scriptFile), bindings);
                Log.log(result);
            } catch (Exception ex) {
                Log.log(ex);
            }
        } else {
            Log.log(String.format("No script file \"%s\".", scriptFile.getAbsolutePath()));
        }
    }

    private static final void addZipDir(final ZipOutputStream out, final Path root, final Path dir,
                                        final ProjectProperties props, DirectoryStream.Filter<Path> filter) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filter)) {
            for (Path child : stream) {
                final Path childPath = child.getFileName();

                // Skip projects inside projects
                if (Files.isDirectory(child) && new File(child.toFile(), OConsts.FILE_PROJECT).exists()) {
                    Log.log(String.format("The directory \"%s\" appears to be an OmegaT project, we'll skip it.", child.toFile().getAbsolutePath()));
                    continue;
                }

                if (root == null && childPath.endsWith(OConsts.FILE_PROJECT)) {
                    // Special case - when a project is opened, the project file is locked and
                    // can't be copied directly. To avoid this, we make a temp copy.
                    // We name it with a .bak extension to make sure it's not included in the package.
                    File tmpProjectFile = File.createTempFile("omt", OConsts.BACKUP_EXTENSION, props.getProjectRootDir());
                    try {
                        ProjectFileStorage.writeProjectFile(props, tmpProjectFile);
                    } catch (Exception e) {
                        throw new IOException(e);
                    }
                    Log.logDebug(LOGGER, "addZipDir\tproject\t[{0}]", OConsts.FILE_PROJECT);
                    out.putNextEntry(new ZipEntry(OConsts.FILE_PROJECT));
                    Files.copy(Paths.get(tmpProjectFile.getAbsolutePath()), out);
                    out.closeEntry();
                    boolean isTmpDeleted = tmpProjectFile.delete();
                    if (!isTmpDeleted) {
                        Log.log(String.format("Could not delete temporary file \"%s\". You can safely delete it.", tmpProjectFile.getAbsolutePath()));
                    }
                    continue;
                }

                Path entry = root == null ? childPath : Paths.get(root.toString(), childPath.toString());
                if (Files.isDirectory(child)) {
                    // Before recursing, we add a ZipEntry for the directory to allow
                    // empty dirs.
                    if (child.toFile().listFiles().length == 0) {
                        String emptyDirFile = entry.toString() + File.separatorChar + IGNORE_FILE;
                        Log.logDebug(LOGGER, "addZipDir\tempty\t[{0}]", emptyDirFile);
                        out.putNextEntry(new ZipEntry(emptyDirFile.replace("\\", "/")));
                        out.closeEntry();
                    }
                    addZipDir(out, entry, child, props, filter);
                } else {
                    Log.logDebug(LOGGER, "addZipDir\tfile\t[{0}]", entry);
                    out.putNextEntry(new ZipEntry(entry.toString().replace("\\", "/")));
                    Files.copy(child, out);
                    out.closeEntry();
                }
            }
        }
    }

}
