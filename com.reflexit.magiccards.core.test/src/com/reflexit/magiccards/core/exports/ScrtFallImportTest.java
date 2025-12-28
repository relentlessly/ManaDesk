package com.reflexit.magiccards.core.exports;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

import org.junit.Test;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.sync.ParseScryFallChecklist;
import com.reflexit.magiccards.core.sync.ParseScryFallChecklist.SortedOutputHanlder;
import com.reflexit.magiccards.core.sync.WebUtils;

public class ScrtFallImportTest extends AbstarctImportTest {
	private ScryFallImportDelegate worker = new ScryFallImportDelegate();

	private void parse() {
		parse(worker);
	}

	private void preview() {
		exception = null;
		preview(worker);
		if (exception != null)
			fail(exception.getMessage());
	}

	protected void previewUrl(String url) {
		try {
			ImportData importData = new ImportData();
			importData.setImportSource(ImportSource.URL);
			importData.setProperty(ImportSource.URL.name(), url);
			URL uurl = new URL(url);
			importData.setText(WebUtils.openUrlText(uurl));
			ImportUtils.performPreImport(worker, importData, ICoreProgressMonitor.NONE);
		} catch (Exception e) {
			fail(e.getMessage());
		}
		result = (List) worker.getResult().getList();
		exception = worker.getResult().getError();
		setout(result);
	}

	@Test
	public void testVlist() {
		previewUrl("https://scryfall.com/search?q=e%3Apzen+display:checklist+order:name");
		assertEquals(5, resSize);
	}

	@Test
	public void testVlist2() {
		previewUrl("https://scryfall.com/search?q=e:pzen+display:checklist+order:name");
		assertEquals(5, resSize);
	}

	@Test
	public void testVspoiler() {
		previewUrl("https://scryfall.com/search?q=e:pzen&as=images&order=cname");
		assertEquals(5, resSize);
	}

	private IMagicCard findById(String id, Collection<IMagicCard> list ) {
		for(IMagicCard m: list) {
			if (id.equals(m.getCardId())) return m;
		}
		return null;
	}

	@Test
	public void testFlipCards() throws IOException {
		SortedOutputHanlder handler = new SortedOutputHanlder(System.out, true, true);
		new ParseScryFallChecklist().loadSet("STX", handler, ICoreProgressMonitor.NONE);
		List<MagicCard> list = handler.getPrimary();
		assertEquals(415, list.size());
		IMagicCard rowan = findById("2cca8cf1-27f8-408d-b4bc-1d4090837f8a", (Collection) list);		
		IMagicCard will = findById("-2cca8cf1-27f8-408d-b4bc-1d4090837f8a", (Collection) list);
		assertNotNull(rowan);
		assertNotNull(will);
		assertEquals(will.getCardId(), rowan.get(MagicCardField.FLIPID));
		assertEquals(rowan.getCardId(), will.get(MagicCardField.FLIPID));
		assertEquals(0, will.get(MagicCardField.GATHERERID));
		assertEquals(513643, rowan.get(MagicCardField.GATHERERID));
	}
}
