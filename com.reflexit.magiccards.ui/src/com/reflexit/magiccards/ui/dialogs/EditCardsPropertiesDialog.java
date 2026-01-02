package com.reflexit.magiccards.ui.dialogs;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.PreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.model.SpecialTags;
import com.reflexit.magiccards.core.sync.CardCache;
import com.reflexit.magiccards.ui.MagicUIActivator;
import com.reflexit.magiccards.ui.utils.ImageCreator;
import com.reflexit.magiccards.ui.widgets.ContextAssist;

public class EditCardsPropertiesDialog extends MagicDialog {
	private static final String VIRTUAL_VALUE = "Virtual";
	private static final String OWN_VALUE = "Own";
	public static final String COMMENT_FIELD = MagicCardField.COMMENT.name();
	public static final String SPECIAL_FIELD = MagicCardField.SPECIAL.name();
	public static final String OWNERSHIP_FIELD = MagicCardField.OWNERSHIP.name();
	public static final String COUNT_FIELD = MagicCardField.COUNT.name();
	public static final String NAME_FIELD = MagicCardField.NAME.name();
	public static final String PRICE_FIELD = MagicCardField.PRICE.name();
	public static final String UNCHANGED = "<unchanged>";
	protected Composite area;

	public EditCardsPropertiesDialog(Shell parentShell, PreferenceStore store) {
		super(parentShell, store);
	}

	@Override
	protected void createBodyArea(Composite parent) {
		getShell().setText("Edit Card Properties");
		setTitle("Edit Magic Card Instance '" + store.getString(NAME_FIELD) + "'");
		Composite back = new Composite(parent, SWT.NONE);
		back.setLayout(new GridLayout(2, false));
		back.setLayoutData(new GridData(GridData.FILL_BOTH));
		createImageControl(back);
		area = new Composite(back, SWT.NONE);
		area.setLayout(new GridLayout(2, false));
		GridData gda = new GridData(GridData.FILL_BOTH);
		gda.widthHint = convertWidthInCharsToPixels(80);
		area.setLayoutData(gda);
		// Header
		createTextLabel(area, "Name");
		createTextLabel(area, store.getString(NAME_FIELD));
		// Count
		Text count = createTextFieldEditor(area, "Count", COUNT_FIELD);
		// Price
		createTextFieldEditor(area, "User Price", PRICE_FIELD);
		// ownership
		createOwnershipFieldEditor(area);
		// comment
		createTextFieldEditor(area, "Comment", COMMENT_FIELD, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		// special
		Text special = createTextFieldEditor(area, "Special Tags", SPECIAL_FIELD, SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		special.setToolTipText(
				"Set card tags, such as foil, mint, premium, forTrade, etc. Tags are separated by ','.\n To add tag use +, to remove tag use -. For example \"+foil,-online\".");
		ContextAssist.addContextAssist(special, SpecialTags.getTags(), true);
		// end
		count.setFocus();
	}

	private void createImageControl(Composite parent) {

		final Label imageControl = new Label(parent, SWT.NONE);

		GridData gda1 = new GridData(GridData.FILL_VERTICAL);
		gda1.widthHint = ImageCreator.CARD_WIDTH;
		gda1.heightHint = ImageCreator.CARD_HEIGHT;
		imageControl.setLayoutData(gda1);

		final String localPath = CardCache.createLocalImageFilePath(store.getString(MagicCardField.ID.name()),
				store.getString(MagicCardField.EDITION_ABBR.name()));

		if (!new File(localPath).exists()) {
			return;
		}

		new Job("Load local card image") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {

				ImageData data = null;

				try {
					data = ImageCreator.createCardImageData(localPath, true);
				} catch (Exception ex) {
					StringWriter sw = new StringWriter();
					ex.printStackTrace(new PrintWriter(sw));
					MagicUIActivator
							.log("Error creating ImageData for local image: " + localPath + "\n" + sw.toString());
					data = null;
				}

				final ImageData finalData = data;

				Display.getDefault().asyncExec(() -> {

					if (imageControl.isDisposed())
						return;

					Image newImg = null;

					try {
						if (finalData != null) {
							newImg = new Image(Display.getDefault(), finalData);
						}
					} catch (SWTException ex) {
						MagicUIActivator.log(
								"Failed to create Image from ImageData for: " + localPath + " - " + ex.getMessage());
						newImg = null;
					}

					if (newImg == null) {
						try {
							newImg = ImageCreator.getInstance().createCardNotFoundImage(null);
						} catch (Throwable t) {
							MagicUIActivator
									.log("Failed to create not-found image for: " + localPath + " - " + t.getMessage());
							return;
						}
					}

					// disposer l’ancienne image correctement
					Image old = (Image) imageControl.getData("cardImage");
					if (old != null && !old.isDisposed()) {
						imageControl.setImage(null); // ← OBLIGATOIRE
						old.dispose();
					}

					// appliquer la nouvelle image
					imageControl.setImage(newImg);
					imageControl.setData("cardImage", newImg);

					if (imageControl.getData("disposeListenerAdded") == null) {
						imageControl.addDisposeListener(ev -> {
							Image i = (Image) imageControl.getData("cardImage");
							if (i != null && !i.isDisposed())
								i.dispose();
						});
						imageControl.setData("disposeListenerAdded", Boolean.TRUE);
					}

					if (!parent.isDisposed()) {
						parent.layout(true);
					}
				});

				return Status.OK_STATUS;
			}
		}.schedule();
	}

	public void createOwnershipFieldEditor(Composite area) {
		createTextLabel(area, "Ownership");
		final Combo ownership = new Combo(area, SWT.READ_ONLY);
		ownership.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		String ovalue = store.getDefaultString(OWNERSHIP_FIELD);
		String defaultString = ovalue;
		if (!UNCHANGED.equals(ovalue))
			defaultString = Boolean.valueOf(ovalue) ? OWN_VALUE : VIRTUAL_VALUE;
		setComboChoices(ownership, new String[] { OWN_VALUE, VIRTUAL_VALUE, UNCHANGED }, defaultString);
		ownership.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean own = ownership.getText().equals(OWN_VALUE);
				store.setValue(OWNERSHIP_FIELD, String.valueOf(own));
			}
		});
	}
}
