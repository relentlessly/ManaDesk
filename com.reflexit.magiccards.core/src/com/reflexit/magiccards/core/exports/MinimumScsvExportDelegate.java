package com.reflexit.magiccards.core.exports;

public class MinimumScsvExportDelegate extends MinimumCsvExportDelegate {
	@Override
	public String getSeparator() {
		return ";";
	}
}
