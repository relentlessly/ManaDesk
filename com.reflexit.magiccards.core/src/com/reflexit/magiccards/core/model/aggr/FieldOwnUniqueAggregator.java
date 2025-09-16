package com.reflexit.magiccards.core.model.aggr;

import com.reflexit.magiccards.core.model.AbstractMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;

public class FieldOwnUniqueAggregator extends FieldUniqueAggregator {
	public FieldOwnUniqueAggregator(MagicCardField field) {
		super(field);
	}

	@Override
	protected String getUniqueCardId(AbstractMagicCard card) {
		if (!card.isOwn())
			return null;
		return super.getUniqueCardId(card);
	}
}
