{ pkgs, ... }:

{
  # Android SDK matching this project: compileSdk/targetSdk 37, AGP 9.3.3, Gradle 9.7.1.
  # Google's repository names this feature-drop platform "37.0"/"37.1" rather than a bare
  # "37" - both are installed so Gradle resolves compileSdk = 37 regardless of which it expects.
  android.enable = true;
  android.android-studio.enable = false;

  # AGP resolves compileSdk = 37 to the exact SDK component "platforms;android-37.0"
  # (confirmed by trying "37.1": AGP refused to treat it as equivalent and tried to
  # auto-install 37.0 itself, which fails since the Nix store is read-only).
  android.platforms.version = [ "37.0" "36" ];
  android.buildTools.version = [ "37.0.0" "36.0.0" ];

  # An x86_64 emulator and system images, so a build can be run and checked without a device
  # (KVM-accelerated). arm64 images are left out: they are slow here and several GB each.
  android.abis = [ "x86_64" ];
  android.emulator.enable = true;
  android.systemImages.enable = true;

  # No native code.
  android.ndk.enable = false;
  android.googleAPIs.enable = false;
  android.googleTVAddOns.enable = false;

  # The project's Kotlin/Java toolchain targets language level 25 (app/build.gradle.kts).
  languages.java.enable = true;
  languages.java.jdk.package = pkgs.jdk25;

  packages = [ pkgs.git ];
}
