

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.model.nav;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import com.reflexit.magiccards.core.model.Location;


public class CollectionsContainer extends CardOrganizer {
	public CollectionsContainer(String name, CardOrganizer parent) {
		super(name, parent);
	}

	public CollectionsContainer(LocationPath path, CardOrganizer parent) {
		super(path, parent);
	}

	@SuppressWarnings("unused")
	public void loadChildren() {
		File dir = getFile();
		File[] listFiles = dir.listFiles();
		if (listFiles == null)
			return;
		for (File mem : listFiles) {
			if (!mem.exists())
				continue;
			String name = mem.getName();
			// System.err.println(this + "/" + name);
			if (name.equals("MagicDBScry"))
				continue; // skip this one, this is the main Db
			if (name.equals("MagicDB"))
				continue; // skip this one, this is the old Db
			if (name.startsWith("."))
				continue; // skip this one too
			CardElement el = findChieldByName(name);
			if (mem.isDirectory()) {
				if (el == null) {
					CollectionsContainer con = new CollectionsContainer(name, this);
					con.loadChildren();
				} else {
					if (el instanceof CollectionsContainer) {
						((CollectionsContainer) el).loadChildren();
					}
				}
			} else {
				if (name.endsWith(".xml")) {
					if (el == null) {
						boolean deck = checkType(mem);
						boolean unsorted = checkSort(mem);
						CardCollection cardCollection = new CardCollection(name, this, deck, null, unsorted);
					}
				}
			}
		}
	}

	private boolean checkType(File mem) {
		try {
			byte[] headerBytes = new byte[1000];
			InputStream openStream = new FileInputStream(mem);
			try {
				int k = openStream.read(headerBytes);
				if (k == -1)
					return false;
				String header = new String(headerBytes, 0, k);
				if (header.contains("<type>deck</type"))
					return true;
			} finally {
				openStream.close();
			}
		} catch (Exception e) {
			// skip
		}
		return false;
	}

	private boolean checkSort(File mem) {
		try {
			byte[] headerBytes = new byte[1000];
			InputStream openStream = new FileInputStream(mem);
			try {
				int k = openStream.read(headerBytes);
				if (k == -1)
					return false;
				String header = new String(headerBytes, 0, k);
				if (header.contains("<property name=\"unsorted\" value=\"true\"/>")) {
					return true;
				} else {
					return false;
				}

			} finally {
				openStream.close();
			}
		} catch (Exception e) {
			// skip
		}
		return false;
	}

	public CollectionsContainer addCollectionsContainer(String name) {
		return (CollectionsContainer) newElement(name, this);
	}

	public CardCollection addDeck(String filename, boolean virtual) {
		CardCollection d = new CardCollection(filename, this, true, virtual, false);
		return d;
	}

	public void removeDeck(CardCollection el) {
		el.remove();
	}

	@Override
	public CardElement newElement(String name, CardOrganizer parent) {
		return new CollectionsContainer(name, parent);
	}

	public CardElement findChield(Location loc) {
		return findChieldByName(loc.getBaseFileName());
	}
}
