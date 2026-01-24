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
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Rectangle;
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
	// keep the true disk/original image separate so we can scale up to it
	private Image originalImageOnDisk = null;
	// Debounce / force flags
	private final java.util.concurrent.atomic.AtomicInteger imageUpdatePending = new java.util.concurrent.atomic.AtomicInteger(
			0);
	// !!! RD private volatile boolean forceOriginalSize = false; // set by CardDescView when asScanned == true
	private org.eclipse.swt.graphics.ImageData pendingDisplayData = null;

	// --- scaling / debug fields ---
	private static final boolean DEBUG = false; // set false to silence debug logs
	private final int MIN_DISPLAY_WIDTH = 120; // minimum width in pixels (adjust to taste)
	private final int SCALE_THRESHOLD_PX = 16; // minimum pixel change to trigger a rescale
	private Image originalImageFull = null; // keep the original full-size image (owner)
	private int lastAppliedWidth = -1; // last width we scaled to (for threshold)
	// unified border/margin used for image templates and layout calculations
	private static final int IMAGE_BORDER = 10;

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
		installResizeHandler();

	}

	private void createImages() {
		int border = IMAGE_BORDER;

		// Dispose previous images if present (defensive)
		try {
			if (transparentImageA != null && !transparentImageA.isDisposed()) {
				transparentImageA.dispose();
				transparentImageA = null;
			}
		} catch (Throwable ignored) {
		}
		try {
			if (loadingImage != null && !loadingImage.isDisposed()) {
				loadingImage.dispose();
				loadingImage = null;
			}
		} catch (Throwable ignored) {
		}

		try {
			// Create transparent base and draw "Loading..." text on it
			transparentImageA = ImageCreator.createTransparentImage(width - 2 * border, hight - 2 * border);
			if (transparentImageA != null) {
				GC gc = null;
				try {
					gc = new GC(transparentImageA);
					gc.setForeground(getForeground());
					gc.drawText("Loading...", 10, 10, true);
				} catch (Throwable t) {
					MagicUIActivator.log("createImages: error drawing loading text");
					MagicUIActivator.log(t);
				} finally {
					if (gc != null && !gc.isDisposed())
						gc.dispose();
				}
				// draw border around the transparent image
				Image bordered = ImageCreator.drawBorder(transparentImageA, border);
				// dispose the transparentImageA if drawBorder returned a new image (we keep bordered)
				if (transparentImageA != null && !transparentImageA.isDisposed()) {
					transparentImageA.dispose();
				}
				transparentImageA = null;
				this.loadingImage = bordered;
			} else {
				this.loadingImage = null;
			}
		} catch (Throwable t) {
			MagicUIActivator.log("createImages: failed to create loading image");
			MagicUIActivator.log(t);
			this.loadingImage = null;
		}

		// Second image: "Can't find image"
		try {
			if (transparentImageB != null && !transparentImageB.isDisposed()) {
				transparentImageB.dispose();
				transparentImageB = null;
			}
		} catch (Throwable ignored) {
		}

		try {
			transparentImageB = ImageCreator.createTransparentImage(width - 2 * border, hight - 2 * border);
			if (transparentImageB != null) {
				GC gc = null;
				try {
					gc = new GC(transparentImageB);
					gc.setForeground(getForeground());
					gc.drawText("Can't find image", 10, 10, true);
				} catch (Throwable t) {
					MagicUIActivator.log("createImages: error drawing not-found text");
					MagicUIActivator.log(t);
				} finally {
					if (gc != null && !gc.isDisposed())
						gc.dispose();
				}
				Image bordered = ImageCreator.drawBorder(transparentImageB, border);
				if (transparentImageB != null && !transparentImageB.isDisposed()) {
					transparentImageB.dispose();
				}
				transparentImageB = null;
				this.cardNotFound = bordered;
			} else {
				this.cardNotFound = null;
			}
		} catch (Throwable t) {
			MagicUIActivator.log("createImages: failed to create cardNotFound image");
			MagicUIActivator.log(t);
			this.cardNotFound = null;
		}
	}

	/**
	 * Backwards-compatible overload used by callers that pass the card and the Image.
	 * If the passed card matches the composite's current card, delegate to setImage(Image).
	 * If the card is different, dispose the provided image (we don't keep it).
	 */
	public void setImage(IMagicCard card, Image remoteImage) {
		if (card == null) {
			// nothing to do; dispose incoming image to avoid leaks
			if (remoteImage != null && !remoteImage.isDisposed()) {
				try {
					remoteImage.dispose();
				} catch (Throwable ignored) {
				}
			}
			return;
		}

		// If this composite currently displays the same card, apply the image.
		if (card == this.card) {
			// Delegate to the Image-only setter which owns the image copy semantics.
			setImage(remoteImage);
		} else {
			// Not the same card: caller passed an image we should not keep — dispose it.
			if (remoteImage != null && !remoteImage.isDisposed()) {
				try {
					remoteImage.dispose();
				} catch (Throwable ignored) {
				}
			}
		}
	}

	/**
	 * Replace the currently displayed image with remoteImage.
	 * This method takes ownership of the displayed image by creating a UI copy
	 * (new Image(display, remoteImage.getImageData())) so callers may safely
	 * dispose their Image without affecting the composite.
	 */
	private void setImage(Image remoteImage) {
		// Defensive: ensure widget still exists
		if (imageControl == null || imageControl.isDisposed()) {
			if (remoteImage != null && !remoteImage.isDisposed()) {
				try {
					remoteImage.dispose();
				} catch (Throwable ignored) {
				}
			}
			return;
		}

		Display display = imageControl.getDisplay();

		// Dispose currently displayed image (we own this.image) BEFORE creating new UI copy
		// but keep a reference to old so we can dispose it AFTER the swap
		Image oldDisplayed = this.image;

		// If no new image provided, clear hints and return
		if (remoteImage == null) {
			try {
				imageControl.setImage(null);
			} catch (Throwable ignored) {
			}
			GridData ld = (GridData) imageControl.getLayoutData();
			if (ld != null) {
				ld.minimumWidth = 0;
				ld.minimumHeight = 0;
				ld.widthHint = SWT.DEFAULT;
				ld.heightHint = SWT.DEFAULT;
			}
			lastAppliedWidth = -1;
			this.image = null;
			if (!this.isDisposed())
				this.layout(true, true);
			// dispose old after clearing
			try {
				if (oldDisplayed != null && !oldDisplayed.isDisposed() && oldDisplayed != loadingImage
						&& oldDisplayed != cardNotFound)
					oldDisplayed.dispose();
			} catch (Throwable ignored) {
			}
			return;
		}

		// Create a UI-owned copy of the incoming image to avoid "Graphic is disposed"
		Image uiFull = null;
		try {
			if (remoteImage.isDisposed()) {
				if (DEBUG)
					System.out.println("[CD] setImage() incoming image is disposed -> abort");
				return;
			}
			uiFull = new Image(display, remoteImage.getImageData());
		} catch (Throwable t) {
			MagicUIActivator.log("setImage: failed to copy incoming image");
			MagicUIActivator.log(t);
			// fallback: use the incoming image if it's still valid
			if (remoteImage != null && !remoteImage.isDisposed()) {
				uiFull = remoteImage;
			} else {
				uiFull = null;
			}
		} finally {
			// dispose the caller's image if we created a copy (caller shouldn't keep it)
			if (uiFull != null && uiFull != remoteImage) {
				try {
					if (remoteImage != null && !remoteImage.isDisposed())
						remoteImage.dispose();
				} catch (Throwable ignored) {
				}
			}
		}

		if (uiFull == null) {
			if (DEBUG)
				System.out.println("[CD] setImage() no usable image after copy -> abort");
			return;
		}

		// Replace originalImageFull with the new UI-owned full image (dispose previous)
		try {
			if (this.originalImageFull != null && !this.originalImageFull.isDisposed()
					&& this.originalImageFull != loadingImage && this.originalImageFull != cardNotFound) {
				try {
					this.originalImageFull.dispose();
				} catch (Throwable ignored) {
				}
			}
		} catch (Throwable ignored) {
		}
		this.originalImageFull = uiFull;

		// Compute original size safely
		int origW = 1, origH = 1;
		try {
			Rectangle b = this.originalImageFull.getBounds();
			origW = Math.max(1, b.width);
			origH = Math.max(1, b.height);
		} catch (Throwable t) {
			MagicUIActivator.log("setImage: failed to read original image bounds");
			origW = width;
			origH = hight;
		}

		if (DEBUG)
			System.out.println("[CD] setImage() ENTER orig=" + origW + "x" + origH);

		// Determine available width using unified helper
		int availW = computeAvailableWidth();
		if (availW <= 0)
			availW = origW;

		// Clamp and threshold
		int targetW = Math.max(MIN_DISPLAY_WIDTH, Math.min(availW, origW));
		if (lastAppliedWidth > 0 && Math.abs(targetW - lastAppliedWidth) < SCALE_THRESHOLD_PX) {
			if (DEBUG)
				System.out.println(
						"[CD] setImage() skip scaling: targetW=" + targetW + " lastApplied=" + lastAppliedWidth);
			// update layout hints to reflect current image size
			GridData ld = (GridData) imageControl.getLayoutData();
			if (ld != null && this.image != null) {
				ld.minimumWidth = Math.min(origW, Math.max(MIN_DISPLAY_WIDTH, lastAppliedWidth));
				ld.minimumHeight = Math.round(ld.minimumWidth * (origH / (float) origW));
				ld.widthHint = ld.minimumWidth;
				ld.heightHint = ld.minimumHeight;
				if (!this.isDisposed())
					this.layout(true, true);
			}
			// dispose oldDisplayed if it is not the same as current image
			try {
				if (oldDisplayed != null && oldDisplayed != this.image && oldDisplayed != loadingImage
						&& oldDisplayed != cardNotFound) {
					if (!oldDisplayed.isDisposed())
						oldDisplayed.dispose();
				}
			} catch (Throwable ignored) {
			}
			return;
		}

		int targetH = Math.round(origH * (targetW / (float) origW));
		if (DEBUG)
			System.out.println("[CD] setImage() availW=" + availW + " -> target=" + targetW + "x" + targetH);

		// Create scaled image off-screen
		ImageData scaledData = null;
		try {
			scaledData = ImageCreator.scaleImageDataWithGC(display, this.originalImageFull.getImageData(), targetW,
					targetH);
		} catch (Throwable t) {
			MagicUIActivator.log("setImage: scaling failed, will use full image");
			MagicUIActivator.log(t);
			scaledData = null;
		}

		Image scaledImage = null;
		if (scaledData != null) {
			try {
				scaledImage = new Image(display, scaledData);
			} catch (Throwable t) {
				MagicUIActivator.log(t);
				scaledImage = null;
			}
		}
		if (scaledImage == null) {
			// fallback to the full image
			scaledImage = this.originalImageFull;
		}

		// Atomic swap: setRedraw(false) -> setImage -> update layout -> setRedraw(true)
		try {
			imageControl.setRedraw(false);
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

			// update reference to current displayed image
			this.image = scaledImage;
			lastAppliedWidth = targetW;
			if (!this.isDisposed())
				this.layout(true, true);
		} finally {
			try {
				imageControl.setRedraw(true);
			} catch (Throwable ignored) {
			}
		}

		// Dispose old displayed image if it was a scaled instance (not the disk template)
		try {
			if (oldDisplayed != null && oldDisplayed != this.image && oldDisplayed != loadingImage
					&& oldDisplayed != cardNotFound) {
				if (!oldDisplayed.isDisposed())
					oldDisplayed.dispose();
			}
		} catch (Throwable ignored) {
		}

		if (DEBUG) {
			try {
				System.out.println("[CD] setImage() EXIT applied=" + this.image.getBounds().width + "x"
						+ this.image.getBounds().height + " lastAppliedWidth=" + lastAppliedWidth);
			} catch (Throwable ignored) {
			}
		}
	}

	/**
	 * Return the available width for the image area in pixels.
	 * Uses the parent client area and subtracts a small consistent margin.
	 */
	private int computeAvailableWidth() {
		if (imageControl == null || imageControl.isDisposed())
			return width;
		Composite parent = imageControl.getParent();
		if (parent == null || parent.isDisposed()) {
			return Math.max(1, imageControl.getSize().x);
		}
		// Use client area to avoid including sash/outer decorations
		Rectangle client = parent.getClientArea();
		int avail = client.width;
		// Use the unified border constant so createImages() and layout use the same margin
		final int MARGIN = IMAGE_BORDER;
		avail = Math.max(1, avail - MARGIN);
		return avail;
	}

	private void scheduleApplyImageDebounced() {
		final int id = imageUpdatePending.incrementAndGet();
		Display display = getDisplay();
		// 80ms debounce; adjust to 40–150ms if you want faster/slower responsiveness
		display.timerExec(80, () -> {
			if (id != imageUpdatePending.get())
				return; // newer update scheduled
			if (imageControl == null || imageControl.isDisposed())
				return;

			// Build the Image to pass to applyImage: prefer pendingDisplayData, fallback to originalImageOnDisk
			Image toApply = null;
			try {
				if (pendingDisplayData != null) {
					toApply = new Image(getDisplay(), pendingDisplayData);
					// clear pendingDisplayData after consuming
					pendingDisplayData = null;
				} else if (originalImageOnDisk != null && !originalImageOnDisk.isDisposed()) {
					// use a copy so applyImage owns it
					toApply = new Image(getDisplay(), originalImageOnDisk.getImageData());
				}
			} catch (Throwable t) {
				MagicUIActivator.log("scheduleApplyImageDebounced: failed to create image for apply");
				MagicUIActivator.log(t);
				if (toApply != null && !toApply.isDisposed()) {
					try {
						toApply.dispose();
					} catch (Throwable ignored) {
					}
				}
				toApply = null;
			}

			// Max size is always size on disk
			if (originalImageOnDisk != null && !originalImageOnDisk.isDisposed()) {
				try {
					if (toApply != null && toApply != originalImageOnDisk) {
						// dispose the temporary toApply; we'll use disk copy
						try {
							toApply.dispose();
						} catch (Throwable ignored) {
						}
					}
					toApply = new Image(getDisplay(), originalImageOnDisk.getImageData());
				} catch (Throwable t) {
					MagicUIActivator.log("scheduleApplyImageDebounced: failed to create force-original image");
					MagicUIActivator.log(t);
				}
			}

			// Finally call applyImage on UI thread (we are on UI thread already)
			if (toApply != null) {
				try {
					applyImage(toApply);
				} catch (Throwable t) {
					MagicUIActivator.log("scheduleApplyImageDebounced: applyImage failed");
					MagicUIActivator.log(t);
					if (!toApply.isDisposed())
						try {
							toApply.dispose();
						} catch (Throwable ignored) {
						}
				}
			}
		});
	}

	private void adjustImageToWidth(int availW) {
		if (imageControl == null || imageControl.isDisposed())
			return;
		if (originalImageFull == null || originalImageFull.isDisposed()) {
			// nothing to scale from; try to use disk original if present
			if (originalImageOnDisk == null || originalImageOnDisk.isDisposed())
				return;
		}

		// Compute max allowed width (prefer disk/original)
		int maxOrigW = (originalImageOnDisk != null && !originalImageOnDisk.isDisposed())
				? originalImageOnDisk.getBounds().width
				: (originalImageFull != null && !originalImageFull.isDisposed() ? originalImageFull.getBounds().width
						: width);

		// Use computeAvailableWidth() for consistent measurement
		int avail = computeAvailableWidth();
		int targetW = Math.max(MIN_DISPLAY_WIDTH, Math.min(avail, maxOrigW));

		// Threshold to avoid frequent small resizes
		if (lastAppliedWidth > 0 && Math.abs(targetW - lastAppliedWidth) < SCALE_THRESHOLD_PX) {
			if (DEBUG)
				System.out.println("[CD] adjustImageToWidth() skip: targetW=" + targetW + " last=" + lastAppliedWidth);
			return;
		}

		// Choose best source to scale from: prefer disk image if it is large enough
		Image scaleSource = originalImageFull;
		try {
			if (originalImageOnDisk != null && !originalImageOnDisk.isDisposed()) {
				int diskW = originalImageOnDisk.getBounds().width;
				if (diskW >= targetW)
					scaleSource = originalImageOnDisk;
			}
		} catch (Throwable ignored) {
		}

		if (scaleSource == null || scaleSource.isDisposed())
			return;

		// Compute proportional height
		int origW = Math.max(1, scaleSource.getBounds().width);
		int origH = Math.max(1, scaleSource.getBounds().height);
		int targetH = Math.round(origH * (targetW / (float) origW));

		if (DEBUG)
			System.out.println(
					"[CD] adjustImageToWidth() scaling from " + origW + "x" + origH + " to " + targetW + "x" + targetH);

		// Create scaled image off-screen
		ImageData scaledData = null;
		try {
			scaledData = ImageCreator.scaleImageDataWithGC(imageControl.getDisplay(), scaleSource.getImageData(),
					targetW, targetH);
		} catch (Throwable t) {
			MagicUIActivator.log("adjustImageToWidth: scaling failed", t);
			scaledData = null;
		}

		Image newDisplayed = null;
		if (scaledData != null) {
			try {
				newDisplayed = new Image(imageControl.getDisplay(), scaledData);
			} catch (Throwable t) {
				MagicUIActivator.log("adjustImageToWidth: failed to create Image from scaled data", t);
				newDisplayed = null;
			}
		}
		if (newDisplayed == null) {
			// fallback to scaleSource (already UI-owned)
			newDisplayed = scaleSource;
		}

		// Atomic swap to avoid flicker
		Image old = this.image;
		try {
			imageControl.setRedraw(false);
			imageControl.setImage(newDisplayed);

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
			if (!this.isDisposed())
				this.layout(true, true);
		} finally {
			try {
				imageControl.setRedraw(true);
			} catch (Throwable ignored) {
			}
		}

		// Dispose old displayed image if it was a scaled instance (not the disk template)
		try {
			if (old != null && old != this.image && old != loadingImage && old != cardNotFound) {
				if (!old.isDisposed())
					old.dispose();
			}
		} catch (Throwable ignored) {
		}

		lastAppliedWidth = targetW;
		if (DEBUG) {
			try {
				System.out.println("[CD] adjustImageToWidth() applied=" + this.image.getBounds().width + "x"
						+ this.image.getBounds().height + " lastAppliedWidth=" + lastAppliedWidth);
			} catch (Throwable ignored) {
			}
		}
	}

	private void installResizeHandler() {
		final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(0);
		Composite parent = imageControl.getParent();
		if (parent == null || parent.isDisposed())
			return;

		parent.addListener(SWT.Resize, e -> {
			// If we don't have an image yet, nothing to do
			if (originalImageFull == null || originalImageFull.isDisposed())
				return;

			// debounce
			int id = pending.incrementAndGet();
			Display display = parent.getDisplay();
			display.timerExec(80, () -> {
				if (id != pending.get())
					return; // newer update scheduled
				if (parent.isDisposed())
					return;
				// Use the unified computeAvailableWidth() so resize and selection use same measurement
				int avail = computeAvailableWidth();
				if (avail <= 0)
					return;
				adjustImageToWidth(avail);
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
		// If we already have a real image for this card, do not overwrite it with a placeholder.
		if ((this.originalImageFull != null && !this.originalImageFull.isDisposed())
				|| (this.originalImageOnDisk != null && !this.originalImageOnDisk.isDisposed())) {
			if (DEBUG)
				System.out.println("[CD] setLoadingImage() skip: real image already present");
			return;
		}

		try {
			Image template = this.loadingImage;
			Image toApply = null;
			if (template != null && !template.isDisposed()) {
				try {
					toApply = new Image(imageControl.getDisplay(), template.getImageData());
				} catch (Throwable t) {
					MagicUIActivator.log("setLoadingImage: failed to copy loadingImage template");
					MagicUIActivator.log(t);
					toApply = null;
				}
			}

			if (toApply == null) {
				try {
					Image tmp = ImageCreator.createTransparentImage(width - 20, hight - 20);
					if (tmp != null) {
						GC gc = null;
						try {
							gc = new GC(tmp);
							gc.setForeground(getForeground());
							gc.drawText("Loading...", 10, 10, true);
						} finally {
							if (gc != null && !gc.isDisposed())
								gc.dispose();
						}
						toApply = ImageCreator.drawBorder(tmp, IMAGE_BORDER);
						if (tmp != null && !tmp.isDisposed())
							tmp.dispose();
					}
				} catch (Throwable t) {
					MagicUIActivator.log("setLoadingImage: failed to create placeholder");
					MagicUIActivator.log(t);
					toApply = null;
				}
			}

			if (toApply != null) {
				// Only apply if still needed for the same card
				if (card == this.card) {
					setImage(toApply);
				} else {
					// not the same card, dispose
					try {
						if (!toApply.isDisposed())
							toApply.dispose();
					} catch (Throwable ignored) {
					}
				}
			}
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
		try {
			if (image != null && !image.isDisposed())
				image.dispose();
		} catch (Throwable ignored) {
		}
		try {
			if (originalImageFull != null && !originalImageFull.isDisposed())
				originalImageFull.dispose();
		} catch (Throwable ignored) {
		}
		try {
			if (originalImageOnDisk != null && !originalImageOnDisk.isDisposed())
				originalImageOnDisk.dispose();
		} catch (Throwable ignored) {
		}
		try {
			if (loadingImage != null && !loadingImage.isDisposed())
				loadingImage.dispose();
		} catch (Throwable ignored) {
		}
		try {
			if (cardNotFound != null && !cardNotFound.isDisposed())
				cardNotFound.dispose();
		} catch (Throwable ignored) {
		}
		try {
			if (transparentImageA != null && !transparentImageA.isDisposed())
				transparentImageA.dispose();
		} catch (Throwable ignored) {
		}
		try {
			if (transparentImageB != null && !transparentImageB.isDisposed())
				transparentImageB.dispose();
		} catch (Throwable ignored) {
		}
		super.dispose();
	}

	/**
	 * Called by CardDescView when ImageData is available.
	 * fullData: the unresized ImageData from disk (may be null if not available)
	 * displayData: the ImageData used for immediate display (may be null)
	 *
	 * This method stores the full-size image (if available) so the composite can
	 * scale up to the original size on future resizes. It also creates and applies
	 * the immediate displayed image (from displayData) to keep current behavior.
	 */
	public void onImageDataLoaded(org.eclipse.swt.graphics.ImageData fullData,
			org.eclipse.swt.graphics.ImageData displayData) {
		// Ensure UI thread for immediate small work; heavy work is scheduled
		if (Display.getCurrent() == null) {
			Display.getDefault().asyncExec(() -> onImageDataLoaded(fullData, displayData));
			return;
		}

		if (DEBUG) {
			System.out.println("[CD] onImageDataLoaded() ENTER fullData=" + (fullData != null) + " displayData="
					+ (displayData != null));
		}

		// Replace disk-original safely (dispose old later if needed)
		try {
			if (originalImageOnDisk != null && !originalImageOnDisk.isDisposed()) {
				originalImageOnDisk.dispose();
				originalImageOnDisk = null;
			}
		} catch (Throwable ignored) {
		}

		try {
			if (fullData != null) {
				originalImageOnDisk = new Image(getDisplay(), fullData);
				if (DEBUG) {
					Rectangle b = originalImageOnDisk.getBounds();
					System.out.println(
							"[CD] onImageDataLoaded() created originalImageOnDisk " + b.width + "x" + b.height);
				}
			} else {
				originalImageOnDisk = null;
				if (DEBUG)
					System.out.println("[CD] onImageDataLoaded() fullData null -> originalImageOnDisk cleared");
			}
		} catch (Throwable t) {
			MagicUIActivator.log("onImageDataLoaded: failed to create originalImageOnDisk");
			MagicUIActivator.log(t);
			originalImageOnDisk = null;
		}

		// If we just obtained the full/disk image, remove any placeholder currently shown
		// so it cannot overwrite the final image later.
		if (originalImageOnDisk != null && !originalImageOnDisk.isDisposed()) {
			try {
				if (imageControl != null && !imageControl.isDisposed()) {
					Image cur = imageControl.getImage();
					// If the widget currently shows one of our templates, clear it now.
					if (cur == loadingImage || cur == cardNotFound) {
						try {
							imageControl.setImage(null);
						} catch (Throwable ignored) {
						}
						// If our internal reference points to the placeholder, clear it too.
						if (this.image == cur)
							this.image = null;
					}
				}
			} catch (Throwable ignored) {
			}
		}

		// Create a small lightweight holder for the immediate display image data (we will create the Image in the debounced task)
		// Store displayData in a field so the scheduled task can use it
		this.pendingDisplayData = displayData; // add this field if not present: private ImageData pendingDisplayData;

		// Reset lastAppliedWidth so the next apply can grow if needed
		lastAppliedWidth = -1;

		// Schedule a debounced apply (coalesce multiple calls)
		scheduleApplyImageDebounced();

		if (DEBUG)
			System.out.println("[CD] onImageDataLoaded() EXIT");
	}

	private void applyImage(Image remoteImage) {
		// Ensure widget exists
		if (imageControl == null || imageControl.isDisposed()) {
			if (remoteImage != null && !remoteImage.isDisposed()) {
				try {
					remoteImage.dispose();
				} catch (Throwable ignored) {
				}
			}
			return;
		}

		Display display = imageControl.getDisplay();

		// Create a UI-owned copy of the incoming image (so caller may dispose theirs)
		Image uiFull = null;
		try {
			if (remoteImage == null || remoteImage.isDisposed()) {
				if (DEBUG)
					System.out.println("[CD] applyImage() incoming image null/disposed -> abort");
				return;
			}
			uiFull = new Image(display, remoteImage.getImageData());
		} catch (Throwable t) {
			MagicUIActivator.log("applyImage: failed to copy incoming image");
			MagicUIActivator.log(t);
			// fallback: try to use incoming image directly if it's still valid
			if (remoteImage != null && !remoteImage.isDisposed()) {
				uiFull = remoteImage;
			} else {
				uiFull = null;
			}
		} finally {
			// If we created a copy, dispose the original passed image to avoid leaks
			if (uiFull != null && uiFull != remoteImage) {
				try {
					if (remoteImage != null && !remoteImage.isDisposed())
						remoteImage.dispose();
				} catch (Throwable ignored) {
				}
			}
		}

		if (uiFull == null) {
			if (DEBUG)
				System.out.println("[CD] applyImage() no usable image after copy -> abort");
			return;
		}

		// Keep uiFull as the immediate originalImageFull for quick resizes
		try {
			if (this.originalImageFull != null && !this.originalImageFull.isDisposed()
					&& this.originalImageFull != uiFull && this.originalImageFull != loadingImage
					&& this.originalImageFull != cardNotFound) {
				try {
					this.originalImageFull.dispose();
				} catch (Throwable ignored) {
				}
			}
		} catch (Throwable ignored) {
		}
		this.originalImageFull = uiFull;

		// Determine the maximum width we may scale to (prefer disk/original if available)
		int maxOrigW = originalImageFull != null && !originalImageFull.isDisposed()
				? originalImageFull.getBounds().width
				: width;
		if (originalImageOnDisk != null && !originalImageOnDisk.isDisposed()) {
			try {
				int diskW = originalImageOnDisk.getBounds().width;
				if (diskW > maxOrigW)
					maxOrigW = diskW;
			} catch (Throwable ignored) {
			}
		}

		// Compute original height from originalImageFull (fallback to stored defaults)
		int origW = 1, origH = 1;
		try {
			Rectangle b = this.originalImageFull.getBounds();
			origW = Math.max(1, b.width);
			origH = Math.max(1, b.height);
		} catch (Throwable t) {
			MagicUIActivator.log("applyImage: failed to read original image bounds");
			origW = width;
			origH = hight;
		}

		if (DEBUG)
			System.out.println("[CD] applyImage() ENTER orig=" + origW + "x" + origH + " maxOrigW=" + maxOrigW);

		// Determine available width
		Composite parent = imageControl.getParent();
		int availW = -1;
		if (parent != null && !parent.isDisposed()) {
			availW = parent.getSize().x;
			availW = Math.max(0, availW - 8);
		}
		if (availW <= 0)
			availW = imageControl.getSize().x;
		if (availW <= 0)
			availW = maxOrigW;

		// Clamp target width between MIN_DISPLAY_WIDTH and the disk/original max
		int targetW = Math.max(MIN_DISPLAY_WIDTH, Math.min(availW, maxOrigW));

		// Threshold: avoid frequent small resizes
		if (lastAppliedWidth > 0 && Math.abs(targetW - lastAppliedWidth) < SCALE_THRESHOLD_PX) {
			if (DEBUG)
				System.out.println(
						"[CD] applyImage() skip scaling: targetW=" + targetW + " lastApplied=" + lastAppliedWidth);
			GridData ld = (GridData) imageControl.getLayoutData();
			if (ld != null) {
				ld.minimumWidth = Math.min(maxOrigW, Math.max(MIN_DISPLAY_WIDTH, lastAppliedWidth));
				ld.minimumHeight = Math.round(ld.minimumWidth * (origH / (float) origW));
				ld.widthHint = ld.minimumWidth;
				ld.heightHint = ld.minimumHeight;
				if (!this.isDisposed())
					this.layout(true, true);
			}
			return;
		}

		int targetH = Math.round(origH * (targetW / (float) origW));
		if (DEBUG)
			System.out.println("[CD] applyImage() availW=" + availW + " -> target=" + targetW + "x" + targetH);

		// Choose the best source to scale from: prefer originalImageOnDisk if it exists and is larger
		Image scaleSource = this.originalImageFull;
		try {
			if (originalImageOnDisk != null && !originalImageOnDisk.isDisposed()) {
				int diskW = originalImageOnDisk.getBounds().width;
				if (diskW >= targetW) {
					scaleSource = originalImageOnDisk;
				}
			}
		} catch (Throwable ignored) {
		}

		// Create scaled ImageData off-screen
		ImageData scaledData = null;
		try {
			scaledData = ImageCreator.scaleImageDataWithGC(display, scaleSource.getImageData(), targetW, targetH);
		} catch (Throwable t) {
			MagicUIActivator.log("applyImage: scaling failed, will use full image");
			MagicUIActivator.log(t);
			scaledData = null;
		}

		Image scaledImage = null;
		if (scaledData != null) {
			try {
				scaledImage = new Image(display, scaledData);
			} catch (Throwable t) {
				MagicUIActivator.log(t);
				scaledImage = null;
			}
		}
		if (scaledImage == null) {
			// fallback to the immediate original image if scaling failed
			scaledImage = this.originalImageFull;
		}

		// Swap images atomically to avoid flicker: prepare old reference, setRedraw(false), setImage, setRedraw(true)
		Image oldDisplayed = this.image;
		try {
			imageControl.setRedraw(false);
			imageControl.setImage(scaledImage);

			// Update layout hints based on the new displayed image
			GridData ld = (GridData) imageControl.getLayoutData();
			if (ld == null) {
				ld = new GridData(SWT.FILL, SWT.FILL, true, true);
				imageControl.setLayoutData(ld);
			}
			ld.minimumWidth = scaledImage.getBounds().width + 1;
			ld.minimumHeight = scaledImage.getBounds().height + 1;
			ld.widthHint = ld.minimumWidth;
			ld.heightHint = ld.minimumHeight;

			// update internal reference to the displayed image
			this.image = scaledImage;

			// layout once while redraw still disabled to avoid intermediate repaints
			if (!this.isDisposed())
				this.layout(true, true);
		} finally {
			try {
				imageControl.setRedraw(true);
			} catch (Throwable ignored) {
			}
		}

		// Dispose the previous displayed image after the swap (if it's not the same object and not a template)
		try {
			if (oldDisplayed != null && oldDisplayed != this.image && oldDisplayed != loadingImage
					&& oldDisplayed != cardNotFound) {
				try {
					oldDisplayed.dispose();
				} catch (Throwable ignored) {
				}
			}
		} catch (Throwable ignored) {
		}

		lastAppliedWidth = targetW;

		if (DEBUG) {
			try {
				System.out.println("[CD] applyImage() EXIT applied=" + this.image.getBounds().width + "x"
						+ this.image.getBounds().height + " lastAppliedWidth=" + lastAppliedWidth);
			} catch (Throwable ignored) {
				System.out.println("[CD] applyImage() EXIT applied=(disposed?) lastAppliedWidth=" + lastAppliedWidth);
			}
		}
	}

	public IMagicCard getCard() {
		return this.card;
	}

	public IPostSelectionProvider getSelectionProvider() {
		return selectionProvider;
	}
}