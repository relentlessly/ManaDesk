package com.reflexit.magiccards.core.exports;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.reflexit.magiccards.core.sync.TextPrinterTest;

@RunWith(Suite.class)
@SuiteClasses({
	TablePipedImportTest.class,
	// !!! RD 	MtgoImportTest.class,
	// !!! RD 	MagicWorkstationImportTest.class,
	DeckParserTest.class,
	ImportUtilsTest.class,
// !!! RD Disable for now	ManaDeckImportTest.class,
// !!! RD Disable for now	ShandalarImportTest.class,
// !!! RD Disable for now	MTGStudioImportTest.class,
	PipedTableExportText.class,
	CsvExportDelegateTest.class,
	CsvImportDelegateTest.class,
	ClassicExportDelegateTest.class,
	ClassicImportDelegateTest.class,
	CustomExportDelegateTest.class,
	// !!! RD 	DeckBoxImportTest.class,
	TextPrinterTest.class,
		HtmlTableImportTest.class, //
		// !!! RD 		ScryGlassImportDelegateTest.class
})
public class ExportImportSuite {
}
