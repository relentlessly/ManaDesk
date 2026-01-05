#!/bin/bash

#build script, only works on linux
#needs maven 3.3.9 and java compiler 17+ installed
export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64/
PATH=$JAVA_HOME/bin:$PATH
mvn -DskipTests -f com.reflexit.magiccards.parent/pom.xml clean verify


#This adds java to bundles, java runtime (1.8 +) has to be downloaded and extracted in $JAVADIR using this convention
# linux.gtk.x86_64/MagicAssistant/jre/
# win32.win32.x86_64/MagicAssistant/jre
# run script download_jre.sh to get it
JAVADIR=$HOME/java
cd com.reflexit.magiccards.product/target/products/

#this one only add it to .zip (win archives)
for i in *.zip; do
	TIMESTAMP=$(echo $i | sed -e 's/.*\.\([0-9]\+\)-.*/\1/')
	PLATFORM=$(echo $i | sed -e 's/.*-\(.*\)\.zip/\1/')
	#echo $PLATFORM
	JRE=$JAVADIR/$PLATFORM/MagicAssistant/jre
	if [ -d "$JRE" ]; then
		NEWZIP=$(echo $(basename $i .zip)-withjava.zip)
		cp $i $NEWZIP
		NEWZIP=$PWD/$NEWZIP
		(
		cd $JAVADIR/$PLATFORM
		zip -qq -u $NEWZIP -r MagicAssistant/jre || rm $NEWZIP
		test -f $NEWZIP && echo "Updated: $NEWZIP"
	)
        else
	        echo "Info: No java for $PLATFORM"
	fi
done
#this one only add it to .tar.gz (linux archives)
for i in *.tar.gz; do
	TIMESTAMP=$(echo $i | sed -e 's/.*\.\([0-9]\+\)-.*/\1/')
	PLATFORM=$(echo $i | sed -e 's/.*-\(.*\)\.tar.gz/\1/')
	#echo $PLATFORM
	JRE=$JAVADIR/$PLATFORM/MagicAssistant/jre
	if [ -d "$JRE" ]; then
		NEWZIP=$(echo $(basename $i .tar.gz)-withjava.tar.gz)
		NEWZIP=$PWD/$NEWZIP
		NEWTAR=$PWD/$(echo $(basename $i .tar.gz)-withjava.tar)
		cp $i $NEWZIP
		(
		gunzip $NEWZIP
		cd $JAVADIR/$PLATFORM
		tar uf $NEWTAR  MagicAssistant/jre && gzip -9 $NEWTAR || rm $NEWTAR $NEWZIP
		test -f $NEWZIP && echo "Updated: $NEWZIP"
		)
        else
	        echo "Info: No java for $PLATFORM"
	fi
done
