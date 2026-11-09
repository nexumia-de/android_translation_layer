/*
 * LD_PRELOAD shim for issue #118: logs who opens a DRM render node.
 *
 * Every open() of /dev/dri/renderD* makes a DRM client with its own GPU
 * memory (see tools/perf-drm-fds.sh); this prints the fd and the call stack
 * (library, nearest exported symbol, offset) of each such open into
 * $PERF_DRM_OPEN_DIR/open-<pid>.txt, so each client of fdinfo can be named.
 *
 * Build: cc -shared -fPIC -O1 -o perf-drm-open.so perf-drm-open.c
 * Run:   PERF_DRM_OPEN_DIR=/tmp/x LD_PRELOAD=./perf-drm-open.so android-translation-layer ...
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>
#include <unwind.h>

static FILE *out;

static _Unwind_Reason_Code frame(struct _Unwind_Context *ctx, void *arg)
{
	int *n = arg;
	void *ip = (void *)_Unwind_GetIP(ctx);
	Dl_info info;
	if ((*n)++ == 0) // the shim itself
		return _URC_NO_REASON;
	if (dladdr(ip, &info) && info.dli_fname) {
		const char *base = strrchr(info.dli_fname, '/');
		fprintf(out, "  %-28s %s+0x%lx (lib+0x%lx)\n", base ? base + 1 : info.dli_fname,
		        info.dli_sname ? info.dli_sname : "?",
		        info.dli_sname ? (unsigned long)((char *)ip - (char *)info.dli_saddr) : 0,
		        (unsigned long)((char *)ip - (char *)info.dli_fbase));
	} else {
		fprintf(out, "  %p\n", ip);
	}
	return *n > 40 ? _URC_END_OF_STACK : _URC_NO_REASON;
}

static void report(const char *path, int fd)
{
	const char *dir = getenv("PERF_DRM_OPEN_DIR");
	struct timespec ts;
	char name[512];
	int n = 0;
	if (!dir || fd < 0 || !strstr(path, "/dev/dri/renderD"))
		return;
	if (!out) {
		snprintf(name, sizeof(name), "%s/open-%d.txt", dir, getpid());
		out = fopen(name, "a");
		if (!out)
			return;
	}
	clock_gettime(CLOCK_MONOTONIC, &ts);
	fprintf(out, "== fd %d %s tid %ld t %ld.%03ld\n", fd, path, syscall(SYS_gettid), (long)ts.tv_sec, ts.tv_nsec / 1000000);
	_Unwind_Backtrace(frame, &n);
	fflush(out);
}

int open(const char *path, int flags, ...)
{
	mode_t mode = 0;
	int fd;
	if (flags & (O_CREAT | O_TMPFILE)) {
		va_list ap;
		va_start(ap, flags);
		mode = va_arg(ap, mode_t);
		va_end(ap);
	}
	fd = syscall(SYS_openat, AT_FDCWD, path, flags | O_LARGEFILE, mode);
	report(path, fd);
	return fd;
}

int openat(int dirfd, const char *path, int flags, ...)
{
	mode_t mode = 0;
	int fd;
	if (flags & (O_CREAT | O_TMPFILE)) {
		va_list ap;
		va_start(ap, flags);
		mode = va_arg(ap, mode_t);
		va_end(ap);
	}
	fd = syscall(SYS_openat, dirfd, path, flags | O_LARGEFILE, mode);
	report(path, fd);
	return fd;
}
