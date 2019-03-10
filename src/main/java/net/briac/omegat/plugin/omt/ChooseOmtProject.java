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
