/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */
package com.reflexit.magiccards.core.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;

public class Rarity implements ISearchableProperty {
	public static final String COMMON = "Common";
	public static final String UNCOMMON = "Uncommon";
	public static final String RARE = "Rare";
	public static final String MYTHIC_RARE = "Mythic"; // RD Don't use rarity with spaces, this causes a few side
														// effects
	public static final String SPECIAL = "Special";
	public static final String OTHER = "Other";

	private Rarity() {
		this.names = new LinkedHashMap();
		add(MYTHIC_RARE);
		add(RARE);
		add(UNCOMMON);
		add(COMMON);
		add(SPECIAL);
		add(OTHER);
	}

	static Rarity instance = new Rarity();
	private LinkedHashMap names;

	private void add(String string) {
		String id = getPrefConstant(string);
		this.names.put(id, string);
	}

	@Override
	public String getIdPrefix() {
		return getFilterField().toString();
	}

	@Override
	public FilterField getFilterField() {
		return FilterField.RARITY;
	}

	public static Rarity getInstance() {
		return instance;
	}

	@Override
	public Collection getIds() {
		return new ArrayList(this.names.keySet());
	}

	public String getPrefConstant(String name) {
		return FilterField.getPrefConstant(getIdPrefix(), name);
	}

	@Override
	public String getNameById(String id) {
		return (String) this.names.get(id);
	}

	/**
	 * @param a1
	 * @param a2
	 * @return
	 */
	public static int compare(String r1, String r2) {
		Collection values = getInstance().names.values();
		int i1 = values.size() - 1, i2 = i1, i = 0;
		for (Iterator iterator = values.iterator(); iterator.hasNext(); i++) {
			String v = (String) iterator.next();
			if (r1.equalsIgnoreCase(v))
				i1 = i;
			if (r2.equalsIgnoreCase(v))
				i2 = i;
		}
		return i2 - i1;
	}

	public static String getMoreRare(String r) {
		Collection values = getInstance().names.values();
		String prev = null;
		for (Iterator iterator = values.iterator(); iterator.hasNext();) {
			String v = (String) iterator.next();
			if (r.equals(v)) {
				return prev;
			}
			prev = v;
		}
		return null;
	}

	public static String resolve(String rarity) {
		if (rarity == null)
			return null;
		switch (rarity) {
		case "common":
		case "Common":
			return Rarity.COMMON;
		case "uncommon":
		case "Uncommon":
			return Rarity.UNCOMMON;
		case "rare":
		case "Rare":
			return Rarity.RARE;
		case "mythic rare":
		case "mythic":
		case Rarity.MYTHIC_RARE:
			return Rarity.MYTHIC_RARE;
		case "special":
		case "Special":
			return Rarity.SPECIAL;
		default:
			return Rarity.OTHER;
		}
	}
}
