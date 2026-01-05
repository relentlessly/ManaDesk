

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.model.storage;

import java.util.Collection;
import java.util.List;

import com.reflexit.magiccards.core.model.abs.ICardSet;

public interface ICardStore<T>
		extends ICardSet<T>, IMergeable<T>, ICardEventManager<T>, ILocatable, IStorageContainer<T> {
	public String getName();

	public String getComment();

	public boolean isVirtual();

	public boolean isUnsorted();

	public T getCard(String id);

	public Collection<T> getCards(String id);

	public void initialize();

	public void reindex();

	public List<T> getCards();

	public void reload();

	public Object getLast();
}