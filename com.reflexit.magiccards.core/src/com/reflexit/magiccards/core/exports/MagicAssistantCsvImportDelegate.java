package com.reflexit.magiccards.core.exports;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import com.reflexit.magiccards.core.exports.DeckBoxExportDelegate.ExtraFields;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.MagicCardPhysical;
import com.reflexit.magiccards.core.model.abs.ICardField;

public class MagicAssistantCsvImportDelegate extends CsvImportDelegate {
	LinkedHashMap<String, ICardField> fieldMap = new LinkedHashMap<>();

	public MagicAssistantCsvImportDelegate() {
		fieldMap.put("NAME", MagicCardField.NAME);
		fieldMap.put("SET", MagicCardField.SET);
		fieldMap.put("COUNT", MagicCardField.COUNT);
		fieldMap.put("SPECIAL", MagicCardField.SPECIAL);
		fieldMap.put("COMMENT", MagicCardField.COMMENT);
		fieldMap.put("LANG", MagicCardField.LANG);
		fieldMap.put("COLLNUM", MagicCardField.COLLNUM);
		fieldMap.put("ID", MagicCardField.GATHERERID);
		fieldMap.put("OWNERSHIP", MagicCardField.OWNERSHIP);
		fieldMap.put("EDITION_ABBR", MagicCardField.EDITION_ABBR);
		fieldMap.put("MANADESK ID", MagicCardField.ID);
	}

	public static String HEADER = "NAME,SET,COUNT,SPECIAL,COMMENT,LANG,COLLNUM,ID,OWNERSHIP,EDITION_ABBR";

	/*-
	 Count,Name,Language,Card Number, GatheredId, ManaDeskId Platinum Angel,Magic 2010,3,,,English,218,,Reya Dawnbringer,Duel Decks: Divine vs. Demonic,,,English,13,,Ashes of the Abhorrent,Ixalan,2,,,English,,,true
	 */
	@Override
	protected void setHeaderFields(List<String> list) {
		ICardField fields[] = new ICardField[list.size()];
		int i = 0;
		for (Iterator iterator = list.iterator(); iterator.hasNext(); i++) {
			String name = (String) iterator.next();
			ICardField field = fieldMap.get(name);
			fields[i] = field;
		}

		setFields(fields);
	}

	@Override
	public void setFieldValue(MagicCardPhysical card, ICardField field, int i, String value) {
		if (field == MagicCardField.GATHERERID) {

			if (value.startsWith("scry_")) {
				card.set(MagicCardField.ID, value.replaceAll("scry_", ""));
				card.set(MagicCardField.GATHERERID, null);
			}
			return;
		}
		if (field instanceof ExtraFields) {
			ExtraFields efield = (ExtraFields) field;
			efield.importInto(card, value);
			return;
		}
		if (field == null)
			return;

		super.setFieldValue(card, field, i, value);
	}
}
