#include <webkit/webkit.h>

/* WebKit's side of CookieManager.java, in libtranslation_layer_webkit.so
 * like the WebView glue: CookieManager keeps the cookies itself and only
 * calls in here once WebView has loaded WebKit. Every function runs on the
 * main thread; calls from other threads are queued there. */

#include "../defines.h"
#include "../util.h"

#include "webkit_cookies.h"

#include "../generated_headers/android_webkit_CookieManager.h"

static WebKitCookieManager *cookie_manager(void)
{
	return webkit_network_session_get_cookie_manager(webkit_network_session_get_default());
}

/* ---- pages wait for the cookies set before them ---- */

static int ops_pending;
static guint flush_timeout;

struct deferred_load {
	WebKitWebView *web_view;
	WebKitURIRequest *request;
};
static GSList *deferred_loads;

static void run_deferred_loads(void)
{
	GSList *loads = deferred_loads;
	deferred_loads = NULL;
	for (GSList *l = loads; l; l = l->next) {
		struct deferred_load *d = l->data;
		webkit_web_view_load_request(d->web_view, d->request);
		g_object_unref(d->web_view);
		g_object_unref(d->request);
		g_free(d);
	}
	g_slist_free(loads);
}

/* in case WebKit never answers, the page loads without waiting longer */
static gboolean flush_timeout_cb(gpointer user_data)
{
	flush_timeout = 0;
	run_deferred_loads();
	return G_SOURCE_REMOVE;
}

gboolean atl_cookies_defer_load(WebKitWebView *web_view, WebKitURIRequest *request)
{
	if (!ops_pending)
		return FALSE;
	struct deferred_load *d = g_new(struct deferred_load, 1);
	d->web_view = g_object_ref(web_view);
	d->request = g_object_ref(request);
	deferred_loads = g_slist_append(deferred_loads, d);
	if (!flush_timeout)
		flush_timeout = g_timeout_add(2000, flush_timeout_cb, NULL);
	return TRUE;
}

static void op_started(void)
{
	ops_pending++;
}

static void op_done(void)
{
	if (--ops_pending == 0) {
		if (flush_timeout) {
			g_source_remove(flush_timeout);
			flush_timeout = 0;
		}
		run_deferred_loads();
	}
}

static void add_done_cb(GObject *source, GAsyncResult *result, gpointer user_data)
{
	GError *error = NULL;
	if (!webkit_cookie_manager_add_cookie_finish(WEBKIT_COOKIE_MANAGER(source), result, &error)) {
		g_warning("CookieManager: adding a cookie to WebKit failed: %s", error->message);
		g_error_free(error);
	}
	op_done();
}

static void delete_done_cb(GObject *source, GAsyncResult *result, gpointer user_data)
{
	webkit_cookie_manager_delete_cookie_finish(WEBKIT_COOKIE_MANAGER(source), result, NULL);
	op_done();
}

static void clear_done_cb(GObject *source, GAsyncResult *result, gpointer user_data)
{
	webkit_website_data_manager_clear_finish(WEBKIT_WEBSITE_DATA_MANAGER(source), result, NULL);
	op_done();
}

/* ---- cookies a page set come back to CookieManager.java ---- */

static void deliver_cookies(GObject *source, GAsyncResult *result, int generation)
{
	GList *list = webkit_cookie_manager_get_all_cookies_finish(WEBKIT_COOKIE_MANAGER(source), result, NULL);
	JNIEnv *env = get_jni_env();
	jobjectArray entries = (*env)->NewObjectArray(env, g_list_length(list), (*env)->FindClass(env, "java/lang/String"), NULL);
	int i = 0;
	for (GList *l = list; l; l = l->next, i++) {
		SoupCookie *cookie = l->data;
		GDateTime *expires = soup_cookie_get_expires(cookie);
		int same_site;
		switch (soup_cookie_get_same_site_policy(cookie)) {
			case SOUP_SAME_SITE_POLICY_NONE: same_site = 1; break;
			case SOUP_SAME_SITE_POLICY_LAX: same_site = 2; break;
			case SOUP_SAME_SITE_POLICY_STRICT: same_site = 3; break;
			default: same_site = 0;
		}
		char *entry = g_strdup_printf("%s\t%s\t%s\t%s\t%" G_GINT64_FORMAT "\t%s%s\t%d",
		                              soup_cookie_get_name(cookie), soup_cookie_get_value(cookie),
		                              soup_cookie_get_domain(cookie), soup_cookie_get_path(cookie),
		                              expires ? g_date_time_to_unix(expires) * 1000 : (gint64)-1,
		                              soup_cookie_get_secure(cookie) ? "s" : "", soup_cookie_get_http_only(cookie) ? "h" : "",
		                              same_site);
		jstring jentry = _JSTRING(entry);
		(*env)->SetObjectArrayElement(env, entries, i, jentry);
		(*env)->DeleteLocalRef(env, jentry);
		g_free(entry);
	}
	g_list_free_full(list, (GDestroyNotify)soup_cookie_free);
	J__CookieManager__internalWebKitCookies(env, generation, entries);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
	}
	(*env)->DeleteLocalRef(env, entries);
}

static void get_all_done_cb(GObject *source, GAsyncResult *result, gpointer user_data)
{
	deliver_cookies(source, result, GPOINTER_TO_INT(user_data));
}

/* Android's onPageFinished sees the cookies the page set, so the page's
 * cookies are copied to CookieManager.java first. Generation -1 never
 * matches, so this only adds and updates cookies. */
struct fetch_then {
	void (*cb)(gpointer);
	gpointer data;
};

static void fetch_then_cb(GObject *source, GAsyncResult *result, gpointer user_data)
{
	struct fetch_then *f = user_data;
	deliver_cookies(source, result, -1);
	f->cb(f->data);
	g_free(f);
}

void atl_cookies_fetch_then(void (*cb)(gpointer), gpointer data)
{
	struct fetch_then *f = g_new(struct fetch_then, 1);
	f->cb = cb;
	f->data = data;
	webkit_cookie_manager_get_all_cookies(cookie_manager(), NULL, fetch_then_cb, f);
}

/* a page sets several cookies at once, so ask for the list once they are in */
static guint changed_timeout;

static gboolean changed_timeout_cb(gpointer user_data)
{
	changed_timeout = 0;
	JNIEnv *env = get_jni_env();
	J__CookieManager__internalWebKitChanged(env);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
	}
	return G_SOURCE_REMOVE;
}

static void cookies_changed(WebKitCookieManager *manager, gpointer user_data)
{
	if (!changed_timeout)
		changed_timeout = g_timeout_add(300, changed_timeout_cb, NULL);
}

/* ---- calls from CookieManager.java, moved to the main thread ---- */

struct cookie_op {
	enum { OP_ATTACH, OP_ADD, OP_DELETE, OP_DELETE_ALL, OP_REQUEST } type;
	SoupCookie *cookie;
	int generation;
};

static gboolean run_op(gpointer user_data)
{
	struct cookie_op *op = user_data;
	switch (op->type) {
		case OP_ATTACH:
			g_signal_connect(cookie_manager(), "changed", G_CALLBACK(cookies_changed), NULL);
			break;
		case OP_ADD:
			op_started();
			webkit_cookie_manager_add_cookie(cookie_manager(), op->cookie, NULL, add_done_cb, NULL);
			break;
		case OP_DELETE:
			op_started();
			webkit_cookie_manager_delete_cookie(cookie_manager(), op->cookie, NULL, delete_done_cb, NULL);
			break;
		case OP_DELETE_ALL:
			op_started();
			webkit_website_data_manager_clear(webkit_network_session_get_website_data_manager(webkit_network_session_get_default()),
			                                  WEBKIT_WEBSITE_DATA_COOKIES, 0, NULL, clear_done_cb, NULL);
			break;
		case OP_REQUEST:
			webkit_cookie_manager_get_all_cookies(cookie_manager(), NULL, get_all_done_cb, GINT_TO_POINTER(op->generation));
			break;
	}
	if (op->cookie)
		soup_cookie_free(op->cookie);
	g_free(op);
	return G_SOURCE_REMOVE;
}

static void queue_op(int type, SoupCookie *cookie, int generation)
{
	struct cookie_op *op = g_new0(struct cookie_op, 1);
	op->type = type;
	op->cookie = cookie;
	op->generation = generation;
	g_main_context_invoke(NULL, run_op, op);
}

/* libsoup marks a cookie for a domain and its subdomains with a leading dot */
static SoupCookie *new_cookie(JNIEnv *env, jstring name, jstring value, jstring domain, jboolean host_only, jstring path)
{
	char *soup_domain = g_strconcat(host_only ? "" : ".", _CSTRING(domain), NULL);
	SoupCookie *cookie = soup_cookie_new(_CSTRING(name), value ? _CSTRING(value) : "", soup_domain, _CSTRING(path), -1);
	g_free(soup_domain);
	return cookie;
}

JNIEXPORT void JNICALL Java_android_webkit_CookieManager_native_1attach(JNIEnv *env, jclass class)
{
	queue_op(OP_ATTACH, NULL, 0);
}

JNIEXPORT void JNICALL Java_android_webkit_CookieManager_native_1addCookie(JNIEnv *env, jclass class, jstring name, jstring value, jstring domain, jboolean host_only, jstring path, jlong expires, jboolean secure, jboolean http_only, jint same_site)
{
	SoupCookie *cookie = new_cookie(env, name, value, domain, host_only, path);
	if (expires != -1) {
		GDateTime *date = g_date_time_new_from_unix_utc(expires / 1000);
		soup_cookie_set_expires(cookie, date);
		g_date_time_unref(date);
	}
	soup_cookie_set_secure(cookie, secure);
	soup_cookie_set_http_only(cookie, http_only);
	if (same_site == 1)
		soup_cookie_set_same_site_policy(cookie, SOUP_SAME_SITE_POLICY_NONE);
	else if (same_site == 2)
		soup_cookie_set_same_site_policy(cookie, SOUP_SAME_SITE_POLICY_LAX);
	else if (same_site == 3)
		soup_cookie_set_same_site_policy(cookie, SOUP_SAME_SITE_POLICY_STRICT);
	queue_op(OP_ADD, cookie, 0);
}

JNIEXPORT void JNICALL Java_android_webkit_CookieManager_native_1deleteCookie(JNIEnv *env, jclass class, jstring name, jstring domain, jboolean host_only, jstring path)
{
	queue_op(OP_DELETE, new_cookie(env, name, NULL, domain, host_only, path), 0);
}

JNIEXPORT void JNICALL Java_android_webkit_CookieManager_native_1deleteAll(JNIEnv *env, jclass class)
{
	queue_op(OP_DELETE_ALL, NULL, 0);
}

JNIEXPORT void JNICALL Java_android_webkit_CookieManager_native_1requestCookies(JNIEnv *env, jclass class, jint generation)
{
	queue_op(OP_REQUEST, NULL, generation);
}
