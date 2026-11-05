package com.fabxdi.dibadge.ui.settings

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores the app passcode as a salted PBKDF2 hash (never the PIN itself) and limits wrong guesses.
 *
 * Your lock screen must call [verify] instead of comparing "passcode_hash" to the typed PIN.
 * An older plain PIN already on the phone still works once and is converted to a hash.
 */
object PasscodeStore {
    private const val PREFS = "passcode_prefs"
    private const val KEY_HASH = "passcode_hash"
    private const val KEY_SALT = "passcode_salt"
    private const val KEY_FAILS = "passcode_fails"
    private const val KEY_LOCK_UNTIL = "passcode_lock_until"
    private const val ITERATIONS = 100_000
    private const val FREE_ATTEMPTS = 5

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isSet(context: Context): Boolean = prefs(context).getString(KEY_HASH, null) != null

    fun set(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(context).edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCK_UNTIL, 0L)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_HASH).remove(KEY_SALT).remove(KEY_FAILS).remove(KEY_LOCK_UNTIL)
            .apply()
    }

    /** Milliseconds until another attempt is allowed, 0 when not locked out. */
    fun lockRemainingMs(context: Context): Long =
        (prefs(context).getLong(KEY_LOCK_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun verify(context: Context, pin: String): Boolean {
        if (lockRemainingMs(context) > 0L) return false
        val p = prefs(context)
        val stored = p.getString(KEY_HASH, null) ?: return false
        val saltB64 = p.getString(KEY_SALT, null)

        val ok = if (saltB64 == null) {
            // Old format: the PIN itself was saved. Accept it once, then store it as a hash.
            val match = MessageDigest.isEqual(stored.toByteArray(), pin.toByteArray())
            if (match) set(context, pin)
            match
        } else {
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            MessageDigest.isEqual(Base64.decode(stored, Base64.NO_WRAP), hash(pin, salt))
        }

        if (ok) {
            p.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0L).apply()
        } else {
            val fails = p.getInt(KEY_FAILS, 0) + 1
            val extra = (fails - FREE_ATTEMPTS).coerceAtLeast(0)
            val lockMs = if (fails >= FREE_ATTEMPTS) 30_000L * (1L shl extra.coerceAtMost(6)) else 0L
            p.edit()
                .putInt(KEY_FAILS, fails)
                .putLong(KEY_LOCK_UNTIL, if (lockMs > 0) System.currentTimeMillis() + lockMs else 0L)
                .apply()
        }
        return ok
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }
}
