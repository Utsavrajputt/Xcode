package com.invictus.xcode.core.git

import org.eclipse.jgit.lib.BatchingProgressMonitor

/** Live progress line for clone/push/pull/fetch UIs. */
data class GitProgress(val task: String = "", val completed: Int = 0, val total: Int = 0)

internal fun gitProgressMonitor(onProgress: (GitProgress) -> Unit): BatchingProgressMonitor =
    object : BatchingProgressMonitor() {
        override fun onUpdate(taskName: String?, workCurr: Int) {
            onProgress(GitProgress(taskName ?: "", workCurr, 0))
        }

        override fun onEndTask(taskName: String?, workCurr: Int) {
            onProgress(GitProgress(taskName ?: "", workCurr, 0))
        }

        override fun onUpdate(taskName: String?, workCurr: Int, workTotal: Int, percentDone: Int) {
            onProgress(GitProgress(taskName ?: "", workCurr, workTotal))
        }

        override fun onEndTask(taskName: String?, workCurr: Int, workTotal: Int, percentDone: Int) = Unit
    }
