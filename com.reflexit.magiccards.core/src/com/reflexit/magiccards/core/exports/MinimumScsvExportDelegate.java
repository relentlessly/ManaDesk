

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.exports;

public class MinimumScsvExportDelegate extends MinimumCsvExportDelegate {
	@Override
	public String getSeparator() {
		return ";";
	}
}
