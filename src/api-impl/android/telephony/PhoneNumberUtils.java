package android.telephony;

public class PhoneNumberUtils {

	public static boolean isNonSeparator(char c) {
		return c != ' ' && c != '\t' && c != '\n' && c != '\r' && c != '-';
	}

	public static boolean isGlobalPhoneNumber(String phoneNumber) {
		return phoneNumber != null && (phoneNumber.startsWith("+") || phoneNumber.startsWith("00"));
	}

	/* returned as given: no per-country formatting */
	public static String formatNumber(String phoneNumber, String defaultCountryIso) {
		return phoneNumber;
	}

	public static String stripSeparators(String phoneNumber) {
		return phoneNumber.replaceAll("[ \t\n\r-]", "");
	}
}
