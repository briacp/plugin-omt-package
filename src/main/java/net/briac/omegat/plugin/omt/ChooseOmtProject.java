package net.briac.omegat.plugin.omt;

import org.omegat.util.OStrings;
import org.omegat.util.Preferences;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.util.Locale;

@SuppressWarnings("serial")
public final class ChooseOmtProject extends JFileChooser {
    public ChooseOmtProject() {
        super(Preferences.getPreference(Preferences.CURRENT_FOLDER));

        setMultiSelectionEnabled(false);
        setFileHidingEnabled(true);
        setFileSelectionMode(FILES_ONLY);
        setDialogTitle(OStrings.getString("PP_OMT_OPEN"));
        setAcceptAllFileFilterUsed(false);
        addChoosableFileFilter(new FileFilter() {

            @Override
            public String getDescription() {
                return OStrings.getString("PP_OMT_OPEN_FILTER");
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
