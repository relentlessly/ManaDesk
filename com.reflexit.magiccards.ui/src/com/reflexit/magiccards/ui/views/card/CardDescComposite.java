/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.ui.views.card;

import java.awt.Desktop;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.IPostSelectionProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.core.DataManager;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.IMagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.storage.ICardStore;
import com.reflexit.magiccards.core.sync.GatherHelper;
import com.reflexit.magiccards.core.sync.WebUtils;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.utils.ImageCreator;
import com.reflexit.magiccards.ui.utils.StoredSelectionProvider;
import com.reflexit.magiccards.ui.utils.SymbolConverter;
import com.reflexit.magiccards.ui.views.columns.PowerColumn;

class CardDescComposite extends Composite {
	private Image image;
	private Label imageControl;
	private final CardDescView cardDescView;
	private Browser textBrowser;
	private Text textBackup;
	private Composite details;
	private IMagicCard card;
	private Image loadingImage;
	private Image cardNotFound;
	private PowerColumn powerProvider;
	private PowerColumn toughProvider;
	private Image transparentImageA;
	private Image transparentImageB;

	// --- scaling / debug fields ---
	private static final boolean DEBUG = true; // set false to silence debug logs
	private final int MIN_DISPLAY_WIDTH = 120; // minimum width in pixels (adjust to taste)
	private final int SCALE_THRESHOLD_PX = 16; // minimum pixel change to trigger a rescale
	private Image originalImageFull = null; // keep the original full-size image (owner)
	private int lastAppliedWidth = -1; // last width we scaled to (for threshold)

	// int width = 223, hight = 310;
	int width = 265, hight = 370;
	private StoredSelectionProvider selectionProvider = new StoredSelectionProvider();

	public CardDescComposite(CardDescView cardDescView1, Composite parent, int style) {
		super(parent, style | SWT.INHERIT_DEFAULT);
		// UI
		this.cardDescView = cardDescView1;
		Composite panel = this;
		panel.setLayout(new GridLayout());
		this.imageControl = new Label(panel, SWT.INHERIT_DEFAULT);
		GridDataFactory.fillDefaults() //
				.grab(true, false) //
				.align(SWT.CENTER, SWT.BEGINNING)//
				.hint(width + 2, hight + 2).applyTo(this.imageControl);
		createImages();
		addDisposeListener(e -> {
			if (transparentImageA != null && !transparentImageA.isDisposed()) {
				transparentImageA.dispose();
			}
			if (transparentImageB != null && !transparentImageB.isDisposed()) {
				transparentImageB.dispose();
			}
		});

		this.powerProvider = new PowerColumn(MagicCardField.POWER, null, null);
		this.toughProvider = new PowerColumn(MagicCardField.TOUGHNESS, null, null);
		details = new Composite(panel, SWT.INHERIT_DEFAULT);
		GridDataFactory.fillDefaults()//
				.align(SWT.FILL, SWT.FILL)//
				.grab(true, true)//
				.applyTo(details);
		details.setLayout(new StackLayout());
		this.textBackup = new Text(details, SWT.WRAP | SWT.INHERIT_DEFAULT);
		// textBackup.setBackground(getDisplay().getSystemColor(SWT.COLOR_BLUE));
		try {
			// if (true)
			// throw new SWTError();
			this.textBrowser = new Browser(details, SWT.WRAP | SWT.INHERIT_FORCE);
			textBrowser.addLocationListener(new LocationAdapter() {
				@Override
				public void changing(LocationEvent event) {
					String location = event.location;
					if (location.equals("about:blank"))
						return;
					try {
						if (location.contains("https:")) {
							if (WebUtils.isWorkOffline())
								return;

							if (Desktop.isDesktopSupported()
									&& Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
								URI url = new URI(location);
								Desktop.getDesktop().browse(url);
							}

							// Nothing else to do
							event.doit = false;
						} else {

							String cardId = GatherHelper.extractCardIdFromURL(new URL(location));
							if (cardId != null) {
								event.doit = false;
								ICardStore<IMagicCard> magicDBStore = DataManager.getCardHandler().getMagicDBStore();
								IMagicCard card2 = magicDBStore.getCard(cardId);
								if (card2 != null) {
									cardDescView.setSelection(new StructuredSelection(card2));
								}
							}
						}
					} catch (MalformedURLException e) {
						MagicLogger.log(e);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (URISyntaxException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
			swapVisibility(textBrowser, textBackup);

		} catch (Throwable e) {
			textBrowser = null;
			MagicUIActivator.log(e);
			swapVisibility(textBackup, textBrowser);
		}
		// at end of constructor
		createImages();
		installResizeHandler();

	}

	private void createImages() {
		int border = 10;
		{
			if (transparentImageA != null && !transparentImageA.isDisposed()) {
				transparentImageA.dispose();
			}

			transparentImageA = ImageCreator.createTransparentImage(width - 2 * border, hight - 2 * border);
			GC gc = new GC(transparentImageA);
			gc.setForeground(getForeground());
			gc.drawText("Loading...", 10, 10, true);
			gc.dispose();
			this.loadingImage = ImageCreator.drawBorder(transparentImageA, border);
		}
		{
			if (transparentImageB != null && !transparentImageB.isDisposed()) {
				transparentImageB.dispose();
			}
			transparentImageB = ImageCreator.createTransparentImage(width - 2 * border, hight - 2 * border);
			GC gc = new GC(transparentImageB);
			gc.setForeground(getForeground());
			gc.drawText("Can't find image", 10, 10, true);
			gc.dispose();
			this.cardNotFound = ImageCreator.drawBorder(transparentImageB, border);
		}
	}

	public void setImage(IMagicCard card, Image remoteImage) {
		if (card == this.card) {
			setImage(remoteImage);
		}
	}

	public void setImageNotFound(IMagicCard card, Throwable e) {
		if (card == this.card) {
			this.image = ImageCreator.getInstance().createCardNotFoundImage(card);
			this.imageControl.setImage(this.image);
		}
	}

	private void setImage(Image remoteImage) {
		// This method takes ownership of remoteImage (same semantic as original code).
		// It stores the full-size original in originalImageFull and displays a scaled
		// version in the label according to available width, min/max and threshold.

		if (imageControl == null || imageControl.isDisposed()) {
			// nothing to do
			if (remoteImage != null && !remoteImage.isDisposed()) {
				// caller expects us to take ownership; dispose to avoid leak
				remoteImage.dispose();
			}
			return;
		}

		// 1) Dispose currently displayed image (but keep originalImageFull for re-scaling)
		if (this.image != null && this.image != this.loadingImage && this.image != this.cardNotFound) {
			try {
				imageControl.setImage(null); // remove from widget before disposing
			} catch (Throwable ignored) {
			}
			if (!this.image.isDisposed()) {
				this.image.dispose();
			}
			this.image = null;
		}

		// 2) Dispose previous original full image if different and not special
		if (this.originalImageFull != null && this.originalImageFull != remoteImage
				&& this.originalImageFull != this.loadingImage && this.originalImageFull != this.cardNotFound) {
			if (!this.originalImageFull.isDisposed()) {
				this.originalImageFull.dispose();
			}
		}

		// 3) If no new image, stop here (keep placeholders handled elsewhere)
		if (remoteImage == null) {
			this.originalImageFull = null;
			lastAppliedWidth = -1;
			return;
		}

		// 4) Store the full-size original (we own it now)
		this.originalImageFull = remoteImage;

		// 5) Determine original size and available width
		int origW = remoteImage.getBounds().width;
		int origH = remoteImage.getBounds().height;
		if (DEBUG) {
			System.out.println("[CD] setImage() ENTER orig=" + origW + "x" + origH);
		}

		// compute available width from the imageControl parent (prefer parent client area)
		Composite parent = imageControl.getParent();
		int availW = -1;
		if (parent != null && !parent.isDisposed()) {
			availW = parent.getSize().x;
			// if parent has margins or borders, subtract a small margin
			availW = Math.max(0, availW - 8);
		}
		// fallback to control width if parent not sized yet
		if (availW <= 0) {
			availW = imageControl.getSize().x;
		}
		if (availW <= 0) {
			// no layout yet: use original width (will be scaled later when layout happens)
			availW = origW;
		}

		// 6) Clamp target width between MIN_DISPLAY_WIDTH and original width
		int targetW = Math.max(MIN_DISPLAY_WIDTH, Math.min(availW, origW));

		// 7) Threshold: avoid scaling if change is small
		if (lastAppliedWidth > 0 && Math.abs(targetW - lastAppliedWidth) < SCALE_THRESHOLD_PX) {
			if (DEBUG) {
				System.out.println(
						"[CD] setImage() skip scaling: targetW=" + targetW + " lastApplied=" + lastAppliedWidth);
			}
			// But still ensure layout hints reflect the original image if needed
			GridData ld = (GridData) imageControl.getLayoutData();
			if (ld != null) {
				ld.minimumWidth = Math.min(origW, Math.max(MIN_DISPLAY_WIDTH, lastAppliedWidth));
				ld.minimumHeight = Math.round(ld.minimumWidth * (origH / (float) origW));
				ld.widthHint = ld.minimumWidth;
				ld.heightHint = ld.minimumHeight;
				if (!this.isDisposed())
					this.layout(true, true);
			}
			// keep originalImageFull for future resizes
			return;
		}

		// 8) Compute proportional height
		int targetH = Math.round(origH * (targetW / (float) origW));

		if (DEBUG) {
			System.out.println("[CD] setImage() availW=" + availW + " -> target=" + targetW + "x" + targetH);
		}

		// 9) Create scaled ImageData using ImageCreator helper (preserves alpha)
		ImageData scaledData = null;
		try {
			scaledData = ImageCreator.scaleImageDataWithGC(imageControl.getDisplay(), remoteImage.getImageData(),
					targetW, targetH);
		} catch (Throwable t) {
			MagicUIActivator.log("Scaling failed, falling back to original image");
			t.printStackTrace();
		}

		Image scaledImage = null;
		if (scaledData != null) {
			try {
				scaledImage = new Image(imageControl.getDisplay(), scaledData);
			} catch (SWTException ex) {
				MagicUIActivator.log("Failed to create scaled Image from ImageData");
				scaledImage = null;
			}
		}

		// 10) If scaling failed, fall back to original (but still clamp hints)
		if (scaledImage == null) {
			if (DEBUG)
				System.out.println("[CD] setImage() scaling returned null, using original image");
			scaledImage = remoteImage; // use original as displayed image
			// Note: originalImageFull already references remoteImage
		} else {
			// If we created a new scaledImage, we must not dispose originalImageFull here.
			// originalImageFull remains the full-size owner for future resizes.
		}

		// 11) Apply scaled image to control and update layout hints
		try {
			imageControl.setImage(scaledImage);
		} catch (Throwable ignored) {
		}

		GridData ld = (GridData) imageControl.getLayoutData();
		if (ld == null) {
			ld = new GridData(SWT.FILL, SWT.FILL, true, true);
			imageControl.setLayoutData(ld);
		}
		ld.minimumWidth = scaledImage.getBounds().width + 1;
		ld.minimumHeight = scaledImage.getBounds().height + 1;
		ld.widthHint = ld.minimumWidth;
		ld.heightHint = ld.minimumHeight;

		// 12) Manage disposal: keep originalImageFull as the full-size owner,
		// and keep this.image as the currently displayed image (scaled or original).
		// Dispose previous displayed image already done at top.
		this.image = scaledImage;

		// 13) Update lastAppliedWidth and relayout
		lastAppliedWidth = targetW;
		if (!this.isDisposed()) {
			this.layout(true, true);
		}

		if (DEBUG) {
			System.out.println("[CD] setImage() EXIT applied=" + scaledImage.getBounds().width + "x"
					+ scaledImage.getBounds().height + " lastAppliedWidth=" + lastAppliedWidth);
		}
	}

	private void adjustImageToWidth(int availW) {
		if (originalImageFull == null || originalImageFull.isDisposed()) {
			if (DEBUG)
				System.out.println("[CD] adjustImageToWidth() no original image -> skip");
			return;
		}
		if (availW <= 0)
			return;

		int origW = originalImageFull.getBounds().width;
		int origH = originalImageFull.getBounds().height;

		// subtract small margin
		int targetW = Math.max(MIN_DISPLAY_WIDTH, Math.min(availW - 8, origW));
		if (targetW <= 0)
			targetW = Math.min(origW, Math.max(MIN_DISPLAY_WIDTH, availW));

		if (lastAppliedWidth > 0 && Math.abs(targetW - lastAppliedWidth) < SCALE_THRESHOLD_PX) {
			if (DEBUG)
				System.out.println("[CD] adjustImageToWidth() skip: targetW=" + targetW + " last=" + lastAppliedWidth);
			return;
		}

		int targetH = Math.round(origH * (targetW / (float) origW));
		if (DEBUG)
			System.out.println("[CD] adjustImageToWidth() resizing to " + targetW + "x" + targetH);

		ImageData scaledData = null;
		try {
			scaledData = ImageCreator.scaleImageDataWithGC(imageControl.getDisplay(), originalImageFull.getImageData(),
					targetW, targetH);
		} catch (Throwable t) {
			MagicUIActivator.log("adjustImageToWidth: scaling failed");
			t.printStackTrace();
		}

		Image newDisplayed = null;
		if (scaledData != null) {
			try {
				newDisplayed = new Image(imageControl.getDisplay(), scaledData);
			} catch (SWTException ex) {
				MagicUIActivator.log("adjustImageToWidth: failed to create Image from scaled data");
				newDisplayed = null;
			}
		}

		if (newDisplayed == null) {
			// fallback to original full image (clamped)
			newDisplayed = originalImageFull;
		}

		// Dispose previous displayed image if it was a scaled instance (not the originalImageFull)
		if (this.image != null && this.image != originalImageFull && this.image != loadingImage
				&& this.image != cardNotFound) {
			try {
				imageControl.setImage(null);
			} catch (Throwable ignored) {
			}
			if (!this.image.isDisposed())
				this.image.dispose();
		}

		// Apply new displayed image
		try {
			imageControl.setImage(newDisplayed);
		} catch (Throwable ignored) {
		}

		GridData ld = (GridData) imageControl.getLayoutData();
		if (ld == null) {
			ld = new GridData(SWT.FILL, SWT.FILL, true, true);
			imageControl.setLayoutData(ld);
		}
		ld.minimumWidth = newDisplayed.getBounds().width + 1;
		ld.minimumHeight = newDisplayed.getBounds().height + 1;
		ld.widthHint = ld.minimumWidth;
		ld.heightHint = ld.minimumHeight;

		this.image = newDisplayed;
		lastAppliedWidth = targetW;

		if (!this.isDisposed())
			this.layout(true, true);

		if (DEBUG) {
			System.out.println("[CD] adjustImageToWidth() applied " + newDisplayed.getBounds().width + "x"
					+ newDisplayed.getBounds().height + " lastAppliedWidth=" + lastAppliedWidth);
		}
	}

	private void installResizeHandler() {
		final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(0);
		Composite parent = imageControl.getParent();
		if (parent == null || parent.isDisposed())
			return;

		parent.addListener(SWT.Resize, e -> {
			if (originalImageFull == null || originalImageFull.isDisposed())
				return;
			int availW = parent.getSize().x;
			if (availW <= 0)
				return;
			// debounce
			int id = pending.incrementAndGet();
			Display display = parent.getDisplay();
			display.timerExec(80, () -> {
				if (id != pending.get())
					return;
				if (parent.isDisposed())
					return;
				int w = parent.getSize().x;
				if (w <= 0)
					return;
				adjustImageToWidth(w);
			});
		});
	}

	private boolean logOnce = false;

	public void setCard(IMagicCard card) {
		this.card = card;
		if (card == IMagicCard.DEFAULT || card == null) {
			selectionProvider.setSelection(new StructuredSelection());
		} else {
			selectionProvider.setSelection(new StructuredSelection(card));
		}
	}

	@Override
	public void setMenu(Menu menu) {
		super.setMenu(menu);
		imageControl.setMenu(menu);
		if (textBrowser != null)
			textBrowser.setMenu(menu);
		textBackup.setMenu(menu);
	}

	protected void setLoadingImage(IMagicCard card) {
		if (card == IMagicCard.DEFAULT) {
			return;
		}
		try {
			setImage(this.loadingImage);
		} catch (RuntimeException e) {
			MagicUIActivator.log(e);
		}
	}

	public void setText(IMagicCard card) {
		if (card == IMagicCard.DEFAULT) {
			return;
		}
		try {
			if (textBrowser != null) {
				String data = getCardDataHtml(card);
				String text = getText(card);
				String links = getLinks(card);
				String oracle = getOracle(card, text);
				String rulings = getCardRulingsHtml(card);
				this.textBrowser.setText(SymbolConverter.wrapHtml(links + data + text + oracle + rulings, textBrowser));
				swapVisibility(textBrowser, textBackup);
				return;
			}
		} catch (Exception e) {
			if (logOnce == false) {
				MagicUIActivator.log(e);
				logOnce = true;
			}
		}
		String data = getCardDataText(card);
		String text = getText(card);
		text = text.replaceAll("<br>", "\n");
		this.textBackup.setText(data + text);
		swapVisibility(textBackup, textBrowser);
	}

	protected String getText(IMagicCard card) {
		String text = card.getText();
		if (text == null || text.length() == 0)
			text = card.getOracleText();
		if (text == null || text.length() == 0)
			text = "";
		return text;
	}

	protected String getOracle(IMagicCard card, String text) {
		String oracle = card.getOracleText();
		if (text != null && text.length() != 0 && !text.equals(oracle)) {
			oracle = "<br><br>Oracle:<br>" + oracle;
		} else {
			oracle = "";
		}
		return oracle;
	}

	protected String getLinks(IMagicCard card) {
		String links = "";
		String flipId = card.getFlipId();
		if (flipId != null) {
			if (flipId.equals(card.getCardId())) {
				MagicLogger.log("Same flip id for " + card.getCardId());
				flipId = "-" + flipId;
			}
			links = "<a href=\"" + GatherHelper.createImageDetailURL(flipId) + "\">Flip</a><br><br>";
		}
		return links;
	}

	private void swapVisibility(Control con1, Control con2) {
		((StackLayout) details.getLayout()).topControl = con1;
		details.layout(true, true);
		redraw();
	}

	private String getCardDataText(IMagicCard card) {
		String pt = "";
		if (card.getToughness() != null && card.getToughness().length() > 0) {
			pt = powerProvider.getText(card) + "/" + toughProvider.getText(card);
		}
		String data = card.getName() + "\n" + getType(card);
		if (pt.length() > 0) {
			data += "\n" + pt;
		} else {
			data += "\n";
		}
		String num = getCollectorNumber(card);
		data += "\n" + card.getSet() + " (" + getRarity(card) + ") " + num + "\n";
		return data;
	}

	public String getRarity(IMagicCard card) {
		return card.getRarity() == null ? "Unknown Rarity" : card.getRarity();
	}

	public String getType(IMagicCard card) {
		return card.getType() == null ? "Unknown Type" : card.getType();
	}

	private String getCardDataHtml(IMagicCard card) {
		String text = getCardDataText(card);
		text = text.replaceAll("\n", "<br/>\n");
		return text + "<p/>";
	}

	public String getCollectorNumber(IMagicCard card) {
		String num = (String) card.get(MagicCardField.COLLNUM);
		if (num != null)
			num = "[" + num + "]";
		return num;
	}

	private String getCardRulingsHtml(IMagicCard card) {
		String srulings = card.getRulings();
		if (srulings == null || srulings.length() == 0) {
			return "";
		}
		String data = "<p>Rulings:<ul>";
		String rulings[] = srulings.split("\\n");
		for (String ruling : rulings) {
			data += "<li>" + ruling + "</li>";
		}
		data += "</ul><p/>";
		return data;
	}

	@Override
	public void dispose() {
		if (this.image != null && !this.image.isDisposed()) {
			this.image.dispose();
		}
		this.image = null;
		if (this.originalImageFull != null && !this.originalImageFull.isDisposed()) {
			this.originalImageFull.dispose();
		}
		this.originalImageFull = null;
		super.dispose();
	}

	public IMagicCard getCard() {
		return this.card;
	}

	public IPostSelectionProvider getSelectionProvider() {
		return selectionProvider;
	}
}