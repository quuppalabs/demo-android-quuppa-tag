# Android Quuppa Tag Demo

This is a Quuppa tag emulation sample app for Android. It's primary purpose is to demonstrate the usage of the [Quuppa Tag emulation library for Android](https://github.com/quuppalabs/android-quuppa-taglib). With this app, you can track your Android device in [Quuppa Intelligent Locating System](https://www.quuppa.com/).

A version, usually the most recent version of this [app is available on Google Play Store](https://play.google.com/store/apps/details?id=com.quuppa.quuppatag&pli=1). I ([kaosko](https://github.com/kaosko)) haven't bothered tagging the app source code, so see AndroidManifest.xml history for versionCode changes. However, the accompanied [Quuppa Tag library is of course properly tagged, versioned and available through Maven central repository](https://search.maven.org/artifact/com.quuppa/android-quuppa-taglib). 

## Getting Started

You can clone the project and modify as needed. This demo app uses best practices in asking for required runtime permissions and tries to explain beforehand to the user why they are needed. However, if you have an existing app, it's probably easier to integrate the library only and then include any permission changes to your app.

While there's plenty of permission and Android version checking, the app doesn't really do anything but starts and stops the QuuppaTagService, a frontend service available in the library, and communicates its status to the user. 

Since Google Play Store requires supporting recent versions of Android, we nowadays use fairly recent versions of Gradle. Some adjustments to grade-wrapper.properties may be needed if you want to use a different version of Gradle, or to gradle.properties if you like to use a different version of the JDK. Once the build succeeds, you should be able to install the resulting apk (in build/outputs/apk/debug/) to your devices with [adb](https://developer.android.com/studio/command-line/adb). Some base knowledge of Android development is expected.

## Feedback

Let us know through issue system if there's anything you'd like us to improve! 

## License

Licensed under Apache 2.0 License.
