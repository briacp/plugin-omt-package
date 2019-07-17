# Change Log

## [v1.2.0]

- Upgraded to OmegaT 4.3.0.

## [v1.1.0]

- Added proper command-line parsing (with groovyjarjarcommonscli already included in OmegaT to avoid an external dependency)
- The path to the configuration file can be specified as a CLI option

```
usage: ManageOMTPackage [options] omegat-project-directory [omt-package-file]
 -c,--config <property-file>   use given file for configuration (default: /home/user/.omegat/omt-package-config.properties)
 -h,--help                     print this message
 -q,--quiet                    be extra quiet
 -v,--verbose                  be extra verbose
```

## [v1.0.9]

- Ignore .repositories directory by default
- Load the configuration properties in CLI mode

## [v1.0.8]

- Fixed a bug in CLI mode where the project paths were not correctly set
- Correctly load all project properties in CLI mode.

## [v1.0.7]

- Added a CLI mode to create packages without launching OmegaT.

## [v1.0.6]

- When extracting a .omt package, the "`.empty`" files are skipped.

## [v1.0.5]

- Avoid errors during `onProjectStatusChanged`, as the OMT menus could sometime not be present.

## [v1.0.4]

### Changed
- The project is always saved before exporting a package.
- Make sure the path in the zipped entries contains "/" instead of "\".  


## [v1.0.3]

### Add
- Property "post-package-script" to execute a script after the package creation.
- Property "prompt-remove-omt-after-import" (true/false) to ask if the user wants to delete the imported OMT.

### Changed
- A default config file is created in OmegaT config directory if none was present.

## [v1.0.2]

### Add
- Configuration file omt-package-config.properties
- Option `exclude-pattern` to specify regular expressions (separated by ";") of files to exclude.
- Option `open-directory-after-export` (true/false) to open the folder containing the created package.
- Option `generate-target-files` (true/false) to generate the target documents before creating the package.

## [v1.0.1]

### Changed

- Title of the Export OMT dialog
- Empty directories have ".empty" files instead of ".ignore" files
- If there are projects folder inside the project to package, they are skipped.

- the Gradle task to install the plugin is `installPlugin`, but it requires you to change the `gradle.properties` file
and modify the `omegatPluginDir`.

### Add
- More logging when zipping the package.

### Fixed
- Bug when zipping empty subdirectories, they would show up at the root of the package

## [v1.0.0]

