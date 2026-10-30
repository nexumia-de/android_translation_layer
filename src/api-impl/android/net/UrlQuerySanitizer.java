package android.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class UrlQuerySanitizer {

	public interface ValueSanitizer {
		public String sanitize(String value);
	}

	public class ParameterValuePair {
		public String mParameter;
		public String mValue;

		public ParameterValuePair(String parameter, String value) {
			mParameter = parameter;
			mValue = value;
		}
	}

	private final HashMap<String, ValueSanitizer> sanitizers = new HashMap<>();
	private final HashMap<String, String> entries = new HashMap<>();
	private final ArrayList<ParameterValuePair> entriesList = new ArrayList<>();
	private boolean allowUnregisteredParamaters;
	private boolean preferFirstRepeatedParameter;
	private ValueSanitizer unregisteredParameterValueSanitizer = new ValueSanitizer() {
		@Override
		public String sanitize(String value) {
			return value;
		}
	};

	public UrlQuerySanitizer() {}

	public UrlQuerySanitizer(String url) {
		setAllowUnregisteredParamaters(true);
		parseUrl(url);
	}

	public void parseUrl(String url) {
		int queryIndex = url.indexOf('?');
		parseQuery(queryIndex >= 0 ? url.substring(queryIndex + 1) : "");
	}

	public void parseQuery(String query) {
		clear();
		for (String attribute : query.split("&")) {
			if (attribute.isEmpty())
				continue;
			int equals = attribute.indexOf('=');
			if (equals < 0)
				parseEntry(attribute, "");
			else
				parseEntry(attribute.substring(0, equals), attribute.substring(equals + 1));
		}
	}

	protected void parseEntry(String parameter, String value) {
		String unescapedParameter = unescape(parameter);
		ValueSanitizer sanitizer = getEffectiveValueSanitizer(unescapedParameter);
		if (sanitizer == null)
			return;
		addSanitizedEntry(unescapedParameter, sanitizer.sanitize(unescape(value)));
	}

	protected void addSanitizedEntry(String parameter, String value) {
		entriesList.add(new ParameterValuePair(parameter, value));
		if (preferFirstRepeatedParameter && entries.containsKey(parameter))
			return;
		entries.put(parameter, value);
	}

	protected void clear() {
		entries.clear();
		entriesList.clear();
	}

	public Set<String> getParameterSet() {
		return entries.keySet();
	}

	public List<ParameterValuePair> getParameterList() {
		return entriesList;
	}

	public boolean hasParameter(String parameter) {
		return entries.containsKey(parameter);
	}

	public String getValue(String parameter) {
		return entries.get(parameter);
	}

	public void registerParameter(String parameter, ValueSanitizer valueSanitizer) {
		if (valueSanitizer == null)
			sanitizers.remove(parameter);
		else
			sanitizers.put(parameter, valueSanitizer);
	}

	public void registerParameters(String[] parameters, ValueSanitizer valueSanitizer) {
		for (String parameter : parameters)
			sanitizers.put(parameter, valueSanitizer);
	}

	public ValueSanitizer getValueSanitizer(String parameter) {
		return sanitizers.get(parameter);
	}

	public ValueSanitizer getEffectiveValueSanitizer(String parameter) {
		ValueSanitizer sanitizer = getValueSanitizer(parameter);
		if (sanitizer == null && allowUnregisteredParamaters)
			sanitizer = unregisteredParameterValueSanitizer;
		return sanitizer;
	}

	public void setAllowUnregisteredParamaters(boolean allowUnregisteredParamaters) {
		this.allowUnregisteredParamaters = allowUnregisteredParamaters;
	}

	public boolean getAllowUnregisteredParamaters() {
		return allowUnregisteredParamaters;
	}

	public void setPreferFirstRepeatedParameter(boolean preferFirstRepeatedParameter) {
		this.preferFirstRepeatedParameter = preferFirstRepeatedParameter;
	}

	public boolean getPreferFirstRepeatedParameter() {
		return preferFirstRepeatedParameter;
	}

	public void setUnregisteredParameterValueSanitizer(ValueSanitizer sanitizer) {
		unregisteredParameterValueSanitizer = sanitizer;
	}

	public ValueSanitizer getUnregisteredParameterValueSanitizer() {
		return unregisteredParameterValueSanitizer;
	}

	public String unescape(String string) {
		return Uri.decode(string.replace('+', ' '));
	}
}
