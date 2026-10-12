# MALTWEB Android Starter

Beginner-friendly Android app starter built with Kotlin and Jetpack Compose.

## In this first version
- Discover screen with sample listings
- Search listings
- Filter by category
- Open listing details
- Save/favourite listings during the current app session
- Profile preview showing planned account types
- GitHub Actions workflow to build a debug APK

## Important
This version uses sample data only. It is not yet connected to Supabase. Sign-in, online listings, reviews, admin approval, and business tools will be added step by step. Do not put a Supabase service-role key inside an Android app. We will use the public anon/publishable key with correct Row Level Security policies.

## Build using GitHub on your phone
1. Create a new repository named `MaltwebAndroid`.
2. Upload the contents of this ZIP into the repository's `main` branch (upload the extracted files and folders, not the ZIP itself).
3. Open **Actions** and wait for **Build MALTWEB Android APK** to complete.
4. Open the successful run and download the `maltweb-debug-apk` artifact.
5. Extract that artifact ZIP and install `app-debug.apk` on your Android phone.

## Technical details
Kotlin, Jetpack Compose, Android Gradle Plugin 8.7.3, Gradle 8.9, Java 17, min SDK 26.


## App icon
The MALTWEB launcher icon is included under `app/src/main/res/mipmap-*` and referenced by the Android manifest. Rebuild the APK and install/update it to see the icon on your phone.
