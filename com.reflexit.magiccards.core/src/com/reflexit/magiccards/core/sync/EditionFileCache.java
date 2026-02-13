
/*
 * Contributors:
 *     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
 */

package com.reflexit.magiccards.core.sync;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import javax.imageio.ImageIO;

import com.reflexit.magiccards.core.FileUtils;
import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.OfflineException;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Rarity;

public class EditionFileCache {
	private Edition edition;
	private HashMap<String, CachedFile> map = new HashMap<>(3);

	public EditionFileCache(Edition ed) {
		this.edition = ed;
	}

	public CachedFile getImageCachedFile(String rarity, boolean forceRemote) throws IOException {
		if ("Land".equals(rarity))
			rarity = "Common";
		CachedFile cachedFile = map.get(rarity);
		if (cachedFile != null) {
			if (forceRemote == false || cachedFile.getRemoteURL() != null)
				return cachedFile;
		}
		String path = createLocalSetImageFilePath(rarity);
		if (forceRemote == false) {
			cachedFile = new CachedFile(null, new File(path));
			if (cachedFile.isImage()) {
				map.put(rarity, cachedFile);
				return cachedFile;
			}
		}
		String tryRarity = rarity;
		while (tryRarity != null) {
			String[] abbreviations = edition.getAbbreviations();
			for (int i = 0; i < abbreviations.length; i++) {
				String editionAbbr = abbreviations[i];
				URL url = createSetImageRemoteURL(editionAbbr, tryRarity);

				// !!! RD  Temporary !!! 
				url = new URL("https://svgs.scryfall.io/sets/woe.svg?1770008400");

				if (url.getPath().toLowerCase().endsWith(".svg")) {

					try {
						// Rasterize directly from URL
						BufferedImage bi = SvgRasterizer.renderSvg(url, 16, 16);

						// Apply rarity tint
						Color tint = RarityColor.valueOf(rarity.toUpperCase()).color;
						bi = SvgRasterizer.tint(bi, tint);

						// Save PNG
						File pngFile = new File(path);
						ImageIO.write(bi, "PNG", pngFile);

						cachedFile = new CachedFile(pngFile.toURI().toURL(), pngFile);
						map.put(rarity, cachedFile);
						return cachedFile;

					} catch (Exception e) {
						MagicLogger.log("Failed to rasterize SVG: " + url + ": " + e.getMessage());
						return null;
					}
				}

				cachedFile.recache();
				map.put(rarity, cachedFile);
				return cachedFile;
			}
			tryRarity = Rarity.getMoreRare(tryRarity);
		}
		return null;
	}

	public enum RarityColor {
		COMMON(new Color(0x000000)), // black
		UNCOMMON(new Color(0xC0C0C0)), // silver
		RARE(new Color(0xD4AF37)), // gold
		MYTHIC(new Color(0xD12A1A)), // red-leaning mythic
		SPECIAL(new Color(0x1E7A1E)); // green

		public final Color color;

		RarityColor(Color c) {
			this.color = c;
		}
	}

	public URL getLocalURL(String rarity, boolean upload) throws IOException {
		if (upload == false)
			return createSetImageLocalURL(rarity);
		CachedFile onefile = getImageCachedFile(rarity, false);
		if (onefile != null)
			return onefile.getLocalURL(false);
		return createSetImageLocalURL(rarity);
	}

	public URL getRemoteURL(String rarity, boolean force) throws IOException {
		try {
			CachedFile onefile = getImageCachedFile(rarity, false);
			URL url = null;
			if (onefile != null)
				url = onefile.getRemoteURL();
			if (url == null) {
				onefile = getImageCachedFile(rarity, true);
				if (onefile != null)
					url = onefile.getRemoteURL();
			}
			if (url != null)
				return url;
		} catch (OfflineException e) {
			// ignore return pre-defined unchecked url
		}
		return createSetImageRemoteURL(edition.getMainAbbreviation(), rarity);
	}

	public URL createSetImageLocalURL(String rarity) {
		String path = createLocalSetImageFilePath(rarity);
		URL localUrl = null;
		try {
			localUrl = new File(path).toURI().toURL();
		} catch (MalformedURLException e) {
			// should not happen
		}
		return localUrl;
	}

	public static URL createSetImageRemoteURL(String editionAbbr, String rarity) {
		return GatherHelper.createSetImageURL(editionAbbr, rarity);
	}

	private String createLocalSetImageFilePath(String rarity) {
		if ("Land".equals(rarity))
			rarity = "Common";
		File loc = FileUtils.getStateLocationFile();

		// RD Modification to use the common "icon" set for special sets like Arts, Tokens, Promos, etc
		String name = edition.getIconBaseFileName() + "-" + rarity;
		String part = "Sets/" + name + ".png";
		String file = new File(loc, part).getPath();
		return file;
	}
}
