# Change Log

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

