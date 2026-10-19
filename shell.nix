{ pkgs ? import <nixpkgs> {
    config = { android_sdk.accept_license = true; allowUnfree = true; };
  } }:
let
  android = pkgs.androidenv.composeAndroidPackages {
    platformVersions = [ "35" ];
    buildToolsVersions = [ "35.0.0" ];
    includeEmulator = false;
    includeSystemImages = false;
    includeNDK = false;
  };
  sdk = "${android.androidsdk}/libexec/android-sdk";
in
pkgs.mkShell {
  packages = [ android.androidsdk pkgs.gradle_8 pkgs.jdk17 pkgs.android-tools ];
  ANDROID_HOME = sdk;
  ANDROID_SDK_ROOT = sdk;
  JAVA_HOME = pkgs.jdk17.home;
  # nixpkgs ships aapt2 for this platform; Gradle's own download is not runnable on NixOS.
  GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/35.0.0/aapt2";
}
