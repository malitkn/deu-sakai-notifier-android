package tr.edu.deu.sakainotifier.data.storage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import tr.edu.deu.sakainotifier.data.model.CourseGrade
import tr.edu.deu.sakainotifier.data.model.GradeDifference

/**
 * Manages persistent local state for DEBİS grades.
 * Enforces "Silent Bootstrap" on initial account setup (recording existing grades without firing push alerts)
 * and calculates precise diffs on subsequent background polls.
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
        private const val KEY_SESSION_EXPIRED_NOTIFIED = "key_debis_session_expired_notified"
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

    fun setGradeMonitoringActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_GRADE_MONITORING_ACTIVE, active).apply()
    }

    fun getSessionCookie(username: String): String? {
        val key = getAccountKey(KEY_DEBIS_COOKIE, username)
        return prefs.getString(key, null)
    }

    fun saveSessionCookie(username: String, cookie: String?) {
        val key = getAccountKey(KEY_DEBIS_COOKIE, username)
        val expiredKey = getAccountKey(KEY_SESSION_EXPIRED_NOTIFIED, username)
        if (!cookie.isNullOrBlank()) {
            prefs.edit()
                .putString(key, cookie)
                .putBoolean(expiredKey, false)
                .apply()
        } else {
            prefs.edit().remove(key).apply()
        }
    }

    fun wasSessionExpiredNotified(username: String): Boolean {
        val key = getAccountKey(KEY_SESSION_EXPIRED_NOTIFIED, username)
        return prefs.getBoolean(key, false)
    }

    fun markSessionExpiredNotified(username: String) {
        val key = getAccountKey(KEY_SESSION_EXPIRED_NOTIFIED, username)
        prefs.edit().putBoolean(key, true).apply()
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
     * Detects newly announced Vize, Final, Bütünleme or Letter grades.
     */
    fun detectDifferences(
        username: String,
        freshGrades: List<CourseGrade>
    ): List<GradeDifference> {
        val oldGradesMap = getSavedGrades(username).associateBy { it.courseCode }
        val differences = mutableListOf<GradeDifference>()

        for (fresh in freshGrades) {
            val old = oldGradesMap[fresh.courseCode]

            if (old == null) {
                // New course appeared with existing grades
                if (!fresh.final.isNullOrBlank()) {
                    differences.add(
                        GradeDifference(
                            courseCode = fresh.courseCode,
                            courseName = fresh.courseName,
                            evaluationType = "Final",
                            oldValue = null,
                            newValue = fresh.final,
                            letterGrade = fresh.letterGrade,
                            status = fresh.status
                        )
                    )
                } else if (!fresh.vize.isNullOrBlank()) {
                    differences.add(
                        GradeDifference(
                            courseCode = fresh.courseCode,
                            courseName = fresh.courseName,
                            evaluationType = "Vize",
                            oldValue = null,
                            newValue = fresh.vize
                        )
                    )
                }
                continue
            }

            // Check Vize announcement
            if (old.vize == null && fresh.vize != null) {
                differences.add(
                    GradeDifference(
                        courseCode = fresh.courseCode,
                        courseName = fresh.courseName,
                        evaluationType = "Vize",
                        oldValue = null,
                        newValue = fresh.vize
                    )
                )
            } else if (old.vize != null && fresh.vize != null && old.vize != fresh.vize) {
                differences.add(
                    GradeDifference(
                        courseCode = fresh.courseCode,
                        courseName = fresh.courseName,
                        evaluationType = "Vize (Güncellendi)",
                        oldValue = old.vize,
                        newValue = fresh.vize
                    )
                )
            }

            var examDiffTriggeredWithLetter = false

            // Check Final announcement
            if (old.final == null && fresh.final != null) {
                examDiffTriggeredWithLetter = true
                differences.add(
                    GradeDifference(
                        courseCode = fresh.courseCode,
                        courseName = fresh.courseName,
                        evaluationType = "Final",
                        oldValue = null,
                        newValue = fresh.final,
                        letterGrade = fresh.letterGrade,
                        status = fresh.status
                    )
                )
            } else if (old.final != null && fresh.final != null && old.final != fresh.final) {
                examDiffTriggeredWithLetter = true
                differences.add(
                    GradeDifference(
                        courseCode = fresh.courseCode,
                        courseName = fresh.courseName,
                        evaluationType = "Final (Güncellendi)",
                        oldValue = old.final,
                        newValue = fresh.final,
                        letterGrade = fresh.letterGrade,
                        status = fresh.status
                    )
                )
            }

            // Check Bütünleme announcement
            if (old.butunleme == null && fresh.butunleme != null) {
                examDiffTriggeredWithLetter = true
                differences.add(
                    GradeDifference(
                        courseCode = fresh.courseCode,
                        courseName = fresh.courseName,
                        evaluationType = "Bütünleme",
                        oldValue = null,
                        newValue = fresh.butunleme,
                        letterGrade = fresh.letterGrade,
                        status = fresh.status
                    )
                )
            } else if (old.butunleme != null && fresh.butunleme != null && old.butunleme != fresh.butunleme) {
                examDiffTriggeredWithLetter = true
                differences.add(
                    GradeDifference(
                        courseCode = fresh.courseCode,
                        courseName = fresh.courseName,
                        evaluationType = "Bütünleme (Güncellendi)",
                        oldValue = old.butunleme,
                        newValue = fresh.butunleme,
                        letterGrade = fresh.letterGrade,
                        status = fresh.status
                    )
                )
            }

            // Check Letter Grade (Yarıyıl Sonu Başarı Notu) announcement or update (only if not already bundled in Final/Büt alert)
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
}
