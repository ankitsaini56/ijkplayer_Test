#!/usr/bin/env sh

if [ -z "$ANDROID_NDK" ]; then
    echo "You must define ANDROID_NDK before starting."
    exit 1
fi

if [ "$#" -eq "0" ]; then
    echo "Usage: build.sh [android / ios / all]";
    exit 0
fi

PATH_BASE=`pwd`;

if [[ "$1" =~ ^("-a"|"Android"|"android"|"all")$ ]]; then
    cd ${PATH_BASE}
    ./init-android.sh
    ./init-android-openssl.sh
    cd android/contrib && ./compile-openssl.sh all && ./compile-ffmpeg.sh all
    cd .. && ./compile-ijk.sh all
fi

if [[ "$1" =~ ^("-i"|"iOS"|"ios"|"all")$ ]]; then
    cd ${PATH_BASE}
    ./init-ios.sh
    # apply prebuilt openssl libs to ios, so no need to compile openssl here
    cd ios && ./compile-ffmpeg.sh all
fi
