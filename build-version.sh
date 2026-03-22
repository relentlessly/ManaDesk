# Contributors:
#     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
# 	  Jason Grammenos (2026) - merged in change_version.sh
#!/bin/bash
show_help(){
	echo "This script builds ManaDesk, and optional a new release of ManaDesk"
	echo "standard usage: bash build.sh -v (new_version_number) -r"
	echo "with new_version_number being a semantic version number" 
	echo "------"
	echo "-v set new version number"
	echo "-r create a new release"
}
change_version (){
	local OLD_VERSION=$1
	local NEW_VERSION=$2
	echo "Updating version from ${OLD_VERSION} to ${NEW_VERSION}."
	find . -name "*.xml" -not \( -path "./updates/*" -prune \) | xargs sed -i -e "s/$OLD_VERSION-SNAPSHOT/$NEW_VERSION-SNAPSHOT/" -e "s/$OLD_VERSION.qualifier/$NEW_VERSION.qualifier/"
	find . -name "*.MF" | xargs sed -i -e "s/$OLD_VERSION.qualifier/$NEW_VERSION.qualifier/"
	find . -name "*.properties" | xargs sed -i -e "s/$OLD_VERSION/$NEW_VERSION/"
	find . -type f -name "*.product" | xargs sed -i -e "s/$OLD_VERSION/$NEW_VERSION/"
}

get_current_version (){
	local filename="./version.ini"
	if [ ! -f "${filename}" ]; then
		echo "File does not exists"
	fi
	if [ -f "${filename}" ]; then
		echo "File exists"
	fi
	local version_number=0
	while IFS= read -r line
	do
	echo "the line is ${line}"
	version_number=$(echo "$line" | cut -d "=" -f 2)
	done < "$filename"
	echo "$version_number"
}

write_current_version(){
	local new_current_version=$1
	printf "current_version=${new_current_version}" > "./version.ini"
}

version=0.9.0
release=false
while getopts "h?v:r" opt; do
  case "$opt" in
    h|\?)
      show_help
      exit 0
      ;;
    v)  version=$OPTARG
      ;;
    r)  release=true
      ;;
  esac
done
shift $((OPTIND-1))

#build script, only works on linux
#needs maven 3.3.9 and java compiler 17+ installed
export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64/
PATH=$JAVA_HOME/bin:$PATH
if [ "$release" = true ] ; then
	# update the version
	current_version="$(get_current_version)"
 	echo "the current version is ${current_version}"
	echo "the desired version is ${version}"
	change_version "${current_version}" "${version}"
fi

