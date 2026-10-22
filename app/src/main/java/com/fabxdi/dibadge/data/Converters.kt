package com.fabxdi.dibadge.data

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter = DateTimeFormatter.ISO_LOCAL_TIME
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.format(formatter)
    }

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let {
            LocalDate.parse(it, formatter)
        }
    }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? {
        return value?.format(timeFormatter)
    }

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? {
        return value?.let {
            LocalTime.parse(it, timeFormatter)
        }
    }

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? {
        return value?.format(dateTimeFormatter)
    }

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        return value?.let {
            LocalDateTime.parse(it, dateTimeFormatter)
        }
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isBlank()) emptyList() else value.split(",")
    }

    @TypeConverter
    fun fromInboxSignerList(list: List<InboxSigner>): String {
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("name", item.name)
            obj.put("role", item.role)
            obj.put("hasAnswered", item.hasAnswered)
            obj.put("statusText", item.statusText)
            item.stamp?.let { stamp ->
                val stampObj = JSONObject()
                stampObj.put("name", stamp.name)
                stampObj.put("badgeNumber", stamp.badgeNumber)
                stampObj.put("signatureUrl", stamp.signatureUrl)
                stampObj.put("actionText", stamp.actionText)
                stampObj.put("timestampText", stamp.timestampText)
                obj.put("stamp", stampObj)
            }
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toInboxSignerList(value: String): List<InboxSigner> {
        if (value.isBlank()) return emptyList()
        val list = mutableListOf<InboxSigner>()
        try {
            val array = JSONArray(value)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val stampObj = obj.optJSONObject("stamp")
                val stamp = if (stampObj != null) {
                    InboxStamp(
                        name = stampObj.optString("name"),
                        badgeNumber = stampObj.optString("badgeNumber"),
                        signatureUrl = stampObj.optString("signatureUrl"),
                        actionText = stampObj.optString("actionText"),
                        timestampText = stampObj.optString("timestampText")
                    )
                } else null

                list.add(
                    InboxSigner(
                        name = obj.optString("name"),
                        role = obj.optString("role"),
                        hasAnswered = obj.optBoolean("hasAnswered"),
                        statusText = obj.optString("statusText"),
                        stamp = stamp
                    )
                )
            }
        } catch (e: Exception) {}
        return list
    }

    @TypeConverter
    fun fromInboxStampList(list: List<InboxStamp>): String {
        val array = JSONArray()
        for (stamp in list) {
            val stampObj = JSONObject()
            stampObj.put("name", stamp.name)
            stampObj.put("badgeNumber", stamp.badgeNumber)
            stampObj.put("signatureUrl", stamp.signatureUrl)
            stampObj.put("actionText", stamp.actionText)
            stampObj.put("timestampText", stamp.timestampText)
            array.put(stampObj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toInboxStampList(value: String): List<InboxStamp> {
        if (value.isBlank()) return emptyList()
        val list = mutableListOf<InboxStamp>()
        try {
            val array = JSONArray(value)
            for (i in 0 until array.length()) {
                val stampObj = array.getJSONObject(i)
                list.add(
                    InboxStamp(
                        name = stampObj.optString("name"),
                        badgeNumber = stampObj.optString("badgeNumber"),
                        signatureUrl = stampObj.optString("signatureUrl"),
                        actionText = stampObj.optString("actionText"),
                        timestampText = stampObj.optString("timestampText")
                    )
                )
            }
        } catch (e: Exception) {}
        return list
    }
}
