# Change Log

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

