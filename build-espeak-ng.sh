#!/bin/bash
# Build espeak-ng shared library for Android arm64-v8a
# Prerequisites: Android NDK installed at $ANDROID_NDK or $HOME/Library/Android/sdk/ndk/<version>
#
# Usage: ./build-espeak-ng.sh [NDK_PATH]

set -e

NDK_PATH="${1:-${ANDROID_NDK:-$HOME/Library/Android/sdk/ndk/$(ls $HOME/Library/Android/sdk/ndk/ 2>/dev/null | sort -V | tail -1)}}"

if [ ! -d "$NDK_PATH" ]; then
    echo "ERROR: Android NDK not found. Install it via Android Studio SDK Manager or provide path:"
    echo "  ./build-espeak-ng.sh /path/to/ndk"
    exit 1
fi

echo "Using NDK: $NDK_PATH"

TOOLCHAIN="$NDK_PATH/toolchains/llvm/prebuilt/darwin-x86_64"
if [ ! -d "$TOOLCHAIN" ]; then
    TOOLCHAIN="$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64"
fi
TARGET=aarch64-linux-android
API=26
CC="$TOOLCHAIN/bin/${TARGET}${API}-clang"
AR="$TOOLCHAIN/bin/llvm-ar"
RANLIB="$TOOLCHAIN/bin/llvm-ranlib"

BUILD_DIR="/tmp/espeak-ng-android-build"
OUTPUT_DIR="$(cd "$(dirname "$0")" && pwd)/app/src/main/jniLibs/arm64-v8a"

# Clone espeak-ng
rm -rf "$BUILD_DIR"
git clone --depth 1 https://github.com/espeak-ng/espeak-ng.git "$BUILD_DIR"
cd "$BUILD_DIR"

# Build
./autogen.sh
./configure \
    --host=$TARGET \
    --prefix="$BUILD_DIR/out" \
    --without-pcaudiolib \
    --without-speechplayer \
    --without-sonic \
    CC="$CC" AR="$AR" RANLIB="$RANLIB" \
    CFLAGS="-fPIC"

make -j$(sysctl -n hw.ncpu 2>/dev/null || nproc)

# Copy output
mkdir -p "$OUTPUT_DIR"
cp "$BUILD_DIR/src/.libs/libespeak-ng.so" "$OUTPUT_DIR/"

echo ""
echo "SUCCESS: libespeak-ng.so built at $OUTPUT_DIR/libespeak-ng.so"
echo "You can now build the Android app in Android Studio."

# Cleanup
rm -rf "$BUILD_DIR"
