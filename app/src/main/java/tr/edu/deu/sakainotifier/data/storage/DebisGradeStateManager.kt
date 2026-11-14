package tr.edu.deu.sakainotifier.data.storage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import tr.edu.deu.sakainotifier.data.model.CourseGrade
import tr.edu.deu.sakainotifier.data.model.GradeDifference

/**
 * Manages persistent local state for DEÜ OBS / DEBİS grades.
 * Enforces "Silent Bootstrap" on initial setup (recording existing grades without firing push alerts),
 * preserves local snapshots across monitoring toggle off/on cycles, and calculates precise diffs
 * on subsequent polls.
 */
class DebisGradeStateManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "deu_debis_grade_state"
        const val KEY_IS_FIRST_GRADE_BOOT = "key_is_first_grade_boot"
        const val KEY_GRADE_SNAPSHOT = "key_grade_snapshot"
        const val KEY_DEBIS_COOKIE = "key_debis_cookie"
        const val KEY_GRADE_MONITORING_ACTIVE = "key_grade_monitoring_active"
    }

    private fun getAccountKey(baseKey: String, username: String): String {
        val sanitized = username.substringBefore("@").trim()
        return if (sanitized.isNotEmpty()) "${baseKey}_$sanitized" else baseKey
    }

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun isGradeMonitoringActive(): Boolean {
        return prefs.getBoolean(KEY_GRADE_MONITORING_ACTIVE, false)
    }

    /**
     * Toggles background grade monitoring on/off without touching saved local snapshots
     * or first-boot state, so turning tracking back on avoids redundant bootstrap notifications.
     */
    fun setGradeMonitoringActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_GRADE_MONITORING_ACTIVE, active).apply()
    }

    fun getSessionCookie(username: String): String? {
        val key = getAccountKey(KEY_DEBIS_COOKIE, username)
        return prefs.getString(key, null)
    }

    fun saveSessionCookie(username: String, cookie: String?) {
        val key = getAccountKey(KEY_DEBIS_COOKIE, username)
        if (!cookie.isNullOrBlank()) {
            prefs.edit()
                .putString(key, cookie)
                .apply()
        } else {
            prefs.edit().remove(key).apply()
        }
    }

    fun isFirstGradeBoot(username: String): Boolean {
        val key = getAccountKey(KEY_IS_FIRST_GRADE_BOOT, username)
        return prefs.getBoolean(key, true)
    }

    fun setFirstGradeBootCompleted(username: String) {
        val key = getAccountKey(KEY_IS_FIRST_GRADE_BOOT, username)
        prefs.edit().putBoolean(key, false).apply()
    }

    fun getSavedGrades(username: String): List<CourseGrade> {
        val key = getAccountKey(KEY_GRADE_SNAPSHOT, username)
        val jsonStr = prefs.getString(key, "[]") ?: "[]"
        val results = mutableListOf<CourseGrade>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                results.add(CourseGrade.fromJsonObject(item))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    fun saveGradeSnapshot(username: String, grades: List<CourseGrade>) {
        val key = getAccountKey(KEY_GRADE_SNAPSHOT, username)
        val jsonArray = JSONArray()
        for (grade in grades) {
            jsonArray.put(grade.toJsonObject())
        }
        prefs.edit().putString(key, jsonArray.toString()).apply()
    }

    /**
     * Compares incoming fresh grades with local snapshot.
     * Handles silent initialization when empty, resets baseline silently on semester change,
     * and detects newly announced or updated evaluations (Vize, Quiz, Ödev, Final, Bütünleme, Harf Notu).
     */
    fun detectDifferences(
        username: String,
        freshGrades: List<CourseGrade>
    ): List<GradeDifference> {
        if (freshGrades.isEmpty()) return emptyList()

        val oldGrades = getSavedGrades(username)
        if (oldGrades.isEmpty()) {
            saveGradeSnapshot(username, freshGrades)
            setFirstGradeBootCompleted(username)
            return emptyList()
        }

        val oldTermId = oldGrades.firstOrNull { it.termId.isNotBlank() }?.termId.orEmpty()
        val newTermId = freshGrades.firstOrNull { it.termId.isNotBlank() }?.termId.orEmpty()
        if (oldTermId.isNotBlank() && newTermId.isNotBlank() && oldTermId != newTermId) {
            // Semester rolled over: store new term as silent baseline
            saveGradeSnapshot(username, freshGrades)
            return emptyList()
        }

        val oldGradesMap = oldGrades.associateBy { it.courseCode }
        val differences = mutableListOf<GradeDifference>()

        for (fresh in freshGrades) {
            val old = oldGradesMap[fresh.courseCode]

            if (old == null) {
                // New course appeared in the active term with announced evaluations
                for ((evalName, score) in fresh.evaluations) {
                    if (score.isNotBlank()) {
                        differences.add(
                            GradeDifference(
                                courseCode = fresh.courseCode,
                                courseName = fresh.courseName,
                                evaluationType = evalName,
                                oldValue = null,
                                newValue = score,
                                letterGrade = fresh.letterGrade,
                                status = fresh.status
                            )
                        )
                    }
                }
                continue
            }

            var examDiffTriggeredWithLetter = false

            for ((evalName, newScore) in fresh.evaluations) {
                val oldScore = old.evaluations[evalName]
                val isFinalOrBut = evalName.contains("final", ignoreCase = true) ||
                    evalName.contains("bütünleme", ignoreCase = true) ||
                    evalName.contains("yarıyıl sonu", ignoreCase = true)

                if (oldScore == null && newScore.isNotBlank()) {
                    if (isFinalOrBut) examDiffTriggeredWithLetter = true
                    differences.add(
                        GradeDifference(
                            courseCode = fresh.courseCode,
                            courseName = fresh.courseName,
                            evaluationType = evalName,
                            oldValue = null,
                            newValue = newScore,
                            letterGrade = if (isFinalOrBut) fresh.letterGrade else null,
                            status = if (isFinalOrBut) fresh.status else null
                        )
                    )
                } else if (oldScore != null && oldScore != newScore) {
                    if (isFinalOrBut) examDiffTriggeredWithLetter = true
                    differences.add(
                        GradeDifference(
                            courseCode = fresh.courseCode,
                            courseName = fresh.courseName,
                            evaluationType = "$evalName (Güncellendi)",
                            oldValue = oldScore,
                            newValue = newScore,
                            letterGrade = if (isFinalOrBut) fresh.letterGrade else null,
                            status = if (isFinalOrBut) fresh.status else null
                        )
                    )
                }
            }

            // Check Letter Grade announcement or update (only if not already bundled in Final/Büt alert)
            if (!examDiffTriggeredWithLetter) {
                if (old.letterGrade == null && fresh.letterGrade != null) {
                    differences.add(
                        GradeDifference(
                            courseCode = fresh.courseCode,
                            courseName = fresh.courseName,
                            evaluationType = "Harf Notu",
                            oldValue = null,
                            newValue = fresh.letterGrade,
                            letterGrade = fresh.letterGrade,
                            status = fresh.status
                        )
                    )
                } else if (old.letterGrade != null && fresh.letterGrade != null && old.letterGrade != fresh.letterGrade) {
                    differences.add(
                        GradeDifference(
                            courseCode = fresh.courseCode,
                            courseName = fresh.courseName,
                            evaluationType = "Harf Notu (Güncellendi)",
                            oldValue = old.letterGrade,
                            newValue = fresh.letterGrade,
                            letterGrade = fresh.letterGrade,
                            status = fresh.status
                        )
                    )
                }
            }
        }

        // Always update snapshot with freshest data
        saveGradeSnapshot(username, freshGrades)

        return differences
    }

    /**
     * Removes saved OBS grade snapshot, first-boot flag, and cached session cookie for an account.
     */
    fun clearAccountState(username: String) {
        prefs.edit()
            .remove(getAccountKey(KEY_GRADE_SNAPSHOT, username))
            .remove(getAccountKey(KEY_IS_FIRST_GRADE_BOOT, username))
            .remove(getAccountKey(KEY_DEBIS_COOKIE, username))
            .apply()
    }
}
