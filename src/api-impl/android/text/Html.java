package android.text;

import android.graphics.drawable.Drawable;
import java.util.Arrays;
import org.xml.sax.XMLReader;

public class Html {

	public static interface ImageGetter {
		public Drawable getDrawable(String source);
	}

	public static Spanned fromHtml(String source) {
		return Html.fromHtml(source, 0);
	}

	public static Spanned fromHtml(String source, int flags) {
		return fromHtml(source, flags, null, null);
	}

	public static Spanned fromHtml(String source, ImageGetter imageGetter, TagHandler tagHandler) {
		return fromHtml(source, 0, imageGetter, tagHandler);
	}

	/*
	 * Minimal HTML parser: keeps the text, removes tags, turns <br> and block
	 * elements into line breaks, collapses whitespace and decodes entities.
	 * Unknown tags are passed to the TagHandler (without an XMLReader).
	 * Styling spans (<b>, <a>, ...) and images are not implemented yet.
	 */
	public static Spanned fromHtml(String source, int flags, ImageGetter imageGetter, TagHandler tagHandler) {
		SpannableStringBuilder out = new SpannableStringBuilder();
		int length = source.length();
		int i = 0;
		while (i < length) {
			char c = source.charAt(i);
			if (source.startsWith("<!--", i)) {
				int end = source.indexOf("-->", i + 4);
				i = end < 0 ? length : end + 3;
			} else if (c == '<' && i + 1 < length && isTagStart(source.charAt(i + 1)) && source.indexOf('>', i) >= 0) {
				int end = source.indexOf('>', i);
				handleTag(source.substring(i + 1, end), out, tagHandler);
				i = end + 1;
			} else if (c == '&') {
				int end = source.indexOf(';', i);
				String entity = end < 0 || end - i > 10 ? null : decodeEntity(source.substring(i + 1, end));
				if (entity != null) {
					out.append(entity);
					i = end + 1;
				} else {
					out.append(c);
					i++;
				}
			} else if (Character.isWhitespace(c)) {
				// like Android, a run of whitespace becomes one space, and none after a line break
				int len = out.length();
				char pred = len == 0 ? '\n' : out.charAt(len - 1);
				if (pred != ' ' && pred != '\n')
					out.append(' ');
				i++;
			} else {
				out.append(c);
				i++;
			}
		}
		return out;
	}

	private static boolean isTagStart(char c) {
		return Character.isLetter(c) || c == '/' || c == '!' || c == '?';
	}

	private static final String[] BLOCK_TAGS = {"p", "div", "ul", "ol", "li", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6"};
	private static final String[] INLINE_TAGS = {"html", "head", "body", "span", "b", "strong", "i", "em", "cite", "dfn", "u", "a", "font", "big", "small", "tt", "sub", "sup", "s", "strike", "del", "img"};

	private static void handleTag(String tag, SpannableStringBuilder out, TagHandler tagHandler) {
		if (tag.startsWith("!") || tag.startsWith("?"))
			return; // doctype or processing instruction
		boolean closing = tag.startsWith("/");
		boolean selfClosing = tag.endsWith("/");
		int start = closing ? 1 : 0;
		int end = start;
		while (end < tag.length() && !Character.isWhitespace(tag.charAt(end)) && tag.charAt(end) != '/')
			end++;
		// the TagHandler gets the name as written, like on Android
		String name = tag.substring(start, end);
		String lowerName = name.toLowerCase();
		if (lowerName.equals("br")) {
			out.append('\n');
		} else if (Arrays.asList(BLOCK_TAGS).contains(lowerName)) {
			if (out.length() > 0 && out.charAt(out.length() - 1) != '\n')
				out.append('\n');
		} else if (!Arrays.asList(INLINE_TAGS).contains(lowerName) && tagHandler != null) {
			tagHandler.handleTag(!closing, name, out, null);
			if (selfClosing && !closing)
				tagHandler.handleTag(false, name, out, null);
		}
	}

	private static String decodeEntity(String entity) {
		try {
			if (entity.startsWith("#x") || entity.startsWith("#X"))
				return new String(Character.toChars(Integer.parseInt(entity.substring(2), 16)));
			if (entity.startsWith("#"))
				return new String(Character.toChars(Integer.parseInt(entity.substring(1))));
		} catch (IllegalArgumentException e) {
			return null;
		}
		switch (entity) {
			case "lt":
				return "<";
			case "gt":
				return ">";
			case "amp":
				return "&";
			case "quot":
				return "\"";
			case "apos":
				return "'";
			case "nbsp":
				return "\u00a0";
			default:
				return null;
		}
	}

	public static String toHtml(Spanned text) {
		return text.toString();
	}

	public static String escapeHtml(CharSequence source) {
		StringBuilder out = new StringBuilder(source.length());
		for (int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '<' || c == '>' || c == '&' || c == '"' || c == '\'' || c > 0x7F) {
				out.append("&#");
				out.append((int)c);
				out.append(';');
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}

	public static interface TagHandler {
		/**
		 * This method will be called whenn the HTML parser encounters
		 * a tag that it does not know how to interpret.
		 */
		public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader);
	}
}
