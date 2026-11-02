package android.webkit;

public class JsPromptResult extends JsResult {
	private String stringResult;

	public JsPromptResult(ResultReceiver receiver) {
		super(receiver);
	}

	public void confirm(String result) {
		stringResult = result;
		confirm();
	}

	public String getStringResult() {
		return stringResult;
	}
}
