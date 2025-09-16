package com.reflexit.magiccards.core.model.xml;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.ICardHandler;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.Location;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.abs.ICardField;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.model.storage.IDbCardStore;
import com.reflexit.magiccards.core.model.storage.IDbPriceStore;
import com.reflexit.magiccards.core.model.storage.IFilteredCardStore;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.monitor.SubCoreProgressMonitor;
import com.reflexit.magiccards.core.sync.ParseScryFallChecklist;
import com.reflexit.magiccards.core.sync.ParseScryFallSets;
import com.reflexit.magiccards.core.sync.TextPrinter;
import com.reflexit.magiccards.core.sync.UpdateCardsFromWeb;

public class XmlCardHolder implements ICardHandler {
	private String activeDeck;

	@Override
	public IFilteredCardStore getMagicDBFilteredStore() {
		return MagicDBFilteredCardFileStore.getInstance();
	}

	@Override
	public IDbCardStore getMagicDBStore() {
		return DbMultiFileCardStore.getInstance();
	}

	@Override
	public IFilteredCardStore getMagicDBFilteredStoreWorkingCopy() {
		return new BasicMagicDBFilteredCardFileStore((DbMultiFileCardStore) getMagicDBStore());
	}

	@Override
	public IFilteredCardStore getLibraryFilteredStore() {
		return LibraryFilteredCardFileStore.getInstance();
	}

	@Override
	public ICardStore getCardStore(Location to) {
		return LibraryFilteredCardFileStore.getInstance().getStore(to);
	}

	@Override
	public IFilteredCardStore getLibraryFilteredStoreWorkingCopy() {
		return new BasicLibraryFilteredCardFileStore((CollectionMultiFileCardStore) getLibraryCardStore());
	}

	@Override
	public ICardStore getLibraryCardStore() {
		return LibraryCardStore.getInstance();
	}

	@Override
	public IFilteredCardStore getCardCollectionFilteredStore(String filename) {
		return new DeckFilteredCardFileStore(filename);
	}

	@Override
	public ICardStore loadFromXml(String filename) {
		File file = new File(filename);
		CollectionSingleFileCardStore store = new CollectionSingleFileCardStore(file, Location.createLocation(file),
				true);
		return store;
	}

	@Override
	public void loadFromFlatResource(String set) throws IOException {
		InputStream is = FileUtils.loadDbResource(set);
		if (is != null) {
			BufferedReader st = new BufferedReader(new InputStreamReader(is, FileUtils.CHARSET_UTF_8));
			ArrayList<IMagicCard> list = new ArrayList<>();
			loadtFromFlatIntoDB(st, list);
			is.close();
		}
	}

	public static File getDbFolder() {
		File dir = DataManager.getInstance().getModelRoot().getMagicDBContainer().getFile();
		return dir;
	}

	private synchronized int loadtFromFlatIntoDB(BufferedReader st, ArrayList<IMagicCard> list)
			throws MagicException, IOException {
		ICardStore store = getMagicDBStore();
		int init = store.size();
		loadFromFlat(st, list);
		boolean hasAny = list.size() > 0;
		store.addAll(list);
		// ArrayList<IMagicCard> more = fixCards(list);
		// if (more.size() > 0)
		// store.addAll(more);
		int rec = store.size() - init;
		return rec > 0 ? rec : (hasAny ? 0 : -1);
	}

	private ArrayList<IMagicCard> loadFromFlat(BufferedReader st, ArrayList<IMagicCard> list) throws IOException {
		String line = st.readLine(); // header ignore for now
		if (line == null)
			throw new IOException("Empty set file");
		ICardField[] xfields = MagicCardField.toFields(line, "\\Q" + TextPrinter.SEPARATOR);
		String[] fields = new String[xfields.length];
		while ((line = st.readLine()) != null) {
			if (line.length() == 0)
				continue;
			try {
				linesplit(line, TextPrinter.SEPARATOR_CHAR, fields);
				MagicCard card = new MagicCard();
				int i = 0;
				for (ICardField field : xfields) {
					if (i < fields.length) {
						card.set(field, fields[i]);
					}
					i++;
				}
				// if (markCn && (card.getCollNumber() == null || card.getCollNumber().length()
				// ==
				// 0)) {
				// card.setCollNumber(cnum);
				// }
				String id = card.getCardId();
				if (id == null) {
					System.err.print("Skipped invalid: " + TextPrinter.getString(card));
					continue;
				}
				list.add(card);
			} catch (Exception e) {
				MagicLogger.log(e);
			}
		}
		return list;
	}

	/**
	 * Optimized split function
	 *
	 * @param line
	 * @param sep
	 * @return
	 */
	private String[] linesplit(String line, char sep, String res[]) {
		char[] charArray = line.toCharArray();
		int k = 0;
		int a = 0;
		int i = 0;
		for (char c : charArray) {
			if (c == sep) {
				res[k++] = line.substring(a, i).trim().intern();
				a = i + 1;
			}
			i++;
			if (k >= res.length)
				return res;
		}
		res[k++] = line.substring(a, i).trim().intern();
		return res;
	}

	@Override
	public int downloadUpdates(final String set, final Properties options, ICoreProgressMonitor pm)
			throws MagicException, InterruptedException {
		final int rec[] = new int[1];
		DataManager.getInstance().getMagicDBStore().updateOperation(pm1 -> {
			try {
				String lang = (String) options.get(UpdateCardsFromWeb.UPDATE_LANGUAGE);
				if (lang != null && lang.length() == 0) {
					lang = null;
				}
				pm1.beginTask("Downloading", 110 + (lang == null ? 0 : 100));
				pm1.subTask("Initializing");
				if (pm1.isCanceled())
					throw new InterruptedException();
				pm1.subTask("Updating set list...");
				try {
					// First, refresh set list
					new ParseScryFallSets().loadSets(false);
				} catch (Exception e) {
					MagicLogger.log(e); // move on if exception via set loading
				}
				ArrayList<IMagicCard> list = new ArrayList<IMagicCard>();
				pm1.subTask("Downloading cards...");
				rec[0] = downloadAndStore(set, options, list, pm1);
				pm1.subTask("Updating editions...");
				Editions.getInstance().save();
				pm1.worked(10);
				if (lang != null && lang.length() > 0) {
					pm1.subTask("Updating languages...");
					Set<ICardField> fieldMaps = new HashSet<ICardField>();
					fieldMaps.add(MagicCardField.LANG);
					new UpdateCardsFromWeb().updateStore(list.iterator(), list.size(), fieldMaps, lang,
							getMagicDBStore(), new SubCoreProgressMonitor(pm1, 100));
				}
			} catch (IOException e) {
				throw new MagicException(e);
			}
		}, pm);
		return rec[0];
	}

	// Scryfall download version
	public int downloadAndStore(String set, Properties options, ArrayList<IMagicCard> list, ICoreProgressMonitor pm)
			throws FileNotFoundException, MalformedURLException, IOException, InterruptedException {
		int rec = 0;
		if (set.equalsIgnoreCase("All")) {

			// Refresh sets list
			ParseScryFallSets allSets = new ParseScryFallSets();
			allSets.loadSets(false);

			Collection<Edition> editions = allSets.getAll();

			pm.beginTask("Downloading all cards", editions.size() * 1000);
			try {
				int i = 1;
				int n = editions.size();

				for (Iterator iterator = editions.iterator(); iterator.hasNext(); i++) {
					Edition edition = (Edition) iterator.next();
					try {
						pm.setTaskName("Downloading " + edition.getName() + " (" + i + " of " + n + ")");

						rec += downloadAndStoreSet(edition, options, list, new SubCoreProgressMonitor(pm, 1000));
					} catch (InterruptedException e) {
						throw e;
					} catch (Exception e) {
						MagicLogger.log(e);
					}
					if (pm.isCanceled())
						throw new InterruptedException();
				}
			} finally {
				pm.done();
			}
			return rec;
		} else if (set.equalsIgnoreCase("Recents")) {

			// Refresh recent sets (last 2 years)
			ParseScryFallSets allSets = new ParseScryFallSets();
			allSets.loadSets(false);

			Collection<Edition> editions = allSets.getAll();

			try {
				int i = 1;
				int n = 0;

				// Count "recent" sets to load
				for (Iterator iterator = editions.iterator(); iterator.hasNext();) {
					Edition edition = (Edition) iterator.next();

					LocalDate threshold = LocalDate.now().minusYears(2);

					// Convert to LocalDate
					LocalDate setDate = edition.getReleaseDate().toInstant().atZone(ZoneId.systemDefault())
							.toLocalDate();

					if (setDate.compareTo(threshold) > 0) {
						n++;
					}
				}

				pm.beginTask("Downloading all cards for last 2 years sets", n * 1000);

				for (Iterator iterator = editions.iterator(); iterator.hasNext(); i++) {
					Edition edition = (Edition) iterator.next();

					LocalDate threshold = LocalDate.now().minusYears(2);

					// Convert to LocalDate
					LocalDate setDate = edition.getReleaseDate().toInstant().atZone(ZoneId.systemDefault())
							.toLocalDate();

					if (setDate.compareTo(threshold) > 0) {
						try {
							pm.setTaskName("Downloading " + edition.getName() + " (" + i + " of " + n + ")");

							rec += downloadAndStoreSet(edition, options, list, new SubCoreProgressMonitor(pm, 1000));
						} catch (InterruptedException e) {
							throw e;
						} catch (Exception e) {
							MagicLogger.log(e);
						}
					}
					if (pm.isCanceled())
						throw new InterruptedException();
				}
			} finally {
				pm.done();
			}
			return rec;
		} else {

			Editions editions = Editions.getInstance();

			Edition ed = editions.getEditionByName(set);

			rec += downloadAndStoreSet(ed, options, list, new SubCoreProgressMonitor(pm, 1000));
			return rec;
		}
	}

	// Scryfall version
	public int downloadAndStoreSet(Edition edition, Properties options, ArrayList<IMagicCard> list,
			ICoreProgressMonitor pm)
			throws FileNotFoundException, MalformedURLException, IOException, InterruptedException {
		pm.beginTask("Downloading set", 100);
		try {
			int rec;

			// Always use the same temporary file
			File file = new File(FileUtils.getStateLocationFile(), "downloaded.txt");

			new ParseScryFallChecklist().downloadAndSaveEdition(file, edition.getAbbreviations()[0]);

			if (pm.isCanceled())
				throw new InterruptedException();
			pm.subTask("Updating database for " + edition.getName());
			BufferedReader st = new BufferedReader(new FileReader(file));

			// Add/refresh the set just before loading the cards
			Editions ed = Editions.getInstance();
			ed.addEdition(edition);

			rec = loadtFromFlatIntoDB(st, list);
			st.close();

			pm.worked(30);
			return rec;
		} finally {
			pm.done();
		}
	}

	@Override
	public ICardStore getActiveStore() {
		LibraryFilteredCardFileStore lib = (LibraryFilteredCardFileStore) DataManager.getCardHandler()
				.getLibraryFilteredStore();
		Location location = Location.createLocation(activeDeck);
		ICardStore<IMagicCard> store = lib.getStore(location);
		return store;
	}

	@Override
	public String getActiveDeckId() {
		return this.activeDeck;
	}

	@Override
	public void setActiveDeckId(String key) {
		this.activeDeck = key;
	}

	@Override
	public IDbPriceStore getDBPriceStore() {
		return DbPricesMultiFileStore.getInstance();
	}

	public static void main(String[] args) {
		String lines[] = new String[] {
				"386463|Abomination of Gudul|{3}{B}{G}{U}|Creature - Horror|3|4|Flying<br>Whenever Abomination of Gudul deals combat damage to a player, you may draw a card. If you do, discard a card.<br>Morph {2}{B}{G}{U} <i>(You may cast this card face down as a 2/2 creature for {3}. Turn it face up any time for its morph cost.)</i>|Khans of Tarkir|Common|0.0||0.0|Erica Yang|159||Flying<br>Whenever Abomination of Gudul deals combat damage to a player, you may draw a card. If you do, discard a card.<br>Morph {2}{B}{G}{U} <i>(You may cast this card face down as a 2/2 creature for {3}. Turn it face up any time for its morph cost.)</i>|0|\n",
				"386464|Abzan Ascendancy|{W}{B}{G}|Enchantment|||When Abzan Ascendancy enters the battlefield, put a +1/+1 counter on each creature you control.<br>Whenever a nontoken creature you control dies, put a 1/1 white Spirit creature token with flying onto the battlefield.|Khans of Tarkir|Rare|0.0||0.0|Mark Winters|160||When Abzan Ascendancy enters the battlefield, put a +1/+1 counter on each creature you control.<br>Whenever a nontoken creature you control dies, put a 1/1 white Spirit creature token with flying onto the battlefield.|0|\n" };
		XmlCardHolder holder = new XmlCardHolder();
		String buf[] = new String[20];
		for (int i = 0; i < 50000; i++) {
			for (String line : lines) {
				holder.linesplit(line, TextPrinter.SEPARATOR_CHAR, buf);
			}
		}
	}
}
