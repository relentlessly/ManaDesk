/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *******************************************************************************/

package com.reflexit.magiccards.core.sync;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGUniverse;

public class SvgRasterizer {

	// --- Public API: render from URL ---
	public static BufferedImage renderSvg(URL url, int width, int height) throws IOException {
		try (InputStream in = url.openStream()) {
			String svg = new String(in.readAllBytes(), StandardCharsets.UTF_8);

			// Normalize fill colors to white for tinting
			svg = svg.replace("fill=\"#000\"", "fill=\"#FFFFFF\"");
			svg = svg.replace("fill=\"#000000\"", "fill=\"#FFFFFF\"");
			svg = svg.replace("fill=\"black\"", "fill=\"#FFFFFF\"");
			svg = svg.replace("currentColor", "#FFFFFF");

			SVGUniverse universe = new SVGUniverse();
			URI uri = universe.loadSVG(new StringReader(svg), "patched");
			SVGDiagram diagram = universe.getDiagram(uri);

			return renderDiagram(diagram, width, height);
		}
	}

	// --- Public API: render from File (kept for compatibility) ---
	public static BufferedImage renderSvg(File svgFile, int width, int height) throws IOException {
		SVGUniverse universe = new SVGUniverse();
		SVGDiagram diagram = universe.getDiagram(universe.loadSVG(svgFile.toURI().toURL()));

		if (diagram == null) {
			throw new IOException("Failed to load SVG: " + svgFile);
		}

		return renderDiagram(diagram, width, height);
	}

	// --- Shared rendering logic ---
	private static BufferedImage renderDiagram(SVGDiagram diagram, int width, int height) throws IOException {
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		// Get diagram size
		float diagWidth = diagram.getWidth();
		float diagHeight = diagram.getHeight();

		// Fallback to viewBox if width/height are missing
		if (diagWidth == 0 || diagHeight == 0) {
			Rectangle2D vb = diagram.getViewRect();
			diagWidth = (float) vb.getWidth();
			diagHeight = (float) vb.getHeight();
		}

		if (diagWidth == 0 || diagHeight == 0) {
			throw new IOException("SVG has invalid size (no width/height/viewBox)");
		}

		// Compute scale
		float scaleX = width / diagWidth;
		float scaleY = height / diagHeight;
		float scale = Math.min(scaleX, scaleY);

		// Center the icon
		float tx = (width - diagWidth * scale) / 2f;
		float ty = (height - diagHeight * scale) / 2f;

		g.translate(tx, ty);
		g.scale(scale, scale);

		try {
			diagram.render(g);
		} catch (Exception e) {
			throw new IOException("Failed to render SVG", e);
		} finally {
			g.dispose();
		}

		return bi;
	}

	// ...

	public static BufferedImage tint(BufferedImage src, Color tint) {
		int w = src.getWidth();
		int h = src.getHeight();

		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = src.getRGB(x, y);

				int alpha = (argb >> 24) & 0xFF;
				if (alpha == 0) {
					// keep fully transparent pixels as-is
					out.setRGB(x, y, argb);
					continue;
				}

				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;

				int gray = (r + g + b) / 3;

				int tr = (tint.getRed() * gray) / 255;
				int tg = (tint.getGreen() * gray) / 255;
				int tb = (tint.getBlue() * gray) / 255;

				int tinted = (alpha << 24) | (tr << 16) | (tg << 8) | tb;
				out.setRGB(x, y, tinted);
			}
		}

		return out;
	}
}