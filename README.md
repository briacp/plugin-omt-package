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
```

## Sponsor

Thanks a lot to [cApStAn](http://www.capstan.be/) for sponsoring the development of this plugin.

## RFE

Relevant feature request:

* [RFE #1425](https://sourceforge.net/p/omegat/feature-requests/1425/)
* [RFE #114](https://sourceforge.net/p/omegat/feature-requests/114/)

## License

This project is distributed under the GNU general public license version 3 or later.

