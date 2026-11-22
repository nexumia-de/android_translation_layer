/*
 * Wrapper for issue #118: runs a command with transparent huge pages off.
 *
 * PR_SET_THP_DISABLE is kept across fork and exec, so neither page faults
 * nor khugepaged give the command 2 MB pages. For an A/B against the
 * system's setting (/sys/kernel/mm/transparent_hugepage/enabled).
 *
 * Build: cc -O1 -o perf-thp-off perf-thp-off.c
 * Run:   PERF_CMD=perf-thp-off tools/perf-run.sh ... -- android-translation-layer ARGS
 */
#include <stdio.h>
#include <sys/prctl.h>
#include <unistd.h>

int main(int argc, char **argv)
{
	if (argc < 2) {
		fprintf(stderr, "usage: %s COMMAND [ARG...]\n", argv[0]);
		return 2;
	}
	if (prctl(PR_SET_THP_DISABLE, 1, 0, 0, 0))
		perror("PR_SET_THP_DISABLE");
	execvp(argv[1], argv + 1);
	perror(argv[1]);
	return 127;
}
