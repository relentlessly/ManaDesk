package com.reflexit.magiccards.ui.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Display;

import com.reflexit.magiccards.core.CachedImageNotFoundException;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.core.model.utils.MRUCache;
import com.reflexit.magiccards.core.sync.WebUtils;
import com.reflexit.magiccards.ui.MagicUIActivator;

/**
 * This class manages Card images, it is singleton and its resposible for
 * disposing them. Do not dispose these images!
 * 
 * @author elaskavaia
 *
 */
public class ImageCache {
	public final Image CARD_NOT_FOUND_IMAGE_TEMPLATE = ImageCreator.getInstance().getCardNotFoundImageTemplate();
	public static ImageCache INSTANCE = new ImageCache();
	private HashMap<Object, Image> map = new MRUCache<Object, Image>(500) {
		@Override
		protected boolean removeEldestEntry(Map.Entry eldest) {
			if (super.removeEldestEntry(eldest)) {
				Image image = (Image) eldest.getValue();
				if (image != CARD_NOT_FOUND_IMAGE_TEMPLATE) {
					image.dispose();
					eldest.setValue(null);
				}
				return true;
			}
			return false;
		};
	};
	private int cart;

	private ImageCache() {
	};

	public Image getCachedImage(Object element) {
		return map.get(element);
	}

	/**
	 * Images from this map will be disposed except
	 * CARD_NOT_FOUND_IMAGE_TEMPLATE
	 * 
	 * @param key
	 * @param value
	 */
	public void setCachedImage(Object key, Image value) {
		map.put(key, value);
	}

	/**
	 * If image is in cache it immediately returns, otherwise callback will be
	 * called when image is ready or error happened
	 * 
	 * @param element
	 * @param callback
	 * @return
	 */

	public Image getImage(Object element, final Runnable callback) {
		Image image = map.get(element);
		if (image != null)
			return image;

		if (element instanceof ICardGroup)
			return null;

		if (!(element instanceof IMagicCard))
			return null;

		final IMagicCard card = (IMagicCard) element;

		if (!in())
			return null;

		new Job("Loading card image " + card) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					ImageData data = null;

					try {
						String path = ImageCreator.getInstance().createCardPath(card, true, false);
						boolean resize = false;

						// background-safe : création de l’ImageData
						data = ImageCreator.createCardImageData(path, resize);

					} catch (CachedImageNotFoundException e) {
						if (!WebUtils.isWorkOffline()) {
							MagicUIActivator.log("Cached image not found for: " + card + " - " + e.getMessage());
						}
					} catch (Exception ex) {
						StringWriter sw = new StringWriter();
						ex.printStackTrace(new PrintWriter(sw));
						MagicUIActivator.log("Error creating ImageData for: " + card + "\n" + sw.toString());
					}

					final ImageData finalData = data;

					// UI thread : création de l’Image + mise en cache + callback
					Display.getDefault().asyncExec(() -> {
						if (!in())
							return;

						Image newImg = null;

						try {
							if (finalData != null) {
								newImg = new Image(Display.getDefault(), finalData);
							}
						} catch (SWTException ex) {
							MagicUIActivator.log(
									"Failed to create Image from ImageData for: " + card + " - " + ex.getMessage());
							newImg = null;
						}

						// fallback UI-safe
						if (newImg == null) {
							try {
								newImg = ImageCreator.getInstance().createCardNotFoundImage(card);
							} catch (Throwable t) {
								MagicUIActivator
										.log("Failed to create not-found image for: " + card + " - " + t.getMessage());
								return;
							}
						}

						// mise en cache + dispose de l’ancienne image
						synchronized (map) {
							Image old = map.put(card, newImg);

							if (old != null && old != newImg && !old.isDisposed()) {
								old.dispose();
							}
						}

						// callback UI
						try {
							if (callback != null)
								callback.run();
						} catch (Throwable t) {
							MagicUIActivator.log("Callback error for card: " + card + " - " + t.getMessage());
						}
					});

					return Status.OK_STATUS;

				} finally {
					out();
				}
			}
		}.schedule();

		return null;
	}

	protected synchronized boolean in() {
		if (cart > 50)
			return false;
		cart++;
		return true;
	}

	protected synchronized void out() {
		cart--;
	}
}
