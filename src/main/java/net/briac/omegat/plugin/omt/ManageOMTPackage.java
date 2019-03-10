package net.briac.omegat.plugin.omt;

import org.omegat.core.Core;
import org.omegat.core.CoreEvents;
import org.omegat.core.data.ProjectProperties;
import org.omegat.core.events.IApplicationEventListener;
import org.omegat.gui.main.IMainMenu;
import org.omegat.gui.main.IMainWindow;
import org.omegat.gui.main.ProjectUICommands;
import org.omegat.util.FileUtil;
import org.omegat.util.Log;
import org.omegat.util.OConsts;
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
import java.util.Enumeration;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.omegat.core.Core.getMainWindow;

public class ManageOMTPackage {

    public static final String OMT_EXTENSION = ".omt";
    public static final String IGNORE_FILE = ".ignore";
    static final ResourceBundle res = ResourceBundle.getBundle("omt-package", Locale.getDefault());
    private static JMenuItem importOMT;
    private static JMenuItem exportOMT;

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
        exportOMT.setEnabled(isProjectLoaded);
        importOMT.setEnabled(!isProjectLoaded);
    }

    public static void projectImportOMT() {
        UIThreadsUtil.mustBeSwingThread();

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
                    get();
                    SwingUtilities.invokeLater(Core.getEditor()::requestFocus);
                } catch (Exception ex) {
                    Log.logErrorRB(ex, "PP_ERROR_UNABLE_TO_READ_PROJECT_FILE");
                    Core.getMainWindow().displayErrorRB(ex, "PP_ERROR_UNABLE_TO_READ_PROJECT_FILE");
                }
            }
        }.execute();
    }

    public static void projectExportOMT() {
        UIThreadsUtil.mustBeSwingThread();

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

        Log.log("\n\n*******\n********\n");

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
                    try {
                        Core.getProject().compileProject(".*");
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });

                createOmt(omtFile, Core.getProject().getProjectProperties());

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

        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(omtZip));

        try (ZipOutputStream out = new ZipOutputStream(bos)) {
            addZipDir(out, null, path, props);
        }

        JOptionPane.showMessageDialog(
                getMainWindow().getApplicationFrame(),
                res.getString("omt.dialog.overwrite_package.created"),
                res.getString("omt.dialog.overwrite_package.created.title"),
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private static final void addZipDir(final ZipOutputStream out, final Path root, final Path dir,
                                        final ProjectProperties props) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                final Path childPath = child.getFileName();

                final String name = childPath.toFile().getName();
                if (name.endsWith(OConsts.BACKUP_EXTENSION) || name.endsWith(OMT_EXTENSION)
                ) {
                    // Skip .bak and .omt files
                    continue;
                }
                if (childPath.endsWith(OConsts.FILE_PROJECT)) {
                    // Special case - when a project is opened, the project file is locked and
                    // can't be copied directly. To avoid this, we make a temp copy.
                    File tmpProjectFile = File.createTempFile("omt", null, props.getProjectRootDir());
                    try {
                        ProjectFileStorage.writeProjectFile(props, tmpProjectFile);
                    } catch (Exception e) {
                        throw new IOException(e);
                    }
                    out.putNextEntry(new ZipEntry(OConsts.FILE_PROJECT));
                    Files.copy(Paths.get(tmpProjectFile.getAbsolutePath()), out);
                    out.closeEntry();
                    tmpProjectFile.delete();
                    continue;
                }

                Path entry = root == null ? childPath : Paths.get(root.toString(), childPath.toString());
                if (Files.isDirectory(child)) {
                    // Before recursing, we add a ZipEntry for the directory to allow
                    // empty dirs.
                    if (child.toFile().listFiles().length == 0) {
                        out.putNextEntry(new ZipEntry(name + File.separatorChar + IGNORE_FILE));
                        out.closeEntry();
                    }
                    addZipDir(out, entry, child, props);
                } else {
                    out.putNextEntry(new ZipEntry(entry.toString()));
                    Files.copy(child, out);
                    out.closeEntry();
                }
            }
        }
    }

}
