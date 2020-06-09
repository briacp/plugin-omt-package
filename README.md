# OMT OmegaT Package Plugin

This plugin introduce an option to import and export OmegaT packages. These packages are made so that it can be easier
to share OmegaT projects between people without using team projects.

At the moment, it is simply a zipped copy of the project, excluding some files.

## Installation

You can get a plugin jar file from zip/tgz distribution file.
OmegaT plugin should be placed in `$HOME/.omegat/plugin` or `C:\Program Files\OmegaT\plugin`
depending on your operating system.

## Configuration

This plugin uses a property file, either located in `$HOME/.omegat/omt-package-config.properties` or
at the root of the project, with the same name `omt-package-config.properties`. All the properties
redefined in the project folder will override those in the `.omegat` folder.

The default configuration looks like the following:

```
#
# OmegaT Plugin - OMT Package Manager
#
#    omt-package-config.properties
#

# List of regex pattern (separated by ";") of files to exclude when
# generating an OMT package. (default: "\\.(zip|bak|omt)$")
exclude-pattern=\\.(zip|bak|omt)$

# If set to true, the folder containing the exported OMT will be
# displayed. (default: false)
open-directory-after-export=false

# If set to true, the translated documents will be created before
# creating the package. (default: false)
generate-target-files=false

# If set to true, a dialog asking the user if they want to delete the
# imported OMT package will appear at the end of the importation.
# (default: false)
prompt-remove-omt-after-import=false

# If this property is set, the script located in the OmegaT scripts
# folder will be executed. The OMT package file is available in the
# binding "omtPackageFile". The console output is done in OmegaT
# logfile, not in the scripting window.
#post-package-script=processOmtPackage.groovy
```

## Graphical user interface
The plugin adds three items under the *Project* menu, which allow for three actions:

* **Import OMT package**: Unpack an OMT file and deploy the project it contains
* **Export OMT package**: Pack the current project as an OMT file
* **Export OMT and delete project**: Pack and delete the current project

### Unpacking the project

When importing an OMT package, OmegaT will uncompress the contents of the OMT package, creating a project folder and opening that project in OmegaT. 

If the plugin detects another project in the folder where it tries to unpack (e.g. because the package is being imported from inside the project folder), then it assumes it is the same project and will overwrite it. 

If a `project_save.tmx` file is found in the existing project being overwritten and the package also contains a `project_save.tmx` file, then it asks the user whether that file should be overwritten or not. If the user chooses to overwrite it, a timestamped backup is created. All other files are overwritten in either case. 

### Exporting a project

When packing the project, by default OmegaT will propose the parent folder (the one that contains the project folder) as the location where the OMT package will be saved, but the user can change this location before packing. Another convenient location might be inside the project itself. 

Every time a user packs a project, a few entries will be written to the `omt-packer.log`, thus keeping track of all the packing actions (which can be useful for the purposes of technical support), including information about the time, the user ID and the files.

### Exporting a package and deleting a project

If the user wants to keep a packed version of the project but wants to get rid of the project itself, this item combines the two actions in one.

## Command-line

The packages can also be created directly with the command line:

```
java -cp path/to/OmegaT.jar:/path/to/plugin-omt-package-1.1.0.jar net.briac.omegat.plugin.omt.ManageOMTPackage /path/to/project /path/to/omt_file`

usage: ManageOMTPackage [options] omegat-project-directory [omt-package-file]
 -c,--config <property-file>   use given file for configuration (default: /home/user/.omegat/omt-package-config.properties)
 -h,--help                     print this message
 -q,--quiet                    be extra quiet
 -v,--verbose                  be extra verbose
 ```

## Sponsor

Thanks a lot to [cApStAn](http://www.capstan.be/) for sponsoring the development of this plugin.

![cApStAn](http://www.capstan.be/wp-content/themes/capstan/img/logo-capstan.png)

## RFE

Relevant feature request:

* [RFE #1425](https://sourceforge.net/p/omegat/feature-requests/1425/)
* [RFE #114](https://sourceforge.net/p/omegat/feature-requests/114/)

## License

This project is distributed under the GNU general public license version 3 or later.

