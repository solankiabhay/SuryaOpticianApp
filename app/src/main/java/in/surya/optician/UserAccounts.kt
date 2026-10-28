package \`in\`.surya.optician

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

data class LocalUser(val username: String, val role: String, val salt: String, val passwordHash: String)

class UserAccounts(context: Context) {
    private val prefs = context.getSharedPreferences("surya_user_accounts", Context.MODE_PRIVATE)
    private val key = "users_json"

    fun loadUsers(): List<LocalUser> {
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            LocalUser(item.optString("username"), item.optString("role", "Staff"), item.optString("salt"), item.optString("passwordHash"))
                .takeIf { it.username.isNotBlank() && it.salt.isNotBlank() && it.passwordHash.isNotBlank() }
        }
    }

    fun create(username: String, password: String, role: String) {
        require(username.length in 3..32 && username.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' }) {
            "Username must be 3–32 letters, numbers, dots, dashes or underscores."
        }
        require(password.length >= 8) { "Password must be at least 8 characters." }
        require(role in setOf("Owner", "Manager", "Staff")) { "Invalid account role." }
        val users = loadUsers()
        require(users.none { it.username.equals(username, ignoreCase = true) }) { "That username already exists." }
        require(role != "Owner" || users.none { it.role == "Owner" }) { "An owner account already exists." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val user = LocalUser(username, role, Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(hash(password, salt)))
        save(users + user)
    }

    fun authenticate(username: String, password: String): LocalUser? {
        val user = loadUsers().firstOrNull { it.username.equals(username.trim(), ignoreCase = true) } ?: return null
        val salt = runCatching { Base64.getDecoder().decode(user.salt) }.getOrNull() ?: return null
        val expected = runCatching { Base64.getDecoder().decode(user.passwordHash) }.getOrNull() ?: return null
        return user.takeIf { MessageDigest.isEqual(hash(password, salt), expected) }
    }

    fun remove(username: String) {
        val users = loadUsers()
        require(users.none { it.username.equals(username, true) && it.role == "Owner" }) { "The owner account cannot be removed." }
        save(users.filterNot { it.username.equals(username, true) })
    }

    private fun save(users: List<LocalUser>) {
        val array = JSONArray()
        users.forEach { user ->
            array.put(JSONObject().put("username", user.username).put("role", user.role).put("salt", user.salt).put("passwordHash", user.passwordHash))
        }
        check(prefs.edit().putString(key, array.toString()).commit()) { "Could not save user accounts." }
    }

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
