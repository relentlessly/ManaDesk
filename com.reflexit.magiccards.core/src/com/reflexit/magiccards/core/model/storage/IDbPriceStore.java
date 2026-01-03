package com.reflexit.magiccards.core.model.storage;

import java.util.Collection;
import java.util.HashMap;

import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.seller.IPriceProvider;
import com.reflexit.magiccards.core.seller.IPriceProviderStore;

public interface IDbPriceStore {
	IPriceProviderStore setProviderByName(String name);

	Collection<IPriceProvider> getProviders();

	IPriceProvider getProvider();

	void initialize();

	HashMap<String, String> getPriceMap(IPriceProviderStore provider);

	float getDbPrice(IMagicCard card);

	float getDbPriceFoil(IMagicCard card);

	void setDbPrice(IMagicCard card, String prices);

	boolean isInitialized();

	void reloadPrices();

}
