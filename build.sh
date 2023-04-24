#!/usr/bin/env sh

if [ "$#" -eq "0" ]; then
    echo "Usage: build.sh [android / ios / all]";
    exit 0
fi

PATH_BASE=`pwd`;


if [[ "$1" =~ ^("-i"|"iOS"|"ios"|"all")$ ]]; then
    cd ${PATH_BASE}
    ./init-ios.sh
    # apply prebuilt openssl libs to ios, so no need to compile openssl here
    cd ios && ./compile-ffmpeg.sh all
fi
