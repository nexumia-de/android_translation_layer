#ifndef WEBKIT_COOKIES_H
#define WEBKIT_COOKIES_H

#include <webkit/webkit.h>

/* TRUE if the load waits for cookies that are still being set in WebKit;
 * it then starts once they are in */
gboolean atl_cookies_defer_load(WebKitWebView *web_view, const char *uri);

#endif
