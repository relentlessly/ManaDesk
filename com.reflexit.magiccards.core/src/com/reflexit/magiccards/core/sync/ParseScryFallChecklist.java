package com.reflexit.magiccards.core.sync;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.reflexit.magiccards.core.MagicLogger;
import com.reflexit.magiccards.core.model.Edition;
import com.reflexit.magiccards.core.model.Editions;
import com.reflexit.magiccards.core.model.MagicCard;
import com.reflexit.magiccards.core.model.MagicCardField;
import com.reflexit.magiccards.core.monitor.ICoreProgressMonitor;
import com.reflexit.magiccards.core.sync.ParserHtmlHelper.ILoadCardHander;
import com.reflexit.magiccards.core.sync.ParserHtmlHelper.OutputHandler;

public class ParseScryFallChecklist extends AbstractParseJson {
	public static final String BASE_SEARCH_URL = "https://api.scryfall.com/cards/search?";
	public static final String TEXT_EXPORT_DIR = "/tmp/madatabase";
	public boolean includeImagesUrl = true;

	// Set to try when generating flat files. This is required to remove some fields
	public boolean generateFlat = false;

	@Override
	public String processFromReader(BufferedReader st, ILoadCardHander handler) throws IOException {
		try {
			JSONObject top = (JSONObject) new JSONParser().parse(st);
			int c = getInt(top, "total_cards");
			handler.setCardCount(c);
			Boolean has_more = (Boolean) top.get("has_more");
			JSONArray data = (JSONArray) top.get("data");
			for (Object elem : data) {
				parseRecord((JSONObject) elem, handler);
			}
			if (has_more) {
				return getString(top, "next_page");
			}
		} catch (ParseException e) {
			MagicLogger.log(e);
			throw new RuntimeException("No results");
		}
		handler.onEnd();
		return null;
	}

	// Convert from Scryfall to MA language string
	private String BuildLanguage(String Language) {
		if (Language == null) {
			return null;
		}

		String maLanguage = null;

		switch (Language) {
		case ("en"):
			maLanguage = "English";
			break;
		case ("es"):
			maLanguage = "Spanish";
			break;
		case ("fr"):
			maLanguage = "French";
			break;
		case ("de"):
			maLanguage = "German";
			break;
		case ("it"):
			maLanguage = "Italian";
			break;
		case ("pt"):
			maLanguage = "Portuguese";
			break;
		case ("ja"):
			maLanguage = "Japanese";
			break;
		case ("ko"):
			maLanguage = "Korean";
			break;
		case ("ru"):
			maLanguage = "Russian";
			break;
		case ("zhs"):
			maLanguage = "Chinese Simplified";
			break;
		case ("zht"):
			maLanguage = "Chinese Traditional";
			break;
		case ("he"):
			maLanguage = "Hebrew";
			break;
		case ("la"):
			maLanguage = "Latin";
			break;
		case ("grc"):
			maLanguage = "Ancient Greek";
			break;
		case ("ar"):
			maLanguage = "Arabic";
			break;
		case ("sa"):
			maLanguage = "Sanskrit";
			break;
		case ("ph"):
			maLanguage = "Phyrexian";
			break;
		case ("qya"):
			maLanguage = "Quenya";
			break;

		default:
			// Unknown return null
			break;
		}

		return maLanguage;
	}

	private String BuildText(String header, String text) {
		assert (header != null);
		assert (text != null);

		String line = "";
		if (text == null || text == "" || text == "false") {
			return line;
		}
		return header + text + "<br>";
	}

	private String BuildFinishes(JSONArray list) {
		if (list == null || list.size() == 0) {
			return "";
		}

		String finishes = "Finishes: ";

		for (int i = 0; i < list.size(); i++) {
			finishes += list.get(i).toString() + ":";
		}

		return finishes + "<br>";
	}

	private String BuildPromos(JSONArray list) {
		if (list == null || list.size() == 0) {
			return "";
		}

		String promos = "Promos: ";

		for (int i = 0; i < list.size(); i++) {
			promos += list.get(i).toString() + ":";
		}

		return promos + "<br>";
	}

	private String BuildLegalities(JSONObject object) {
		String legalitiesStr = "";

		if (object != null && object.size() > 0) {
			if (object.get("standard").toString().compareTo("legal") == 0) {
				legalitiesStr += "Standard;";
			} else {
				legalitiesStr += "Standard-;";
			}

			if (object.get("pioneer").toString().compareTo("legal") == 0) {
				legalitiesStr += "Pioneer;";
			} else {
				legalitiesStr += "Pioneer-;";
			}
			if (object.get("modern").toString().compareTo("legal") == 0) {
				legalitiesStr += "Modern;";
			} else {
				legalitiesStr += "Modern-;";
			}
			if (object.get("commander").toString().compareTo("legal") == 0) {
				legalitiesStr += "Commander;";
			} else {
				legalitiesStr += "Commander-;";
			}
			if (object.get("vintage").toString().compareTo("legal") == 0) {
				legalitiesStr += "Vintage;";
			} else {
				legalitiesStr += "Vintage-;";
			}
			if (object.get("legacy").toString().compareTo("legal") == 0) {
				legalitiesStr += "Legacy";
			} else {
				legalitiesStr += "Legacy-";
			}
		}

		return legalitiesStr;
	}

	private void parseRecord(JSONObject elem, ILoadCardHander handler) {
		if (elem == null)
			return;

		Object games = elem.get("games");

		// Skip non paper cards
		// We don't want to manage Virtual cards
		if (games == null || !(games.toString().contains("paper"))) {
			return;
		}

		// Skip some languages for now
		// We could improve this later
		String lang = (String) elem.get("lang");

		// We have a card we support, let check the info
		MagicCard frontCard = new MagicCard();
		MagicCard backCard = new MagicCard();

		int cardLayout = 0; // Single face card by default

		Object layout = elem.get("layout");
		JSONArray card_faces = (JSONArray) elem.get("card_faces");

		JSONObject frontFace = null;
		JSONObject backFace = null;

		if (card_faces != null && card_faces.size() > 0) {
			frontFace = (JSONObject) card_faces.get(0);
			backFace = (JSONObject) card_faces.get(1);
		}

		// Determine the number of "faces/sides/zones", depending of the card type
		switch (layout.toString()) {
		case ("split"):
		case ("flip"):
		case ("adventure"):
			// Two cards, same face
			cardLayout = 1;
			break;

		case ("transform"):
		case ("modal_dfc"):
		case ("battle"):
		case ("double_faced_token"):
		case ("reversible_card"):
			// Two cards, 2 faces
			cardLayout = 2;
			break;

		case ("scheme"):
		case ("token"):
		case ("emblem"):
		case ("art_series"):
			// Depends
			if (card_faces != null && card_faces.size() > 1) {
				// 2 cards
				cardLayout = 2;
			} else {
				// Single card
				cardLayout = 0;
			}

			break;

		case ("normal"):
		case ("meld"): // Process only the face for now
		case ("leveler"):
		case ("class"):
		case ("case"):
		case ("saga"):
		case ("mutate"):
		case ("prototype"):
		case ("planar"):
		case ("vanguard"):
		case ("augment"):
		case ("host"):
			// Single face
			// Exception: Meld, we process only the "main Face" for now
			cardLayout = 0;
			break;

		default:
			// Unknown layout, assume single side card by default
			cardLayout = 0;
			break;
		}

		// Read multiverse info, for the ID
		JSONArray gids = (JSONArray) elem.get("multiverse_ids");
		String frontMultiverseString = "";
		String backMultiverseString = "";
		String cardText = "";

		if (gids != null && gids.size() == 2) {
			frontCard.setCardId(gids.get(0).toString());
			backCard.setCardId("-" + frontCard.getCardId());

			frontMultiverseString = "MID: " + gids.get(0).toString() + " // " + gids.get(1).toString() + "<br>";
			backMultiverseString = "MID: " + gids.get(1).toString() + " // " + gids.get(0).toString() + "<br>";
		} else if (gids != null && gids.size() == 1) {
			frontCard.setCardId(gids.get(0).toString());
			backCard.setCardId("-" + frontCard.getCardId());

			frontMultiverseString = "MID: " + gids.get(0).toString() + "<br>";
			backMultiverseString = "MID: " + gids.get(0).toString() + "<br>";
		} else {
			frontCard.setCardId("scry_" + elem.get("id").toString());
			frontMultiverseString = "";
			backCard.setCardId("-" + "scry_" + elem.get("id").toString());
			backMultiverseString = "";
		}

		String languageString = BuildLanguage(elem.get("lang").toString());
		String finishesString = BuildFinishes((JSONArray) elem.get("finishes"));
		String legalitiesString = BuildLegalities((JSONObject) elem.get("legalities"));
		;
		String scryfallUriString = (String) elem.get("scryfall_uri");
		String fullArtString = BuildText("FullArt: ", elem.get("full_art").toString());
		String textlessString = BuildText("TextLess: ", elem.get("textless").toString());
		String storySpotlightString = BuildText("StorySpotlight: ", elem.get("story_spotlight").toString());
		String boosterString = BuildText("Booster: ", elem.get("textless").toString());
		String promoTypesString = BuildPromos((JSONArray) elem.get("promo_types"));

		JSONObject image_uris = (JSONObject) elem.get("image_uris");

		switch (cardLayout) {
		// Standard single face card
		case 0:

			frontCard.setName(elem.get("name").toString());
			frontCard.setLanguage(languageString);
			frontCard.setSet(elem.get("set_name").toString());
			frontCard.setCost(elem.get("mana_cost").toString().replace("/P", "P"));
			frontCard.setType(elem.get("type_line").toString().replace("—", "-"));
			frontCard.setRarity(elem.get("rarity").toString());
			frontCard.setPower(elem.get("power") != null ? elem.get("power").toString() : "");
			frontCard.setToughness(elem.get("toughness") != null ? elem.get("toughness").toString() : "");
			frontCard.setOracleText(elem.get("oracle_text").toString().replace("|", "&vert;"));
			frontCard.setArtist(elem.get("artist").toString());
			frontCard.setCollNumber(elem.get("collector_number").toString());

			// Check if an image exist
			if (elem.get("image_status") != "missing") {
				JSONObject images = null;

				// Get the image
				if (image_uris.size() > 0) {
					images = image_uris;
				} else if (card_faces.size() > 0 && frontFace != null) {
					images = (JSONObject) frontFace.get("image_uris");
				}

				if (images != null) {
					// We select the "normal" format, this is the best fit
					Object image = images.get("normal").toString();

					if (image == null || image == "") {
						image = images.get(0);
					}
					if (image != null && !(image.toString().contains("errors.scryfall"))) {
						frontCard.set(MagicCardField.IMAGE_URL, image);
					}
				}
			}

			// Useful information for collectors
			// We will use the Text field to store and display that information
			cardText = frontMultiverseString + finishesString + fullArtString + textlessString + boosterString
					+ storySpotlightString + promoTypesString + scryfallUriString;

			frontCard.setText(cardText);
			frontCard.setLanguage(languageString);

			// Add legality if we're doing a live update
			if (!generateFlat) {
				frontCard.set(MagicCardField.LEGALITY, legalitiesString);
			}
			handler.handleCard(frontCard);
			break;

		// One face, 2 cards
		case 1:
			// 2 faces
		case 2:

			frontCard.setName(elem.get("name").toString());
			backCard.setName(elem.get("name").toString() + " (" + layout.toString() + ")");

			frontCard.setLanguage(languageString);
			backCard.setLanguage(languageString);

			frontCard.setSet(elem.get("set_name").toString());
			backCard.setSet(frontCard.getSet());

			frontCard.setCost(frontFace.get("mana_cost").toString().replace("/P", "P"));
			backCard.setCost(backFace.get("mana_cost").toString().replace("/P", "P"));

			frontCard.setType(frontFace.get("type_line").toString().replace("—", "-"));
			backCard.setType(
					backFace.get("type_line") != null ? backFace.get("type_line").toString().replace("—", "-") : "");

			frontCard.setRarity(elem.get("rarity").toString());
			backCard.setRarity(frontCard.getRarity());

			frontCard.setPower(frontFace.get("power") != null ? frontFace.get("power").toString() : "");
			backCard.setPower(backFace.get("power") != null ? backFace.get("power").toString() : "");

			frontCard.setToughness(frontFace.get("toughness") != null ? frontFace.get("toughness").toString() : "");
			backCard.setToughness(backFace.get("toughness") != null ? backFace.get("toughness").toString() : "");

			frontCard.setOracleText(frontFace.get("oracle_text") != null
					? frontFace.get("oracle_text").toString().replace("|", "&vert;")
					: "");
			backCard.setOracleText(
					backFace.get("oracle_text") != null ? backFace.get("oracle_text").toString().replace("|", "&vert;")
							: "");

			frontCard.setArtist(frontFace.get("artist") != null ? frontFace.get("artist").toString() : "");
			backCard.setArtist(backFace.get("artist") != null ? backFace.get("artist").toString() : "");

			frontCard.setCollNumber(elem.get("collector_number").toString());
			backCard.setCollNumber(frontCard.getCollNumber() + "b");

			// Check if an image exist
			if (elem.get("image_status") != "missing") {
				JSONObject images = null;

				if (cardLayout == 2) {
					// Get the image
					if (frontFace != null) {
						images = (JSONObject) frontFace.get("image_uris");
					}

					if (images != null) {
						Object image = images.get("normal").toString();

						if (image == null || image == "") {
							image = images.get(0);
						}
						if (image != null && !(image.toString().contains("errors.scryfall"))) {
							frontCard.set(MagicCardField.IMAGE_URL, image);
						}
					}

					if (backFace != null) {
						images = (JSONObject) backFace.get("image_uris");
					}

					if (images != null) {
						Object image = images.get("normal").toString();

						if (image == null || image == "") {
							image = images.get(0);
						}
						if (image != null && !(image.toString().contains("errors.scryfall"))) {
							backCard.set(MagicCardField.IMAGE_URL, image);
						}
					}

				} else {
					// Get the image
					if (image_uris.size() > 0) {
						images = image_uris;
					} else if (card_faces.size() > 0 && frontFace != null) {
						images = (JSONObject) frontFace.get("image_uris");
					}

					if (images != null) {
						Object image = images.get("normal").toString();

						if (image == null || image == "") {
							image = images.get(0);
						}
						if (image != null && !(image.toString().contains("errors.scryfall"))) {
							frontCard.set(MagicCardField.IMAGE_URL, image);
							backCard.set(MagicCardField.IMAGE_URL, image);
						}
					}
				}
			}

			// Useful for collectors
			cardText = finishesString + fullArtString + textlessString + boosterString + storySpotlightString
					+ promoTypesString + scryfallUriString;

			frontCard.setText(frontMultiverseString + cardText);
			backCard.setText(backMultiverseString + cardText);

			if (!generateFlat) {
				frontCard.set(MagicCardField.LEGALITY, legalitiesString);
				backCard.set(MagicCardField.LEGALITY, frontCard.get(MagicCardField.LEGALITY));
			}

			frontCard.set(MagicCardField.FLIPID, backCard.getCardId());
			backCard.set(MagicCardField.FLIPID, frontCard.getCardId());

			handler.handleCard(frontCard);
			handler.handleCard(backCard);
			break;
		}
	}

	@Override
	public URL getSearchQuery(String set) throws MalformedURLException {
		String url;
		if (set != null && set.startsWith("http")) {
			url = set;
		} else {
			// We always want all the prints, extras, all variations
			String out = "&unique=prints&include_extras=true&include_variations=true";
			String abbr = Editions.getInstance().getAbbrByName(set);
			if (abbr == null)
				abbr = set;

			// We don't restrict by lang on the query, accept everyting Scryfall provides by
			// default
			// Search for a specific Set (abbr)
			// Example:
			// https://api.scryfall.com/cards/search?q=e%3ATSPM&unique=prints&include_extras=true&include_variations=true
			String base = BASE_SEARCH_URL + "q=e%3A" + abbr + out;
			url = base;
		}
		return new URL(url);
	}

	public static class SortedOutputHanlder extends OutputHandler {
		private ArrayList<MagicCard> primary = new ArrayList<>();

		public SortedOutputHanlder(PrintStream st, boolean loadLandPrintings, boolean loadOtherPrintings) {
			super(st, loadLandPrintings, loadOtherPrintings);
		}

		@Override
		public void handleCard(MagicCard card) {
			primary.add(card);
			count++;
		}

		@Override
		public void onEnd() {
			printHeader();
			Collections.sort(primary, (o1, o2) -> o1.getCollectorNumberId() - o2.getCollectorNumberId());
			for (MagicCard magicCard : primary) {
				printCard(magicCard);
			}
		}

		public List<MagicCard> getPrimary() {
			return primary;
		}
	}

	// Used to create the flat resource files
	public void saveAllFlat(File dir) throws IOException {

		generateFlat = true;

		this.includeImagesUrl = false;

		Editions editions = Editions.getInstance(true);

		ParseScryFallSets setsLoader = new ParseScryFallSets();

		setsLoader.loadSets(true);

		Collection<Edition> sets = setsLoader.getAll();

		dir.mkdirs();

		try {

			File file = new File(dir, "/editions.txt");

			editions.save(file);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		int size = editions.getEditions().size();
		int i = 0;

		for (Edition x : editions.getEditions()) {
			i++;

			int prog = Math.round(i / (float) size * 100);
			System.out.println("Set \"" + x.getName() + "\" written in " + dir.getAbsolutePath() + "\\"
					+ x.getMainAbbreviation() + ".txt (" + prog + "%)");
			saveEditionText(dir, x);
		}
	}

	// Use to create a set flat file
	public void saveEditionText(File dir, Edition x) {
		String base = x.getBaseFileName() + ".txt";
		for (String abbr : x.getAbbreviations()) {
			File file = new File(dir, base);
			try (PrintStream out = new PrintStream(file)) {
				SortedOutputHanlder handler = new SortedOutputHanlder(out, true, true);
				this.loadSet(abbr, handler, ICoreProgressMonitor.NONE);
				if (handler.getRealCount() > 0)
					break;
			} catch (Exception e) {
				System.err.println(e);
			}
			file.delete();
		}
	}

	public void downloadAndSaveEdition(File dir, String set) {
		System.out.println("Downloading " + set + " from Scryfall");

		try (PrintStream out = new PrintStream(dir)) {
			SortedOutputHanlder handler = new SortedOutputHanlder(out, true, true);
			this.loadSet(set, handler, ICoreProgressMonitor.NONE);
		} catch (Exception e) {
			System.err.println(e);
		}
	}

	public static void main(String[] args) throws MalformedURLException, IOException {
		// Important! Run this to create the flat files required to update the Db
		// resource
		// Files will be located under TEXT_EXPORT_DIR
		new ParseScryFallChecklist().saveAllFlat(new File(TEXT_EXPORT_DIR));
	}
}
