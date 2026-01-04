package com.reflexit.magiccards.core.seller;

import java.io.IOException;
import java.net.URL;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.core.exports.ClassicNoXExportDelegate;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.storage.IDbPriceStore;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.monitor.SubCoreProgressMonitor;
import com.reflexit.magiccards.core.sync.CurrencyConvertor;
import com.reflexit.magiccards.core.xml.PricesXmlStreamWriter;

public class AbstractPriceProvider implements IPriceProvider {
	protected String name;
	protected final HashMap<String, String> priceMap;
	protected final Properties properties;

	public AbstractPriceProvider(String name) {
		this.name = name;
		this.properties = new Properties();
		this.priceMap = new HashMap<>();
	}

	@Override
	public Currency getCurrency() {
		String cur = getProperties().getProperty("currency");
		if (cur == null)
			return CurrencyConvertor.USD;
		return Currency.getInstance(cur);
	}

	@Override
	public void updatePricesAndSync(Iterable<IMagicCard> iterable, ICoreProgressMonitor monitor) throws IOException {
		monitor.beginTask("Loading prices from " + getURL() + " ...", 200);
		try {
			Iterable<IMagicCard> res = updatePrices(iterable, new SubCoreProgressMonitor(monitor, 100));
			if (res != null) {
				save();
				sync(res, new SubCoreProgressMonitor(monitor, 100));
			}
		} finally {
			monitor.done();
		}
	}

	@Override
	public void Sync(Iterable<IMagicCard> iterable, ICoreProgressMonitor monitor) throws IOException {
		monitor.beginTask("Sync prices", 200);
		try {
			sync(iterable, new SubCoreProgressMonitor(monitor, 100));
		} finally {
			monitor.done();
		}
	}

	public int getSize(Iterable<IMagicCard> iterable) {
		int size = 0;
		for (IMagicCard magicCard : iterable) {
			size++;
		}
		return size;
	}

	public Set<String> getSets(Iterable<IMagicCard> iterable) {
		HashSet<String> sets = new HashSet();
		for (IMagicCard magicCard : iterable) {
			String set = magicCard.getSet();
			sets.add(set);
		}
		return sets;
	}

	public void sync(Iterable<IMagicCard> res, ICoreProgressMonitor monitor) {
		IDbPriceStore dbPriceStore = DataManager.getDBPriceStore();
		if (dbPriceStore.getProvider().equals(this))
			dbPriceStore.reloadPrices();
	}

	public Iterable<IMagicCard> updatePrices(Iterable<IMagicCard> iterable, ICoreProgressMonitor monitor)
			throws IOException {
		throw new MagicException("This price provider " + name + " does not support interactive update");
	}

	@Override
	public URL getURL() {
		return null;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public URL buy(Iterable<IMagicCard> cards) {
		return null;
	}

	@Override
	public String export(Iterable<IMagicCard> cards) {
		String res = new ClassicNoXExportDelegate().export(cards);
		return res;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public synchronized void setDbPrice(String id, float price, Currency cur) {

		float curr = CurrencyConvertor.convertFromInto(price, cur, getCurrency());
		float currF = getDbPriceFoil(id, getCurrency());
		if (currF != 0) {
			currF = CurrencyConvertor.convertFromInto(currF, cur, getCurrency());
		}
		if (curr == 0 && currF == 0) {
			priceMap.remove(id);
		} else {
			String prices = String.valueOf(curr) + ":" + String.valueOf(currF);

			priceMap.put(id, prices);
		}
	}

	@Override
	public synchronized void setDbPriceFoil(String id, float price, Currency cur) {

		float curr = getDbPrice(id, getCurrency());
		float currF = CurrencyConvertor.convertFromInto(price, cur, getCurrency());
		if (curr != 0) {
			curr = CurrencyConvertor.convertFromInto(curr, cur, getCurrency());
		}
		if (curr == 0 && currF == 0) {
			priceMap.remove(id);
		} else {
			String prices = String.valueOf(curr) + ":" + String.valueOf(currF);

			priceMap.put(id, prices);
		}
	}

	@Override
	public synchronized void setDbPrice(IMagicCard magicCard, float price, Currency cur) {
		String id = magicCard.getCardId();

		setDbPrice(id, price, cur);
	}

	@Override
	public synchronized void setDbPriceFoil(IMagicCard magicCard, float price, Currency cur) {
		String id = magicCard.getCardId();

		setDbPriceFoil(id, price, cur);
	}

	@Override
	public synchronized float getDbPrice(IMagicCard card, Currency cur) {
		String id = card.getCardId();
		if (priceMap.containsKey(id)) {
			String prices = priceMap.get(id);
			int sep = prices.indexOf(":");
			if (sep != -1) {
				prices = prices.substring(0, sep);
			}
			float price = Float.valueOf(prices);
			return CurrencyConvertor.convertFromInto(price, getCurrency(), cur);
		}
		return 0f;
	}

	@Override
	public synchronized float getDbPrice(String id, Currency cur) {
		if (priceMap.containsKey(id)) {
			String prices = priceMap.get(id);
			int sep = prices.indexOf(":");
			if (sep != -1) {
				prices = prices.substring(0, sep);
			}
			float price = Float.valueOf(prices);
			return CurrencyConvertor.convertFromInto(price, getCurrency(), cur);
		}
		return 0f;
	}

	@Override
	public synchronized float getDbPriceFoil(IMagicCard card, Currency cur) {
		String id = card.getCardId();
		if (priceMap.containsKey(id)) {
			String prices = priceMap.get(id);
			int sep = prices.indexOf(":");
			if (sep != -1 && sep < prices.length() - 1) {
				prices = prices.substring(sep + 1);
			} else {
				prices = "-0.0001f";
			}
			float price = Float.valueOf(prices);
			return CurrencyConvertor.convertFromInto(price, getCurrency(), cur);
		}
		return 0f;
	}

	@Override
	public synchronized float getDbPriceFoil(String id, Currency cur) {
		if (priceMap.containsKey(id)) {
			String prices = priceMap.get(id);
			int sep = prices.indexOf(":");
			if (sep != -1 && sep < prices.length() - 1) {
				prices = prices.substring(sep + 1);
			} else {
				prices = "-0.0001f";
			}
			float price = Float.valueOf(prices);
			return CurrencyConvertor.convertFromInto(price, getCurrency(), cur);
		}
		return 0f;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractPriceProvider other = (AbstractPriceProvider) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	public static transient PricesXmlStreamWriter writer = new PricesXmlStreamWriter();

	@Override
	public void save() throws IOException {
		writer.write(this);
	}

	@Override
	public HashMap<String, String> getPriceMap() {
		return priceMap;
	}

	@Override
	public Properties getProperties() {
		return properties;
	}
}
