#include <gio/gunixinputstream.h>
#include <gtk/gtk.h>
#include <webkit/webkit.h>

#include <androidfw/androidfw_c_api.h>

#include "../defines.h"
#include "../util.h"

#include "../AssetInputStream.h"
#include "WrapperWidget.h"

#include "../generated_headers/android_view_View.h"
#include "../generated_headers/android_webkit_WebView.h"

static void asset_uri_scheme_request_cb(WebKitURISchemeRequest *request, gpointer user_data)
{
	const gchar *path = webkit_uri_scheme_request_get_path(request);
	if (path == NULL)
		return;
	path += 1; // remove the leading '/'
	JNIEnv *env = get_jni_env();
	WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(gtk_widget_get_parent(GTK_WIDGET(webkit_uri_scheme_request_get_web_view(request)))));
	jobject asset_manager_obj = J__WebView__internalGetAssetManager(env, wrapper->jobj);
	struct AssetManager *asset_manager = _PTR(_GET_LONG_FIELD(asset_manager_obj, "mObject"));
	struct Asset *asset = AssetManager_openNonAsset(asset_manager, path, ACCESS_STREAMING);
	GInputStream *stream = asset_input_stream_new(asset);
	webkit_uri_scheme_request_finish(request, stream, Asset_getLength(asset), NULL);
	g_object_unref(stream);
}

static void bridge_uri_scheme_request_cb(WebKitURISchemeRequest *request, gpointer user_data)
{
	JNIEnv *env = get_jni_env();
	WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(gtk_widget_get_parent(GTK_WIDGET(webkit_uri_scheme_request_get_web_view(request)))));
	jstring result = J__WebView__internalBridgeCall(env, wrapper->jobj, _JSTRING(webkit_uri_scheme_request_get_uri(request)));
	const char *result_chars = (*env)->GetStringUTFChars(env, result, NULL);
	gsize length = strlen(result_chars);
	GInputStream *stream = g_memory_input_stream_new_from_data(g_strndup(result_chars, length), length, g_free);
	(*env)->ReleaseStringUTFChars(env, result, result_chars);
	WebKitURISchemeResponse *response = webkit_uri_scheme_response_new(stream, length);
	webkit_uri_scheme_response_set_content_type(response, "application/json");
	SoupMessageHeaders *headers = soup_message_headers_new(SOUP_MESSAGE_HEADERS_RESPONSE);
	soup_message_headers_append(headers, "Access-Control-Allow-Origin", "*");
	webkit_uri_scheme_response_set_http_headers(response, headers);
	webkit_uri_scheme_request_finish_with_response(request, response);
	g_object_unref(response);
	g_object_unref(stream);
}

static void evaluate_cb(GObject *source, GAsyncResult *result, gpointer user_data)
{
	JNIEnv *env = get_jni_env();
	jobject callback = user_data;
	JSCValue *value = webkit_web_view_evaluate_javascript_finish(WEBKIT_WEB_VIEW(source), result, NULL);
	if (callback) {
		char *json = value ? jsc_value_to_json(value, 0) : NULL;
		WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(gtk_widget_get_parent(GTK_WIDGET(source))));
		J__WebView__internalEvaluateDone(env, wrapper->jobj, callback, json ? _JSTRING(json) : NULL);
		g_free(json);
		(*env)->DeleteGlobalRef(env, callback);
	}
	if (value)
		g_object_unref(value);
}

#define CONTENT_HEIGHT_SCRIPT "Math.ceil(document.documentElement.getBoundingClientRect().height)"

static void content_height_cb(GObject *source, GAsyncResult *result, gpointer user_data)
{
	WrapperWidget *wrapper = user_data;
	JSCValue *value = webkit_web_view_evaluate_javascript_finish(WEBKIT_WEB_VIEW(source), result, NULL);
	if (value) {
		J__WebView__internalContentHeightChanged(get_jni_env(), wrapper->jobj, jsc_value_to_int32(value));
		g_object_unref(value);
	}
	g_object_unref(wrapper);
}

static void update_content_height(WebKitWebView *web_view, WrapperWidget *wrapper)
{
	webkit_web_view_evaluate_javascript(web_view, CONTENT_HEIGHT_SCRIPT, -1, NULL, NULL, NULL, content_height_cb, g_object_ref(wrapper));
}

static void web_view_load_changed(WebKitWebView *web_view, WebKitLoadEvent load_event, WrapperWidget *wrapper)
{
	JNIEnv *env = get_jni_env();
	J__WebView__internalLoadChanged(env, wrapper->jobj, load_event, _JSTRING(webkit_web_view_get_uri(web_view)));
	if (load_event == WEBKIT_LOAD_FINISHED)
		update_content_height(web_view, wrapper);
}

static gboolean decide_policy(WebKitWebView *web_view, WebKitPolicyDecision *decision, WebKitPolicyDecisionType type, WrapperWidget *wrapper)
{
	if (type == WEBKIT_POLICY_DECISION_TYPE_NAVIGATION_ACTION) {
		WebKitNavigationPolicyDecision *nav = WEBKIT_NAVIGATION_POLICY_DECISION(decision);
		WebKitNavigationAction *action = webkit_navigation_policy_decision_get_navigation_action(nav);
		WebKitURIRequest *request = webkit_navigation_action_get_request(action);
		const gchar *uri = webkit_uri_request_get_uri(request);

		JNIEnv *env = get_jni_env();
		jboolean result = J__WebView__internalShouldOverrideUrlLoading(env, wrapper->jobj, _JSTRING(uri));
		if (result)
			webkit_policy_decision_ignore(decision);
		else
			webkit_policy_decision_use(decision);
		return result;
	}
	return FALSE;
}

/* many apps use webview just for fingerprinting or displaying ads, which seems like a waste of
 * resources even if we deal with fingerprinting and ads in some other way in the future. So the
 * widget of a WebView is an empty box, and the web view goes into it when the first page is loaded.
 */
static WebKitWebView *get_web_view(GtkWidget *box, gboolean javascript_enabled)
{
	if (getenv("ATL_UGLY_DISABLE_WEBVIEW"))
		return NULL;

	GtkWidget *webview = gtk_widget_get_first_child(box);
	if (!webview) {
		WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(box));
		webview = webkit_web_view_new();
		gtk_widget_set_hexpand(webview, TRUE);
		gtk_widget_set_vexpand(webview, TRUE);
		gtk_box_append(GTK_BOX(box), webview);
		static gboolean uri_scheme_registered = FALSE;
		if (!uri_scheme_registered) {
			webkit_web_context_register_uri_scheme(webkit_web_view_get_context(WEBKIT_WEB_VIEW(webview)), "android-asset", asset_uri_scheme_request_cb, NULL, NULL);
			webkit_web_context_register_uri_scheme(webkit_web_view_get_context(WEBKIT_WEB_VIEW(webview)), "atl-bridge", bridge_uri_scheme_request_cb, NULL, NULL);
			WebKitSecurityManager *security_manager = webkit_web_context_get_security_manager(webkit_web_view_get_context(WEBKIT_WEB_VIEW(webview)));
			webkit_security_manager_register_uri_scheme_as_cors_enabled(security_manager, "atl-bridge");
			webkit_security_manager_register_uri_scheme_as_secure(security_manager, "atl-bridge");
			uri_scheme_registered = TRUE;
		}
		g_signal_connect(webview, "load-changed", G_CALLBACK(web_view_load_changed), wrapper);
		g_signal_connect(webview, "decide-policy", G_CALLBACK(decide_policy), wrapper);
	}
	/* scripts of the page; the ones we evaluate ourselves still run */
	webkit_settings_set_enable_javascript_markup(webkit_web_view_get_settings(WEBKIT_WEB_VIEW(webview)), javascript_enabled);
	return WEBKIT_WEB_VIEW(webview);
}

JNIEXPORT jlong JNICALL Java_android_webkit_WebView_native_1constructor(JNIEnv *env, jobject this, jobject context, jobject attrs)
{
	GtkWidget *wrapper = g_object_ref(wrapper_widget_new());
	GtkWidget *box = gtk_box_new(GTK_ORIENTATION_VERTICAL, 0);
	wrapper_widget_set_child(WRAPPER_WIDGET(wrapper), box);
	wrapper_widget_set_jobject(WRAPPER_WIDGET(wrapper), env, this);
	wrapper_widget_register_invalidation_listener(WRAPPER_WIDGET(wrapper));
	return _INTPTR(box);
}

JNIEXPORT void JNICALL Java_android_webkit_WebView_native_1loadUrl(JNIEnv *env, jobject this, jlong widget_ptr, jstring url, jboolean javascript_enabled)
{
	WebKitWebView *webview = get_web_view(_PTR(widget_ptr), javascript_enabled);
	if (!webview)
		return;

	webkit_web_view_load_uri(webview, _CSTRING(url));
}

JNIEXPORT void JNICALL Java_android_webkit_WebView_native_1loadDataWithBaseURL(JNIEnv *env, jobject this, jlong widget_ptr, jstring base_url, jstring data_jstr, jstring mime_type, jstring encoding, jboolean javascript_enabled)
{
	WebKitWebView *webview = get_web_view(_PTR(widget_ptr), javascript_enabled);
	if (!webview)
		return;

	jsize data_len = (*env)->GetStringUTFLength(env, data_jstr);
	jsize data_jlen = (*env)->GetStringLength(env, data_jstr);
	char *data = malloc(data_len + 1); // + 1 for NUL
	(*env)->GetStringUTFRegion(env, data_jstr, 0, data_jlen, data);
	webkit_web_view_load_bytes(webview, g_bytes_new(data, data_len), mime_type ? _CSTRING(mime_type) : "text/html", encoding ? _CSTRING(encoding) : NULL, base_url ? _CSTRING(base_url) : NULL);
}

JNIEXPORT void JNICALL Java_android_webkit_WebView_native_1setUserScript(JNIEnv *env, jobject this, jlong widget_ptr, jstring script, jboolean javascript_enabled)
{
	WebKitWebView *webview = get_web_view(_PTR(widget_ptr), javascript_enabled);
	if (!webview)
		return;

	WebKitUserContentManager *manager = webkit_web_view_get_user_content_manager(webview);
	WebKitUserScript *user_script = webkit_user_script_new(_CSTRING(script), WEBKIT_USER_CONTENT_INJECT_ALL_FRAMES, WEBKIT_USER_SCRIPT_INJECT_AT_DOCUMENT_START, NULL, NULL);
	webkit_user_content_manager_remove_all_scripts(manager);
	webkit_user_content_manager_add_script(manager, user_script);
	webkit_user_script_unref(user_script);
}

JNIEXPORT void JNICALL Java_android_webkit_WebView_native_1evaluateJavascript(JNIEnv *env, jobject this, jlong widget_ptr, jstring script, jobject callback, jboolean javascript_enabled)
{
	WebKitWebView *webview = get_web_view(_PTR(widget_ptr), javascript_enabled);
	if (!webview)
		return;

	webkit_web_view_evaluate_javascript(webview, _CSTRING(script), -1, NULL, NULL, NULL, evaluate_cb, callback ? (*env)->NewGlobalRef(env, callback) : NULL);
}

JNIEXPORT void JNICALL Java_android_webkit_WebView_native_1updateContentHeight(JNIEnv *env, jobject this, jlong widget_ptr)
{
	GtkWidget *box = _PTR(widget_ptr);
	GtkWidget *webview = gtk_widget_get_first_child(box);
	if (webview && !webkit_web_view_is_loading(WEBKIT_WEB_VIEW(webview)))
		update_content_height(WEBKIT_WEB_VIEW(webview), WRAPPER_WIDGET(gtk_widget_get_parent(box)));
}

/* the WebKitWebView only exists after the first load */
static WebKitWebView *existing_web_view(jlong widget_ptr)
{
	GtkWidget *webview = gtk_widget_get_first_child(GTK_WIDGET(_PTR(widget_ptr)));
	return webview ? WEBKIT_WEB_VIEW(webview) : NULL;
}

JNIEXPORT jboolean JNICALL Java_android_webkit_WebView_native_1canGoBackOrForward(JNIEnv *env, jobject this, jlong widget_ptr, jint steps)
{
	WebKitWebView *webview = existing_web_view(widget_ptr);
	if (!webview)
		return false;
	WebKitBackForwardList *list = webkit_web_view_get_back_forward_list(webview);
	return webkit_back_forward_list_get_nth_item(list, steps) != NULL;
}

JNIEXPORT void JNICALL Java_android_webkit_WebView_native_1goBackOrForward(JNIEnv *env, jobject this, jlong widget_ptr, jint steps)
{
	WebKitWebView *webview = existing_web_view(widget_ptr);
	if (!webview)
		return;
	WebKitBackForwardList *list = webkit_web_view_get_back_forward_list(webview);
	WebKitBackForwardListItem *item = webkit_back_forward_list_get_nth_item(list, steps);
	if (item)
		webkit_web_view_go_to_back_forward_list_item(webview, item);
}

JNIEXPORT void JNICALL Java_android_webkit_WebView_native_1reload(JNIEnv *env, jobject this, jlong widget_ptr)
{
	WebKitWebView *webview = existing_web_view(widget_ptr);
	if (webview)
		webkit_web_view_reload(webview);
}

JNIEXPORT jstring JNICALL Java_android_webkit_WebView_native_1getUrl(JNIEnv *env, jobject this, jlong widget_ptr)
{
	WebKitWebView *webview = existing_web_view(widget_ptr);
	const char *uri = webview ? webkit_web_view_get_uri(webview) : NULL;
	return uri ? _JSTRING(uri) : NULL;
}
