package me.bmax.apatch.ui.superuser

enum class SuperUserSort {
    NAME,
    PACKAGE_NAME,
    INSTALL_TIME;

    companion object {
        val DEFAULT = NAME

        fun fromString(value: String?): SuperUserSort {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
        }
    }
}
