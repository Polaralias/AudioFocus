#!/bin/bash
set -e

SDK_DIR="$(pwd)/.android_sdk"
OS="$(uname -s)"

# Determine download URL based on OS
case "$OS" in
    Linux*)
        CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
        ;;
    Darwin*)
        CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-mac-13114758_latest.zip"
        ;;
    *)
        echo "Unsupported OS: $OS. This script supports Linux and macOS."
        exit 1
        ;;
esac

if [ -d "$SDK_DIR" ]; then
    echo "SDK directory exists at $SDK_DIR. Skipping download."
else
    echo "Creating SDK directory..."
    mkdir -p "$SDK_DIR"

    echo "Downloading Command Line Tools for $OS..."
    curl -o "$SDK_DIR/cmdline-tools.zip" "$CMDLINE_TOOLS_URL"

    echo "Unzipping..."
    unzip -q "$SDK_DIR/cmdline-tools.zip" -d "$SDK_DIR"
    rm "$SDK_DIR/cmdline-tools.zip"

    # Restructure for sdkmanager requirements: cmdline-tools/latest/...
    echo "Restructuring for sdkmanager..."
    if [ -d "$SDK_DIR/cmdline-tools" ]; then
        mv "$SDK_DIR/cmdline-tools" "$SDK_DIR/temp_cmdline_tools"
        mkdir -p "$SDK_DIR/cmdline-tools/latest"
        mv "$SDK_DIR/temp_cmdline_tools"/* "$SDK_DIR/cmdline-tools/latest/"
        rmdir "$SDK_DIR/temp_cmdline_tools"
    else
        echo "Error: cmdline-tools directory not found after unzip."
        exit 1
    fi
fi

export ANDROID_HOME="$SDK_DIR"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "Accepting licenses..."
yes | sdkmanager --licenses

echo "Installing packages..."
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

echo "Creating local.properties..."
echo "sdk.dir=$ANDROID_HOME" > local.properties

echo "Setup complete."
