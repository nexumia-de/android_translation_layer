package android.net;

public class ParseException extends RuntimeException {
	public String response;

	ParseException(String response) {
		super(response);
		this.response = response;
	}
}
