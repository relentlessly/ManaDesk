package com.reflexit.magiccards.ui.utils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;

import com.reflexit.magiccards.core.MagicException;
import com.reflexit.magiccards.ui.MagicUIActivator;

public class SymbolConverter {
	public static final int SYMBOL_SIZE = 16;
	private static String bundleBase;

	private static final Map<String, List<String>> manaMapTxt = new HashMap<>();
	private static final Map<String, List<String>> manaMapCc = new HashMap<>();

	private static final String[] COLORS = { "W", "U", "B", "R", "G", "C" };

	static {
		autoGenerateManaMaps();
		addManualSymbols();
	}

	private static void debugAdd(String section, String key, String fileName) {
		// System.out.println("[MAP][" + section + "] key=" + key + " -> " + fileName);
	}

	private static void autoGenerateManaMaps() {

		// Single colors: keep C here
		for (String c : COLORS) {
			String key = "{" + c + "}";
			String fileName = normalizeManaKey(key);
			debugAdd("single", key, fileName);
			add(key, fileName);
		}

		// Hybrids: EXCLUDE C (only W,U,B,R,G)
		for (int i = 0; i < 5; i++) { // 0..4 = W,U,B,R,G
			for (int j = i + 1; j < 5; j++) {

				String a = COLORS[i];
				String b = COLORS[j];

				String key1 = "{" + a + "/" + b + "}";
				String key2 = "{" + b + "/" + a + "}";

				// Generate BOTH permutations
				String stem1 = a + b; // e.g. WU
				String stem2 = b + a; // e.g. UW

				debugAdd("hybrid", key1, stem1 + "," + stem2);
				debugAdd("hybrid", key2, stem1 + "," + stem2);

				// Store both stems for both keys
				add(key1, stem1, stem2);
				add(key2, stem1, stem2);
			}
		}

		// Numeric mana 0–20
		for (int i = 0; i <= 20; i++) {
			String key = "{" + i + "}";
			String fileName = normalizeManaKey(key);
			debugAdd("number", key, fileName);
			add(key, fileName);
		}

		// 2/X hybrids: EXCLUDE C (only W,U,B,R,G)
		for (int i = 0; i < 5; i++) {
			String c = COLORS[i];
			String key = "{2/" + c + "}";
			String fileName = normalizeManaKey(key);
			debugAdd("2X", key, fileName);
			add(key, fileName);
		}

		// Phyrexian: EXCLUDE C (only W,U,B,R,G)
		for (int i = 0; i < 5; i++) {
			String c = COLORS[i];
			String key = "{" + c + "/P}";
			String fileName = normalizeManaKey(key);
			debugAdd("phyrexian", key, fileName);
			add(key, fileName);
		}

		// 3-way phyrexian hybrids: {A/B/P}  (P always last in input)
		for (int i = 0; i < 5; i++) {
			for (int j = i + 1; j < 5; j++) {

				String a = COLORS[i];
				String b = COLORS[j];

				// Input keys (P always last)
				String key1 = "{" + a + "/" + b + "/P}";
				String key2 = "{" + b + "/" + a + "/P}";

				// Filenames: PAB and PBA (both needed, sources are inconsistent)
				String stem1 = "P" + a + b;
				String stem2 = "P" + b + a;

				debugAdd("phyrexian3", key1, stem1 + "," + stem2);
				debugAdd("phyrexian3", key2, stem1 + "," + stem2);

				add(key1, stem1, stem2);
				add(key2, stem1, stem2);
			}
		}
	}

	private static void add(String key, String... stems) {
		merge(manaMapTxt, key, stems);
		merge(manaMapCc, key, stems);
	}

	private static void merge(Map<String, List<String>> map, String key, String... stems) {
		List<String> list = map.get(key);
		if (list == null) {
			list = new ArrayList<>();
			map.put(key, list);
		}
		for (String s : stems) {
			if (!list.contains(s)) {
				list.add(s);
			}
		}
	}

	private static void add(String key) {
		String stem = normalizeManaKey(key);
		manaMapTxt.put(key, Arrays.asList(stem));
		manaMapCc.put(key, Arrays.asList(stem));
	}

	private static void addManualSymbols() {
		add("{100}", "100.gif");
		add("{1000000}", "1000000");
		add("{A}", "acorn");
		add("{C}", "C");
		add("{CHAOS}", "H.gif");
		add("{C/P}", "PH");
		add("{D}", "D");
		add("{E}", "E");
		add("{H}", "PH");
		add("{HR}", "demiR.gif");
		add("{HW}", "V");
		add("{L}", "L");
		add("{P}", "paw");
		add("{Q}", "Q");
		add("{S}", "S");
		add("{T}", "T");
		add("{TK}", "ticket");
		add("{X}", "X");
		add("{Y}", "Y");
		add("{Z}", "Z");
		add("{½}", "demi");
		add("{∞}", "I");
	}

	private static void addHybridSymbols() {
		String[] colors = { "W", "U", "B", "R", "G" };

		for (String a : colors) {
			for (String b : colors) {
				if (!a.equals(b)) {
					String key = "{" + a + "/" + b + "}";
					add(key, a + b, b + a); // both permutations
				}
			}
		}
	}

	static {
		// init
		try {
			URL url = MagicUIActivator.getDefault().getBundle().getEntry("/");
			bundleBase = FileLocator.resolve(url).toString();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Converts an Oracle-style mana symbol key (e.g. "{G/P}", "{W/U}", "{2/W}", "{R}", "{10}")
	 * into the corresponding Scryfall-style filename stem (e.g. "PG", "WU", "2W", "R", "10").
	 *
	 * Input is expected to be a full symbol including braces, like "{G/P}".
	 */
	/**
	 * Converts an Oracle-style mana symbol (e.g. "{G/P}", "{W/U}", "{2/W}", "{R}", "{10}")
	 * into the corresponding filename stem (e.g. "PG", "WU", "2W", "R", "10").
	 */
	private static String normalizeManaKey(String sym) {
		if (sym == null || sym.length() < 3 || sym.charAt(0) != '{' || sym.charAt(sym.length() - 1) != '}')
			return sym;

		String s = sym.substring(1, sym.length() - 1); // e.g. "G/P", "2/W", "R", "10"

		// Phyrexian: X/P -> PX (e.g. "{G/P}" -> "PG")
		if (s.endsWith("/P") && s.length() == 3) {
			return "P" + s.charAt(0);
		}

		// Monocolor hybrid: 2/X -> 2X (e.g. "{2/W}" -> "2W")
		if (s.startsWith("2/") && s.length() == 3) {
			return "2" + s.charAt(2);
		}

		// Regular hybrid: A/B where both A and B are colors
		if (s.contains("/")) {
			String[] parts = s.split("/");
			if (parts.length == 2 && parts[0].length() == 1 && parts[1].length() == 1
					&& Arrays.asList(COLORS).contains(parts[0]) && Arrays.asList(COLORS).contains(parts[1])) {

				char a = parts[0].charAt(0);
				char b = parts[1].charAt(0);

				// Canonicalize order so {G/U} and {U/G} both map to "GU"
				if (b < a) {
					char tmp = a;
					a = b;
					b = tmp;
				}

				return "" + a + b;
			}

			// Fallback for any other slash-based symbol
			return s.replace("/", "");
		}

		// Everything else: W, U, B, R, G, C, S, X, numbers, etc.
		return s;
	}

	private static String getHtmlStyle(Control con) {
		FontData fontData = con.getFont().getFontData()[0];
		int height = fontData.getHeight();
		String fontName = fontData.getName();
		RGB rgb = con.getBackground().getRGB();
		String bgColor = "rgb(" + rgb.red + "," + rgb.green + "," + rgb.blue + ")";
		rgb = con.getForeground().getRGB();
		String fgColor = "rgb(" + rgb.red + "," + rgb.green + "," + rgb.blue + ")";
		String style = "font-size:" + height + "pt;" + "background-color: " + bgColor + ";" + "color: " + fgColor + ";"
				+ "font-family:" + fontName + ";";
		return style;
	}

	public static Image buildCostImage(String cost) {
		if (cost == null || cost.length() == 0)
			return null;
		ImageRegistry imageRegistry = MagicUIActivator.getDefault().getImageRegistry();
		String key = "[" + cost;
		Image costImage = imageRegistry.get(key);
		if (costImage != null)
			return costImage;
		try {
			Collection<Image> manaImages = getManaImages(cost);

			int totalWidth = 0;
			for (Image img : manaImages) {
				totalWidth += img.getBounds().width;
			}

			costImage = ImageCreator.joinImages(manaImages, totalWidth, SYMBOL_SIZE);
			return costImage;
		} catch (Exception e) {
			MagicUIActivator.log(e);
			costImage = ImageCreator.createTransparentImage(SYMBOL_SIZE * cost.length() / 2, SYMBOL_SIZE);
			GC gc = new GC(costImage);
			gc.drawText(cost, 0, 0, true);
			gc.dispose();
			return costImage;
		} finally {
			if (costImage != null) {
				imageRegistry.remove(key);
				imageRegistry.put(key, costImage);
			}
		}
	}

	private static Image scaleToSymbolHeight(Image img) {
		if (img == null)
			return null;

		int targetH = SYMBOL_SIZE; // 16
		int w = img.getBounds().width;
		int h = img.getBounds().height;

		if (h == targetH) {
			return img; // already correct height
		}

		// Keep original width, only squash/stretch vertically
		int newW = w;

		Image scaled = new Image(img.getDevice(), newW, targetH);
		GC gc = new GC(scaled);

		gc.setAntialias(SWT.ON);
		gc.setInterpolation(SWT.HIGH);

		gc.drawImage(img, 0, 0, w, h, // source
				0, 0, newW, targetH // destination (same width, new height)
		);

		gc.dispose();
		return scaled;
	}

	private static Collection<Image> getManaImages(String cost) {
		if (cost.length() == 0)
			return Collections.emptyList();

		Collection<Image> res = new ArrayList<>();
		String text = cost.trim();

		if (text.equals("*"))
			return res;

		// Sort keys by descending length (critical for hybrid symbols)
		List<String> keys = new ArrayList<>(manaMapCc.keySet());
		keys.sort((a, b) -> Integer.compare(b.length(), a.length()));

		while (text.length() > 0) {
			boolean cut = false;

			for (String sym : keys) {

				if (text.startsWith(sym)) {

					String imagePath = resolveIconPath(sym);
					if (imagePath == null) {
						throw new MagicException("Cannot find image for " + sym);
					}

					Image img = ImageCreator.getManaSymbolImage(sym, imagePath);
					if (img != null) {
						img = scaleToSymbolHeight(img);
						res.add(img);
					}

					text = text.substring(sym.length()).trim();
					cut = true;
					break; // restart outer loop
				}
			}

			if (!cut) {
				throw new MagicException("Cannot build mana images for '" + text + "'");
			}
		}

		return res;
	}

	public static String wrapHtml(String text, Control con) {

		if (bundleBase != null && text != null && text.contains("{") && text.contains("}")) {

			StringBuilder out = new StringBuilder();
			int i = 0;

			while (i < text.length()) {

				int tagStart = text.indexOf('<', i);

				if (tagStart == -1) {
					// No more tags → replace in the rest
					out.append(replaceSymbols(text.substring(i)));
					break;
				}

				// Replace in the text before the tag
				if (tagStart > i) {
					out.append(replaceSymbols(text.substring(i, tagStart)));
				}

				// Copy the tag verbatim
				int tagEnd = text.indexOf('>', tagStart);
				if (tagEnd == -1) {
					// malformed HTML, just append the rest
					out.append(text.substring(tagStart));
					break;
				}

				out.append(text, tagStart, tagEnd + 1);
				i = tagEnd + 1;
			}

			text = out.toString();
		}

		String style = getHtmlStyle(con);

		return "<html><head><base href=\"" + (bundleBase == null ? "." : bundleBase)
				+ "\"/></head><body style='overflow:auto;" + style + "'>" + text + "</body></html>";
	}

	private static String replaceSymbols(String segment) {
		String work = segment;

		List<String> keys = new ArrayList<>(manaMapTxt.keySet());
		keys.sort((a, b) -> Integer.compare(b.length(), a.length()));

		for (String sym : keys) {
			if (work.contains(sym)) {
				work = insertCostImages(work, sym, manaMapTxt.get(sym));
			}
		}

		return work;
	}

	private static String insertCostImages(String text, String symbol, List<String> stems) {

		for (String stem : stems) {
			String pathTxt = "icons/mana_txt/" + stem.toString().replace(".gif", "") + ".png";
			String pathCc = "icons/mana_cc/" + stem.replace(".gif", "") + ".png";

			if (fileExists(pathTxt)) {
				return text.replace(symbol,
						"<img src=\"" + pathTxt + "\" style=\"height:16px; width:auto;vertical-align:-4px;\"/>");
			}

			if (fileExists(pathCc)) {
				return text.replace(symbol,
						"<img src=\"" + pathCc + "\" style=\"height:16px; width:auto;vertical-align:-4px;\"/>");
			}
		}

		return text;
	}

	private static String normalizeSymbol(String sym) {
		String s = sym.substring(1, sym.length() - 1);
		return s.replaceAll("[^A-Za-z0-9]", "");
	}

	private static boolean fileExists(String path) {
		try {
			URL url = new URL(bundleBase + path);
			url.openStream().close();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static String resolveIconPath(String key) {

		List<String> stemsTxt = manaMapTxt.get(key);
		if (stemsTxt != null) {
			for (String stem : stemsTxt) {
				String path = "icons/mana_txt/" + stem.replace(".gif", "") + ".png";
				if (fileExists(path))
					return path;
			}
		}

		List<String> stemsCc = manaMapCc.get(key);
		if (stemsCc != null) {
			for (String stem : stemsCc) {
				String path = "icons/mana_cc/" + stem.replace(".gif", "") + ".png";
				if (fileExists(path))
					return path;
			}
		}

		return null;
	}

	private static boolean downloadSymbol(List<String> stems, String type, File outDir) {
		for (String stem : stems) {
			if (tryDownload(stem, stem, type, outDir)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isColor(char c) {
		return c == 'W' || c == 'U' || c == 'B' || c == 'R' || c == 'G' || c == 'C';
	}

	private static boolean tryDownload(String remoteName, String localName, String type, File outDir) {
		try {
			boolean isGif = remoteName.toLowerCase().endsWith(".gif");

			// Strip extension from remoteName
			String baseRemote = remoteName.replaceAll("\\.(png|gif)$", "");

			// Strip extension from localName
			String baseLocal = localName.replaceAll("\\.(png|gif)$", "");

			// Build correct URL
			String url = "https://www.mtgpics.com/graph/manas_" + type + "/" + baseRemote + (isGif ? ".gif" : ".png");

			System.out.println("Try url " + url + " for : " + type + " / " + baseLocal);

			URL u = new URL(url);
			File out = new File(outDir, baseLocal + ".png");

			try (InputStream in = u.openStream()) {
				BufferedImage img = ImageIO.read(in);
				if (img == null) {
					System.err.println("Invalid image data for: " + type + " / " + baseLocal);
					return false;
				}

				ImageIO.write(img, "png", out);

				System.out.println("Downloaded: " + type + " / " + baseLocal);
				System.out.println("Saving icons to: " + outDir.getAbsolutePath());
				return true;
			}

		} catch (Exception e) {
			System.err.println("Missing: " + type + " / " + localName + " (remote tried: " + remoteName + ")");
			return false;
		}
	}

	public static void main(String[] args) throws Exception {

		File outCc = new File("/tmp/symbols/cc");
		File outTxt = new File("/tmp/symbols/txt");
		outCc.mkdirs();
		outTxt.mkdirs();

		Set<String> confirmedMissing = new HashSet<>(Arrays.asList("17", "18", "19"));

		List<String> missing = new ArrayList<>();

		for (List<String> stems : manaMapTxt.values()) {

			// Skip if the first stem is in confirmedMissing
			if (confirmedMissing.contains(stems.get(0))) {
				continue;
			}

			boolean txtOk = downloadSymbol(stems, "txt", outTxt);
			boolean ccOk = txtOk || downloadSymbol(stems, "cc", outCc);

			if (!ccOk) {
				missing.add(stems.toString());
			}
		}

		System.out.println("\n=== Download Report ===");
		System.out.println("Missing icons (excluding confirmed missing): " + missing);
	}
}
