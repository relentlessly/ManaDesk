

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.model.nav;


public class MagicDbContainter extends CardOrganizer {
	public MagicDbContainter(CardOrganizer parent) {
		super(new LocationPath("MagicDBScry"), parent);
	}

	public String getLabel() {
		return "Scryfall Database";
	}
}
