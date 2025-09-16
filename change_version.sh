#!/bin/bash

OLD_VERSION=1.5.2
NEW_VERSION=1.6.0
find . -name "*.xml" | xargs sed -i -e "s/$OLD_VERSION-SNAPSHOT/$NEW_VERSION-SNAPSHOT/" -e "s/$OLD_VERSION.qualifier/$NEW_VERSION.qualifier/"
find . -name "*.MF" | xargs sed -i -e "s/$OLD_VERSION.qualifier/$NEW_VERSION.qualifier/"
find . -name "*.properties" | xargs sed -i -e "s/$OLD_VERSION/$NEW_VERSION/"
find . -type f -name "*.product" | xargs sed -i -e "s/$OLD_VERSION/$NEW_VERSION/"
