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

char *get_app_data_dir();

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

static void intercept_uri_scheme_request_cb(WebKitURISchemeRequest *request, gpointer user_data)
{
	JNIEnv *env = get_jni_env();
	WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(gtk_widget_get_parent(GTK_WIDGET(webkit_uri_scheme_request_get_web_view(request)))));
	SoupMessageHeaders *headers = webkit_uri_scheme_request_get_http_headers(request);
	GPtrArray *pairs = g_ptr_array_new();
	if (headers) {
		SoupMessageHeadersIter iter;
		const char *name, *value;
		soup_message_headers_iter_init(&iter, headers);
		while (soup_message_headers_iter_next(&iter, &name, &value)) {
			g_ptr_array_add(pairs, (gpointer)name);
			g_ptr_array_add(pairs, (gpointer)value);
		}
	}
	jobjectArray header_array = (*env)->NewObjectArray(env, pairs->len, (*env)->FindClass(env, "java/lang/String"), NULL);
	for (guint i = 0; i < pairs->len; i++) {
		jstring str = _JSTRING(g_ptr_array_index(pairs, i));
		(*env)->SetObjectArrayElement(env, header_array, i, str);
		(*env)->DeleteLocalRef(env, str);
	}
	g_ptr_array_free(pairs, TRUE);
	/* the reference is given up in native_finishInterceptedRequest */
	jstring uri = _JSTRING(webkit_uri_scheme_request_get_uri(request));
	jstring method = _JSTRING(webkit_uri_scheme_request_get_http_method(request));
	J__WebView__internalInterceptRequest(env, wrapper->jobj, _INTPTR(g_object_ref(request)), uri, method, header_array);
	(*env)->DeleteLocalRef(env, uri);
	(*env)->DeleteLocalRef(env, method);
	(*env)->DeleteLocalRef(env, header_array);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		GError *error = g_error_new_literal(G_IO_ERROR, G_IO_ERROR_FAILED, "shouldInterceptRequest failed");
		webkit_uri_scheme_request_finish_error(request, error);
		g_error_free(error);
		g_object_unref(request);
	}
}

static char *dup_jstring(JNIEnv *env, jstring str)
{
	if (!str)
		return NULL;
	const char *chars = (*env)->GetStringUTFChars(env, str, NULL);
	char *copy = g_strdup(chars);
	(*env)->ReleaseStringUTFChars(env, str, chars);
	return copy;
}

JNIEXPORT void JNICALL Java_android_webkit_WebView_native_1finishInterceptedRequest(JNIEnv *env, jclass class, jlong request_ptr, jint status, jstring reason, jstring mime_type, jobjectArray headers, jbyteArray body)
{
	WebKitURISchemeRequest *request = _PTR(request_ptr);
	jsize length = (*env)->GetArrayLength(env, body);
	gpointer data = g_malloc(length);
	(*env)->GetByteArrayRegion(env, body, 0, length, data);
	GInputStream *stream = g_memory_input_stream_new_from_data(data, length, g_free);
	WebKitURISchemeResponse *response = webkit_uri_scheme_response_new(stream, length);
	char *reason_str = dup_jstring(env, reason);
	char *mime_type_str = dup_jstring(env, mime_type);
	webkit_uri_scheme_response_set_status(response, status, reason_str);
	if (mime_type_str)
		webkit_uri_scheme_response_set_content_type(response, mime_type_str);
	g_free(reason_str);
	g_free(mime_type_str);
	SoupMessageHeaders *response_headers = soup_message_headers_new(SOUP_MESSAGE_HEADERS_RESPONSE);
	for (jsize i = 0; i + 1 < (*env)->GetArrayLength(env, headers); i += 2) {
		jstring name = (*env)->GetObjectArrayElement(env, headers, i);
		jstring value = (*env)->GetObjectArrayElement(env, headers, i + 1);
		char *name_str = dup_jstring(env, name);
		char *value_str = dup_jstring(env, value);
		if (name_str && value_str)
			soup_message_headers_append(response_headers, name_str, value_str);
		g_free(name_str);
		g_free(value_str);
		(*env)->DeleteLocalRef(env, name);
		(*env)->DeleteLocalRef(env, value);
	}
	webkit_uri_scheme_response_set_http_headers(response, response_headers);
	webkit_uri_scheme_request_finish_with_response(request, response);
	g_object_unref(response);
	g_object_unref(stream);
	g_object_unref(request);
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
		jboolean result = J__WebView__internalShouldOverrideUrlLoading(env, wrapper->jobj, _JSTRING(uri), JNI_TRUE);
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
		/* the app's own website data (localStorage, IndexedDB, cookies), like app_webview on Android */
		static WebKitNetworkSession *session = NULL;
		if (!session) {
			char *data_dir = g_build_filename(get_app_data_dir(), "app_webview", NULL);
			char *cache_dir = g_build_filename(get_app_data_dir(), "cache", "webview", NULL);
			session = webkit_network_session_new(data_dir, cache_dir);
			g_free(data_dir);
			g_free(cache_dir);
		}
		webview = g_object_new(WEBKIT_TYPE_WEB_VIEW, "network-session", session, NULL);
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
			/* pages of clients that intercept requests, see WebView.toWebKitUrl */
			for (const char **scheme = (const char *[]){"atl-http", "atl-https", NULL}; *scheme; scheme++) {
				webkit_web_context_register_uri_scheme(webkit_web_view_get_context(WEBKIT_WEB_VIEW(webview)), *scheme, intercept_uri_scheme_request_cb, NULL, NULL);
				webkit_security_manager_register_uri_scheme_as_cors_enabled(security_manager, *scheme);
				webkit_security_manager_register_uri_scheme_as_secure(security_manager, *scheme);
			}
			uri_scheme_registered = TRUE;
		}
		g_signal_connect(webview, "load-changed", G_CALLBACK(web_view_load_changed), wrapper);
		g_signal_connect(webview, "decide-policy", G_CALLBACK(decide_policy), wrapper);
	}
	/* console.log and errors of the page go to the app's log, as Android's chromium logs them */
	webkit_settings_set_enable_write_console_messages_to_stdout(webkit_web_view_get_settings(WEBKIT_WEB_VIEW(webview)), TRUE);
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
