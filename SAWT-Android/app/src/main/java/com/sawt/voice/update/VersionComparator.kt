package com.sawt.voice.update

/** Compares numeric release tags (for example v1.4.2) with the installed versionName. */
internal object VersionComparator {
    fun compare(remoteTag: String, installedVersion: String): Int? {
        fun parts(value: String): List<Int>? {
            val version = Regex("[0-9]+(?:\\.[0-9]+){0,3}").find(value)?.value ?: return null
            return version.split('.').map { it.toIntOrNull() ?: return null }
        }

        val remote = parts(remoteTag) ?: return null
        val installed = parts(installedVersion) ?: return null
        for (index in 0 until maxOf(remote.size, installed.size)) {
            val left = remote.getOrElse(index) { 0 }
            val right = installed.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }
}
