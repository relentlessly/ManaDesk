#!/bin/bash -e
JAVADIR=$HOME/java
mkdir -p $JAVADIR
cd $JAVADIR
#wget -O jre.zip https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u432-b06/OpenJDK8U-jre_x64_windows_hotspot_8u432b06.zip
#wget -O jre.tag.gz https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u432-b06/OpenJDK8U-jre_aarch64_linux_hotspot_8u432b06.tar.gz
unzip jre.zip
rm -rf win32.win32.x86_64/MagicAssistant/jre
mv *jre win32.win32.x86_64/MagicAssistant/jre/
tar xf jre.tar.gz
rm -rf linux.gtk.x86_64/MagicAssistant/jre/
mv *jre linux.gtk.x86_64/MagicAssistant/jre/
