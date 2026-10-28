package `in`.surya.optician

/**
 * Centralized local role policy. These checks are UI/action guards for this
 * single-device app; they are not a substitute for backend authorization.
 */
object RolePermissions {
    fun isOwner(role: String): Boolean = role == "Owner"

    fun canManageRecords(role: String): Boolean =
        role == "Owner" || role == "Manager"

    fun canDeleteRecords(role: String): Boolean = isOwner(role)

    fun canAccess(role: String, tab: String): Boolean = when (tab) {
        "Users" -> isOwner(role)
        "Reports" -> role == "Owner" || role == "Manager"
        else -> true
    }
}
