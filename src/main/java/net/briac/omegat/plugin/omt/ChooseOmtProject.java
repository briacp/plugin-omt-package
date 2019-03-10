package net.briac.omegat.plugin.omt;

import org.omegat.util.Preferences;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.util.Locale;

@SuppressWarnings("serial")
public final class ChooseOmtProject extends JFileChooser {
    public ChooseOmtProject(String dialogTitle) {
        super(Preferences.getPreference(Preferences.CURRENT_FOLDER));

        setMultiSelectionEnabled(false);
        setFileHidingEnabled(true);
        setFileSelectionMode(FILES_ONLY);
        setDialogTitle(dialogTitle);
        setAcceptAllFileFilterUsed(false);
        addChoosableFileFilter(new FileFilter() {

            @Override
            public String getDescription() {
                return ManageOMTPackage.res.getString("omt.chooser.filter");
            }

            @Override
            public boolean accept(File f) {
                return f.isFile() && f.getName().toLowerCase(Locale.ENGLISH).endsWith(ManageOMTPackage.OMT_EXTENSION);
            }
        });
    }

    @Override
    public boolean accept(File f) {
        if (f.isDirectory()) {
            return true;
        }
        return f.isFile() && f.getName().toLowerCase(Locale.ENGLISH).endsWith(ManageOMTPackage.OMT_EXTENSION);
    }
}
