#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>

#include "memory.h"

/* Android kernels have transparent huge pages off or on request only (madvise), and Android
 * apps are not written for them. With the setting "always", which many desktop distributions
 * use, each sparsely used 2 MiB range of an app's anonymous memory gets a whole 2 MiB page at
 * the first touch, and khugepaged later fills more of them: the process holds zero-filled RAM
 * it never asked for, 7 to 20 MiB per app after a few idle minutes on a phone. The flag is
 * inherited across fork and exec, so the dex2oat processes ART starts are covered as well.
 * ATL_TRANSPARENT_HUGE_PAGES=1 keeps the system setting. */
void disable_transparent_huge_pages(void)
{
	const char *keep = getenv("ATL_TRANSPARENT_HUGE_PAGES");
	if (keep && !strcmp(keep, "1"))
		return;
	if (prctl(PR_SET_THP_DISABLE, 1, 0, 0, 0))
		perror("prctl(PR_SET_THP_DISABLE)");
}
