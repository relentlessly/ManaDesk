package com.reflexit.magiccards.core.model.aggr;

import java.util.HashSet;

import com.reflexit.magiccards.core.model.AbstractMagicCard;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;

public class FieldUniqueAggregator extends AbstractIntTransAggregator {
	public FieldUniqueAggregator(MagicCardField field) {
		super(field);
	}

	@Override
	protected Object visitAbstractMagicCard(AbstractMagicCard card, Object data) {
		if (data == null)
			return 1;
		HashSet<String> uniq = (HashSet<String>) data;
		String cardId = getUniqueCardId(card);
		if (cardId != null) {
			uniq.add(cardId);
			return 0;
		}
		return 0;
	}

	protected String getUniqueCardId(AbstractMagicCard card) {
		MagicCard base = card.getBase();
		String cardId = base.getEnglishCardId();
		if (cardId == null)
			cardId = base.getCardId();
		return cardId;
	}

	@Override
	protected Object pre(Object group) {
		return new HashSet<String>();
	}

	@Override
	protected Object post(Object data) {
		if (data == null)
			return null;
		HashSet<String> uniq = (HashSet<String>) data;
		return uniq.size();
	}
}
