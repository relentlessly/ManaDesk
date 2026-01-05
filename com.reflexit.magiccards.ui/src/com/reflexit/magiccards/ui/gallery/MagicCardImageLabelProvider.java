

/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.ui.gallery;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;

import com.reflexit.magiccards.core.model.CardGroup;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.IMagicCardPhysical;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.abs.ICardGroup;
import com.reflexit.magiccards.ui.utils.ImageCache;
import com.reflexit.magiccards.ui.utils.WaitUtils;

final class MagicCardImageLabelProvider extends LabelProvider implements IImageOverlayRenderer {
	private StructuredViewer viewer;

	public MagicCardImageLabelProvider(StructuredViewer viewer) {
		this.viewer = viewer;
	}

	private ImageCache cache = ImageCache.INSTANCE;
	private final Set<Image> galleryImages = Collections.synchronizedSet(new HashSet<>());

	@Override
	public void dispose() {
		// Nettoyer toutes les copies créées par ce label provider
		synchronized (galleryImages) {
			for (Image img : galleryImages) {
				if (img != null && !img.isDisposed()) {
					img.dispose();
				}
			}
			galleryImages.clear();
		}

		super.dispose();
	}

	@Override
	public String getText(Object element) {
		if (element instanceof ICardGroup) {
			ICardGroup group = (ICardGroup) element;
			return group.getName();
		} else if (element instanceof IMagicCard) {
			return ((IMagicCard) element).getName();
		}
		return String.valueOf(element);
	}

	@Override
	public Image getImage(final Object element) {

		Object candidate = element;
		if (element instanceof CardGroup) {
			candidate = ((CardGroup) element).getFirstCard();
		}
		if (!(candidate instanceof IMagicCard)) {
			return null;
		}

		final IMagicCard card = (IMagicCard) candidate;

		// Image "source" venant du cache
		Image base = cache.getImage(card, () -> refreshCallback(element));

		if (base == null || base.isDisposed()) {
			base = cache.CARD_NOT_FOUND_IMAGE_TEMPLATE;
			if (base == null || base.isDisposed())
				return null;
		}

		// Copie dédiée à l’affichage (Gallery / viewer)
		Image copy = new Image(Display.getDefault(), base, SWT.IMAGE_COPY);
		galleryImages.add(copy);
		return copy;
	}

	protected void refreshCallback(final Object element) {
		if (viewer == null)
			return;
		WaitUtils.asyncExec(() -> viewer.refresh(element, true));
	}

	@Override
	public void drawAllOverlays(GC gc, Object element, int x, int y, Point imageSize, int xShift, int yShift) {
		String text = getCountDecoration(element);
		if (!text.isEmpty()) {
			gc.setBackground(gc.getDevice().getSystemColor(SWT.COLOR_BLACK));
			gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
			int x1 = x + xShift - 5;
			int y1 = y + yShift + imageSize.y - 20 + 5;
			gc.fillOval(x1, y1, 20, 20);
			gc.drawText(text, x1, y1, true);
		}
	}

	private String getCountDecoration(Object element) {
		if (element instanceof MagicCard) {
			return "";
		}
		if (element instanceof CardGroup && ((CardGroup) element).getFirstCard() instanceof MagicCard) {
			return "";
		}
		if (element instanceof IMagicCardPhysical) {
			String text = "x" + ((IMagicCardPhysical) element).getCount();
			return text;
		}
		return "";
	}
}