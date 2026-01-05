

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.seller;

import java.util.HashMap;
import java.util.Properties;

public interface IPriceProviderStore {
	public abstract String getName();

	public abstract HashMap<String, String> getPriceMap();

	public abstract Properties getProperties();

}