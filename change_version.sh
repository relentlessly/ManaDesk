# Contributors:
#     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
#!/bin/bash

OLD_VERSION=0.9.2
NEW_VERSION=0.9.3
find . -name "*.xml" -not \( -path "./updates/*" -prune \) | xargs sed -i -e "s/$OLD_VERSION-SNAPSHOT/$NEW_VERSION-SNAPSHOT/" -e "s/$OLD_VERSION.qualifier/$NEW_VERSION.qualifier/"
find . -name "*.MF" | xargs sed -i -e "s/$OLD_VERSION.qualifier/$NEW_VERSION.qualifier/"
find . -name "*.properties" | xargs sed -i -e "s/$OLD_VERSION/$NEW_VERSION/"
find . -type f -name "*.product" | xargs sed -i -e "s/$OLD_VERSION/$NEW_VERSION/"
