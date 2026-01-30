# Build Release APK and AAB for Google Play and FOSS flavors

if (!(Test-Path "..\gradlew.bat") -and !(Test-Path "..\gradlew")) {
    Write-Warning "Gradle Wrapper (gradlew) not found in root directory."
    Write-Warning "Please ensure you have initialized the project with Gradle Wrapper."
    Write-Warning "You can try running 'gradle wrapper' if you have global gradle installed."
    # Try using global gradle if available
    if (Get-Command gradle -ErrorAction SilentlyContinue) {
        Write-Host "Using global gradle..."
        $gradleCmd = "gradle"
    } else {
        Write-Error "Neither gradlew nor global gradle found. Build cannot proceed."
        exit 1
    }
} else {
    $gradleCmd = "..\gradlew"
}

Write-Host "Cleaning project..."
& $gradleCmd clean

Write-Host "Building Release (Google Play)..."
& $gradleCmd assembleGooglePlayRelease bundleGooglePlayRelease

Write-Host "Building Release (FOSS)..."
& $gradleCmd assembleFossRelease bundleFossRelease

Write-Host "Build complete. Outputs are in app/build/outputs/apk/ and app/build/outputs/bundle/"
