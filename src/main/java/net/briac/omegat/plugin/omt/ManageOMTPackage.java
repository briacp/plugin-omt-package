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

import static org.omegat.core.Core.getMainWindow;

import java.awt.Cursor;
import java.awt.Desktop;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.apache.commons.io.FileUtils;
import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.data.ProjectFactory;
import org.omegat.core.data.ProjectProperties;
import org.omegat.core.events.IApplicationEventListener;
import org.omegat.core.segmentation.Segmenter;
import org.omegat.filters2.master.FilterMaster;
import org.omegat.gui.main.IMainMenu;
import org.omegat.gui.main.IMainWindow;
import org.omegat.gui.main.ProjectUICommands;
import org.omegat.gui.scripting.IScriptLogger;
import org.omegat.gui.scripting.ScriptItem;
import org.omegat.gui.scripting.ScriptRunner;
import org.omegat.util.FileUtil;
import org.omegat.util.Log;
import org.omegat.util.OConsts;
import org.omegat.util.Preferences;
import org.omegat.util.StaticUtils;
import org.omegat.util.gui.OmegaTFileChooser;
import org.omegat.util.gui.UIThreadsUtil;
import org.openide.awt.Mnemonics;

import groovyjarjarcommonscli.BasicParser;
import groovyjarjarcommonscli.CommandLine;
import groovyjarjarcommonscli.CommandLineParser;
import groovyjarjarcommonscli.HelpFormatter;
import groovyjarjarcommonscli.Option;
import groovyjarjarcommonscli.OptionBuilder;
import groovyjarjarcommonscli.Options;
import groovyjarjarcommonscli.ParseException;

public class ManageOMTPackage
{
    public static final String PLUGIN_VERSION = ManageOMTPackage.class.getPackage().getImplementationVersion();
    public static final String PROPERTY_POST_PACKAGE_SCRIPT = "post-package-script";

    public static final String OMT_EXTENSION = ".omt";
    public static final String IGNORE_FILE = ".empty";
    public static final String CONFIG_FILE = "omt-package-config.properties";

    public static final String PROPERTY_EXCLUDE = "exclude-pattern";
    public static final String DEFAULT_EXCLUDE = "\\.(zip|bak|omt|lck)$;\\.repositories";
    public static final String PROPERTY_OPEN_DIR = "open-directory-after-export";
    public static final String PROPERTY_GENERATE_TARGET = "generate-target-files";
    public static final String PROPERTY_PROMPT_DELETE_IMPORT = "prompt-remove-omt-after-import";
    protected static final Logger LOGGER = Logger.getLogger(ManageOMTPackage.class.getName());
    protected static final String OMT_PACKER_LOGNAME = "omt-packer.log";

    protected static final ResourceBundle res = ResourceBundle.getBundle("omt-package", Locale.getDefault());

    private static final ReentrantLock EXCLUSIVE_RUN_LOCK = new ReentrantLock();

    private static JMenuItem importOMT;
    private static JMenuItem exportOMT;
    private static JMenuItem exportDeleteOMT;

    private static boolean cliMode = false;

    private static Properties pluginProps = new Properties();
    private static FileWriter fhandler;

    @SuppressWarnings({ "static-access" })
    public static void main(String[] args) throws Exception
    {
        File configFile = new File(StaticUtils.getConfigDir(), CONFIG_FILE);
        String projectDirectoryName = null;
        String omtFilename = null;

        cliMode = true;

        // Parse the CLI options
        Options options = new Options();
        //@formatter:off
        Option configOpt = OptionBuilder
                .withLongOpt("config")
                .withArgName("property-file")
                .hasArg()
                .withDescription("use given file for configuration (default: " + configFile + ")")
                .withType(String.class)
                .create('c');

        Option configVerbose = OptionBuilder
                .withLongOpt("verbose")
                .withDescription("be extra verbose")
                .create('v');

        Option configQuiet = OptionBuilder
                .withLongOpt("quiet")
                .withDescription("be extra quiet")
                .create('q');

        Option configHelp = OptionBuilder
                .withLongOpt("help")
                .withDescription("print this message")
                .create('h');

        options.addOption(configOpt);
        options.addOption(configHelp);
        options.addOption(configVerbose);
        options.addOption(configQuiet);

        if (args.length == 0) {
            printCliHelp(options);
        }

        CommandLineParser parser = new BasicParser();
        try {
            // parse the command line arguments
            CommandLine commandLine = parser.parse(options, args);

            if (commandLine.hasOption("config")) {
                configFile = new File(commandLine.getOptionValue("config"));
            }

            if (commandLine.hasOption("verbose")) {
                Log.setLevel(Level.FINE);
                LOGGER.setLevel(Level.FINE);
            }
            if (commandLine.hasOption("quiet")) {
                Log.setLevel(Level.OFF);
            }
            if (commandLine.hasOption("help")) {
                printCliHelp(options);
            }

            String[] remainder = commandLine.getArgs();
            if (remainder == null || remainder.length == 0) {
                printCliHelp(options);
            }
            projectDirectoryName = remainder[0];

            if (remainder.length == 2) {
                omtFilename = remainder[1];
            }
        } catch (ParseException exp) {
            System.err.println("Invalid command line: " + exp.getMessage());
            System.exit(3);
        }

        File projectDir = new File(projectDirectoryName);
        if (!projectDir.exists() || !projectDir.canRead() || !projectDir.isDirectory()) {
            System.err.println("The omegat-project-directory must be a valid directory");
            System.exit(4);
        }

        File omtFile = null;
        if (omtFilename != null) {
            omtFile = new File(omtFilename);
        } else {
            omtFile = new File(projectDir.getParentFile(), projectDir.getName() + OMT_EXTENSION);
        }

        Log.log(res.getString("omt.menu.export"));
        loadPluginProps(configFile);
        Preferences.init();
        // Correctly load the project properties
        ProjectProperties props = org.omegat.util.ProjectFileStorage.loadProjectProperties(projectDir);
        createOmt(omtFile, props);
    }

    private static void printCliHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(150);
        formatter.printHelp("ManageOMTPackage [options] omegat-project-directory [omt-package-file]", options, false);
        System.exit(2);
    }

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

                exportDeleteOMT = new JMenuItem();
                Mnemonics.setLocalizedText(exportDeleteOMT, res.getString("omt.menu.export.delete"));
                exportDeleteOMT.addActionListener(e -> {
                    projectExportOMT(true);
                });
                projectMenu.add(exportDeleteOMT, startMenuIndex++);
                // projectMenu.add(new JPopupMenu.Separator(), startMenuIndex++);

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
        if (exportDeleteOMT != null) {
            exportDeleteOMT.setEnabled(isProjectLoaded);
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
                IMainWindow mainWindow = Core.getMainWindow();
                Cursor hourglassCursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
                Cursor oldCursor = mainWindow.getCursor();
                mainWindow.setCursor(hourglassCursor);
                showStatusMessage(res.getString("omt.status.importing_omt"));

                final File projectDir = extractFromOmt(omtFile);
                ProjectUICommands.projectOpen(projectDir);

                showStatusMessage(res.getString("omt.status.omt_imported"));
                mainWindow.setCursor(oldCursor);
                return null;
            }

            protected void done() {
                try {

                    if (Boolean.parseBoolean(pluginProps.getProperty(PROPERTY_PROMPT_DELETE_IMPORT, "false"))) {
                        //@formatter:off
                        int deletePackage = JOptionPane.showConfirmDialog(
                                getMainWindow().getApplicationFrame(),
                                res.getString("omt.dialog.delete_package"),
                                res.getString("omt.dialog.delete_package.title"),
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE);
                        //@formatter:on

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

    public static void projectExportOMT()
    {
        projectExportOMT(false);
    }

    public static void projectExportOMT(boolean deleteProject)
    {
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
        File defaultLocation = Core.getProject().getProjectProperties().getProjectRootDir();

        if (deleteProject) {
            // Since the project will be deleted, no point saving the OMT inside it.
            defaultLocation = defaultLocation.getParentFile();
        }

        ndm.setSelectedFile(new File(defaultLocation, zipName));
        int ndmResult = ndm.showSaveDialog(Core.getMainWindow().getApplicationFrame());
        if (ndmResult != OmegaTFileChooser.APPROVE_OPTION) {
            // user press 'Cancel' in project creation dialog
            return;
        }

        // add .omt extension if there is none
        final File omtFile = ndm.getSelectedFile().getName().toLowerCase(Locale.ENGLISH).endsWith(OMT_EXTENSION)
                ? ndm.getSelectedFile()
                : new File(ndm.getSelectedFile().getAbsolutePath() + OMT_EXTENSION);

        Log.log(String.format("Exporting OMT \"%s\"", omtFile.getAbsolutePath()));

        // Check and ask if the user wants to overwrite an existing package
        if (omtFile.exists()) {
            //@formatter:off
            int overwritePackage = JOptionPane.showConfirmDialog(
                    getMainWindow().getApplicationFrame(),
                    res.getString("omt.dialog.overwrite_package"),
                    res.getString("omt.dialog.overwrite_package.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
          //@formatter:on

            if (overwritePackage == 0) {
                Log.log("Overwriting existing package");
            } else {
                Log.log("Not overwriting existing package");
                return;
            }
        }

        File projectDir = Core.getProject().getProjectProperties().getProjectRootDir();

        new SwingWorker<Void, Void>()
        {
            protected Void doInBackground() throws Exception
            {
                IMainWindow mainWindow = Core.getMainWindow();
                Cursor hourglassCursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
                Cursor oldCursor = mainWindow.getCursor();
                mainWindow.setCursor(hourglassCursor);

                mainWindow.showStatusMessageRB("MW_STATUS_SAVING");
                ManageOMTPackage.executeExclusively(true, () -> {
                    Core.getProject().saveProject(true);
                });

                if (Boolean.parseBoolean(pluginProps.getProperty(PROPERTY_GENERATE_TARGET, "false"))) {
                    ManageOMTPackage.executeExclusively(true, () -> {
                        try {
                            Core.getProject().compileProject(".*");
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        } finally {
                            mainWindow.setCursor(oldCursor);
                        }
                    });
                }

                showStatusMessage(res.getString("omt.status.exporting_omt"));
                createOmt(omtFile, Core.getProject().getProjectProperties());

                if (deleteProject) {
                    ManageOMTPackage.executeExclusively(true, () -> {
                        ProjectFactory.closeProject();
                        Core.setFilterMaster(new FilterMaster(Preferences.getFilters()));
                        Core.setSegmenter(new Segmenter(Preferences.getSRX()));
                    });
                }

                // Display the containing folder on the desktop
                if (Boolean.parseBoolean(pluginProps.getProperty(PROPERTY_OPEN_DIR, "false"))) {
                    Desktop.getDesktop().open(omtFile.getParentFile());
                }

                showStatusMessage(res.getString("omt.status.omt_exported"));
                mainWindow.setCursor(oldCursor);
                return null;
            }

            protected void done()
            {
                try {
                    get();

                    if (deleteProject) {
                        showStatusMessage(res.getString("omt.status.delete_project"));
                        Log.log("Deleting project directory...");
                        Path pathToBeDeleted = projectDir.toPath();

                        Files.walk(pathToBeDeleted).sorted(Comparator.reverseOrder()).map(Path::toFile)
                                .forEach(File::delete);

                        if (Files.exists(pathToBeDeleted)) {
                            Log.log("Couldn't delete project directory...");
                        }
                    }

                    SwingUtilities.invokeLater(Core.getEditor()::requestFocus);
                } catch (Exception ex) {
                    Log.logErrorRB(ex, "PP_ERROR_UNABLE_TO_READ_PROJECT_FILE");
                    Core.getMainWindow().displayErrorRB(ex, "PP_ERROR_UNABLE_TO_READ_PROJECT_FILE");
                }
            }
        }.execute();
    }

    private static void loadPluginProps()
    {
        loadPluginProps(new File(StaticUtils.getConfigDir(), CONFIG_FILE));
    }

    private static void loadPluginProps(File propFile)
    {
        pluginProps = new Properties();

        if (!propFile.exists()) {
            Log.logDebug(LOGGER, "No app plugin properties [{0}], creating one...", propFile.getAbsolutePath());
            try {
                FileUtils.copyInputStreamToFile(ManageOMTPackage.class.getResourceAsStream("/" + CONFIG_FILE),
                        propFile);
            } catch (IOException e) {
                Log.log(e);
                return;
            }
        }
        try (FileInputStream inStream = new FileInputStream(propFile)) {
            pluginProps.load(inStream);
            Log.logDebug(LOGGER, "OMT App Plugin Properties");
            if (LOGGER.isLoggable(Level.FINE)) {
                pluginProps.list(System.out);
            }
        } catch (IOException e) {
            Log.log(String.format("Could not load plugin property file \"%s\"", propFile.getAbsolutePath()));
        }

        if (Core.getProject() == null || !Core.getProject().isProjectLoaded()) {
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
                Log.log(String.format("Could not load project plugin property file \"%s\"",
                        propFile.getAbsolutePath()));
            }
        } else {
            Log.logDebug(LOGGER, "No project plugin properties [{0}]", propFile.getAbsolutePath());
        }
    }

    /**
     * It creates project internals from OMT zip file.
     */
    public static File extractFromOmt(File omtFile) throws Exception
    {
        String omtName = omtFile.getName().replaceAll("\\" + OMT_EXTENSION + "$", "");

        ZipFile zip = new ZipFile(omtFile);

        ZipEntry e = zip.getEntry(OConsts.FILE_PROJECT);
        if (e == null) {
            zip.close();
            throw new Exception(res.getString("omt.invalid.package"));
        }

        // Check if we're inside a project folder
        File projectDir = new File(omtFile.getParent(), OConsts.FILE_PROJECT);

        Log.log(String.format("Checking for project file \"%s\": %s", projectDir.getAbsolutePath(),
                projectDir.exists()));

        if (projectDir.exists()) {
            Log.log(res.getString("omt.update.package"));
            projectDir = omtFile.getParentFile();
        } else {
            Log.log(res.getString("omt.new.package"));
            projectDir = new File(omtFile.getParentFile(), omtName);
            projectDir.mkdirs();
        }

        for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements();) {
            e = en.nextElement();

            File outFile = new File(projectDir, e.getName());

            if (outFile.getName().equals(OConsts.STATUS_EXTENSION)
                    && outFile.getParentFile().getName().equals(OConsts.DEFAULT_INTERNAL) && outFile.exists()) {
                // Maybe not overwrite project_save.tmx if it already exists? Ask the user?
                //@formatter:off
                int overwriteSave = JOptionPane.showConfirmDialog(
                        getMainWindow().getApplicationFrame(),
                        res.getString("omt.dialog.overwrite_project_save"),
                        res.getString("omt.dialog.overwrite_project_save.title"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                //@formatter:on

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
                if (outFile.getName().equals(IGNORE_FILE)) {
                    outFile.getParentFile().mkdirs();
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

    public static void createOmt(final File omtZip, final ProjectProperties props) throws Exception
    {
        Path path = Paths.get(props.getProjectRootDir().getAbsolutePath());
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path \"" + path + "\" must be a directory.");
        }

        List<String> listExcludes = Arrays
                .asList(pluginProps.getProperty(PROPERTY_EXCLUDE, DEFAULT_EXCLUDE).split(";"));

        DirectoryStream.Filter<Path> filter = new DirectoryFilter(path, listExcludes);

        String logFile = props.getProjectInternal() + OMT_PACKER_LOGNAME;
        fhandler = new FileWriter(logFile, true);

        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(omtZip));
        String username = Preferences.getPreferenceDefault(Preferences.TEAM_AUTHOR, System.getProperty("user.name"));
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        omtPackLog("------------------------------------");
        omtPackLog(String.format("Packing timestamp: %s", timestamp));
        omtPackLog(String.format("OMT plugin version: %s", PLUGIN_VERSION));
        omtPackLog(String.format("User ID: \"%s\"", username));
        omtPackLog(String.format("Project name: [%s]", path));
        omtPackLog(String.format("Package name: [%s]", omtZip.getAbsolutePath()));

        int addedFiles = 0;
        try (ZipOutputStream out = new ZipOutputStream(bos)) {
            addedFiles = addZipDir(out, null, path, props, filter);

            omtPackLog(String.format("Added %s files to the Zip.", addedFiles));
            fhandler.close();

            // Add logfile
            out.putNextEntry(new ZipEntry(OConsts.DEFAULT_INTERNAL + '/' + OMT_PACKER_LOGNAME));
            Files.copy(Paths.get(logFile), out);
            out.closeEntry();
        }

        String postPackageScript = pluginProps.getProperty(PROPERTY_POST_PACKAGE_SCRIPT);
        if (postPackageScript != null) {
            runScript(new File(Preferences.getPreference(Preferences.SCRIPTS_DIRECTORY), postPackageScript));
        }

        if (cliMode) {
            Log.log(res.getString("omt.dialog.overwrite_package.created"));
            return;
        }

        JOptionPane.showMessageDialog(getMainWindow().getApplicationFrame(),
                res.getString("omt.dialog.overwrite_package.created"),
                res.getString("omt.dialog.overwrite_package.created.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    private static void runScript(File scriptFile)
    {
        if (scriptFile.isFile() && scriptFile.exists()) {
            HashMap<String, Object> bindings = new HashMap<>();
            bindings.put("omtPackageFile", scriptFile);

            bindings.put(ScriptRunner.VAR_CONSOLE, new IScriptLogger()
            {
                @Override
                public void print(Object o)
                {
                    Log.log(o.toString());
                }

                @Override
                public void println(Object o)
                {
                    Log.log(o.toString());
                }

                @Override
                public void clear()
                {
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

    private static final int addZipDir(final ZipOutputStream out, final Path root, final Path dir,
            final ProjectProperties props, DirectoryStream.Filter<Path> filter) throws IOException
    {
        int addedFiles = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filter)) {
            for (Path child : stream) {
                final Path childPath = child.getFileName();

                // Skip projects inside projects
                if (Files.isDirectory(child) && new File(child.toFile(), OConsts.FILE_PROJECT).exists()) {
                    omtPackLog(String.format("The directory \"%s\" appears to be an OmegaT project, we'll skip it.",
                            child.toFile().getAbsolutePath()));
                    continue;
                }

                if (root == null && childPath.endsWith(OConsts.FILE_PROJECT)) {
                    // Special case - when a project is opened, the project file is locked and
                    // can't be copied directly. To avoid this, we make a temp copy.
                    // We name it with a .bak extension to make sure it's not included in the
                    // package.
                    File tmpProjectFile = File.createTempFile("omt", OConsts.BACKUP_EXTENSION,
                            props.getProjectRootDir());
                    try {
                        ProjectFileStorage.writeProjectFile(props, tmpProjectFile);
                    } catch (Exception e) {
                        throw new IOException(e);
                    }
                    omtPackLog(String.format("addZipDir\tproject\t[%s]", OConsts.FILE_PROJECT));
                    out.putNextEntry(new ZipEntry(OConsts.FILE_PROJECT));
                    Files.copy(Paths.get(tmpProjectFile.getAbsolutePath()), out);
                    addedFiles++;
                    out.closeEntry();
                    boolean isTmpDeleted = tmpProjectFile.delete();
                    if (!isTmpDeleted) {
                        Log.log(String.format("Could not delete temporary file \"%s\". You can safely delete it.",
                                tmpProjectFile.getAbsolutePath()));
                    }
                    continue;
                }

                Path entry = root == null ? childPath : Paths.get(root.toString(), childPath.toString());
                if (Files.isDirectory(child)) {
                    // Before recursing, we add a ZipEntry for the directory to allow
                    // empty dirs.
                    boolean emptyDir = child.toFile().listFiles().length == 0;
                    if (emptyDir) {
                        createEmptyFile(out, entry);
                    }
                    int added = addZipDir(out, entry, child, props, filter);
                    if (!emptyDir && added == 0) {
                        createEmptyFile(out, entry);
                    }
                    addedFiles += added;
                } else {
                    omtPackLog(String.format("addZipDir\tfile\t[%s]", entry));
                    out.putNextEntry(new ZipEntry(entry.toString().replace("\\", "/")));
                    Files.copy(child, out);
                    addedFiles++;
                    out.closeEntry();
                }
            }
        }
        return addedFiles;
    }

    private static void createEmptyFile(final ZipOutputStream out, Path entry) throws IOException
    {
        String emptyDirFile = entry.toString() + File.separatorChar + IGNORE_FILE;
        omtPackLog(String.format("createEmptyFile\t[%s]", emptyDirFile));
        out.putNextEntry(new ZipEntry(emptyDirFile.replace("\\", "/")));
        out.closeEntry();
    }

    /**
     * This is copied from org.omegat.core.Core.executeExclusively(boolean,
     * RunnableWithException) to allow the plugin to run in old (pre 4.3.0) and new
     * versions of OmegaT.
     */
    public static void executeExclusively(boolean waitForUnlock, RunnableWithException run) throws Exception
    {
        if (!EXCLUSIVE_RUN_LOCK.tryLock(waitForUnlock ? 180000 : 1, TimeUnit.MILLISECONDS)) {
            Exception ex = new TimeoutException("Timeout waiting for previous exclusive execution");
            Exception cause = new Exception("Previous exclusive execution");
            if (runningStackTrace != null) {
                cause.setStackTrace(runningStackTrace);
                ex.initCause(cause);
            }
            throw ex;
        }
        try {
            runningStackTrace = new Exception().getStackTrace();
            run.run();
        } finally {
            runningStackTrace = null;
            EXCLUSIVE_RUN_LOCK.unlock();
        }
    }

    private static StackTraceElement[] runningStackTrace;

    public interface RunnableWithException
    {
        void run() throws Exception;
    }

    protected static void omtPackLog(String msg) throws IOException
    {
        Log.logDebug(LOGGER, msg);
        fhandler.write(msg + "\n");
    }

    /** Hack to display a message other than a Bundle.properties string */
    private static void showStatusMessage(String msg)
    {
        // app-version-template-pretty={0} {1}
        Core.getMainWindow().showStatusMessageRB("app-version-template-pretty", msg, "");
    }

}
