package com.reflexit.magiccards.core.seller;

import java.util.HashMap;
import java.util.Properties;

public interface IPriceProviderStore {
	public abstract String getName();

	public abstract HashMap<String, Float> getPriceMap();

	public abstract Properties getProperties();
}