package com.umbamobile.smslogger

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * SmsLogger - A library for accessing and analyzing SMS logs on Android devices
 *
 * This library provides tools for accessing SMS messages stored on an Android device,
 * with support for filtering, searching, and exporting SMS data.
 */
class SmsLogger private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "SmsLogger"
        
        // Singleton instance
        @Volatile private var INSTANCE: SmsLogger? = null
        
        /**
         * Get the singleton instance of SmsLogger
         * @param context Application context
         * @return SmsLogger instance
         */
        fun getInstance(context: Context): SmsLogger {
            return INSTANCE ?: synchronized(this) {
                val instance = SmsLogger(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Check if the app has SMS read permission
         * @param context Application context
         * @return Boolean indicating if permission is granted
         */
        fun hasPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Data class to hold SMS message information
     */
    data class SmsMessage(
        val id: String,
        val threadId: String,
        val address: String,
        val body: String,
        val date: Long,
        val type: Int,
        val read: Boolean,
        val seen: Boolean,
        val serviceCenterAddress: String?,
        val protocol: Int?
    ) {
        /**
         * Get a formatted date string
         * @param pattern Date format pattern
         * @return Formatted date string
         */
        fun getFormattedDate(pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = date
            val sdf = SimpleDateFormat(pattern, Locale.getDefault())
            return sdf.format(calendar.time)
        }
        
        /**
         * Get the human-readable message type
         * @return String representing the message type
         */
        fun getMessageTypeName(): String {
            return when (type) {
                Telephony.Sms.MESSAGE_TYPE_INBOX -> "Inbox"
                Telephony.Sms.MESSAGE_TYPE_SENT -> "Sent"
                Telephony.Sms.MESSAGE_TYPE_DRAFT -> "Draft"
                Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "Outbox"
                Telephony.Sms.MESSAGE_TYPE_FAILED -> "Failed"
                Telephony.Sms.MESSAGE_TYPE_QUEUED -> "Queued"
                else -> "Unknown"
            }
        }
    }
    
    /**
     * Callback interface for asynchronous SMS operations
     */
    interface SmsCallback {
        fun onSuccess(messages: List<SmsMessage>)
        fun onError(error: Exception)
    }
    
    /**
     * Filter options for SMS messages
     */
    data class SmsFilter(
        val type: Int? = null,               // Filter by message type
        val address: String? = null,         // Filter by address/phone number
        val searchQuery: String? = null,     // Search in message body
        val startDate: Long? = null,         // Filter by start date
        val endDate: Long? = null,           // Filter by end date
        val threadId: String? = null,        // Filter by conversation thread
        val unreadOnly: Boolean = false,     // Filter only unread messages
        val limit: Int? = null               // Limit number of results
    )
    
    /**
     * Get all SMS messages
     * @return List of SmsMessage objects or empty list if permission not granted
     */
    suspend fun getAllMessages(): List<SmsMessage> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) {
            Log.e(TAG, "SMS permission not granted")
            return@withContext emptyList<SmsMessage>()
        }
        
        return@withContext querySmsMessages()
    }
    
    /**
     * Get SMS messages with filtering
     * @param filter SmsFilter object with filter criteria
     * @return Filtered list of SmsMessage objects
     */
    suspend fun getMessages(filter: SmsFilter): List<SmsMessage> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) {
            Log.e(TAG, "SMS permission not granted")
            return@withContext emptyList<SmsMessage>()
        }
        
        // Build selection and args based on filter
        val selectionCriteria = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()
        
        filter.type?.let {
            selectionCriteria.add("type = ?")
            selectionArgs.add(it.toString())
        }
        
        filter.address?.let {
            selectionCriteria.add("address LIKE ?")
            selectionArgs.add("%$it%")
        }
        
        filter.searchQuery?.let {
            selectionCriteria.add("body LIKE ?")
            selectionArgs.add("%$it%")
        }
        
        if (filter.startDate != null && filter.endDate != null) {
            selectionCriteria.add("date >= ? AND date <= ?")
            selectionArgs.add(filter.startDate.toString())
            selectionArgs.add(filter.endDate.toString())
        } else if (filter.startDate != null) {
            selectionCriteria.add("date >= ?")
            selectionArgs.add(filter.startDate.toString())
        } else if (filter.endDate != null) {
            selectionCriteria.add("date <= ?")
            selectionArgs.add(filter.endDate.toString())
        }
        
        filter.threadId?.let {
            selectionCriteria.add("thread_id = ?")
            selectionArgs.add(it)
        }
        
        if (filter.unreadOnly) {
            selectionCriteria.add("read = 0")
        }
        
        // Create selection string
        val selection = if (selectionCriteria.isNotEmpty()) {
            selectionCriteria.joinToString(" AND ")
        } else {
            null
        }
        
        // Create selection args array
        val args = if (selectionArgs.isNotEmpty()) {
            selectionArgs.toTypedArray()
        } else {
            null
        }
        
        // Add limit if specified
        val limit = filter.limit?.toString()
        
        return@withContext querySmsMessages(selection, args, limit)
    }
    
    /**
     * Get messages asynchronously with callback
     * @param callback SmsCallback for results
     */
    fun getMessagesAsync(callback: SmsCallback) {
        Thread {
            try {
                val messages = querySmsMessages()
                callback.onSuccess(messages)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching SMS messages", e)
                callback.onError(e)
            }
        }.start()
    }
    
    /**
     * Get filtered messages asynchronously with callback
     * @param filter SmsFilter object with filter criteria
     * @param callback SmsCallback for results
     */
    fun getMessagesAsync(filter: SmsFilter, callback: SmsCallback) {
        Thread {
            try {
                // Build selection and args based on filter
                val selectionCriteria = mutableListOf<String>()
                val selectionArgs = mutableListOf<String>()
                
                filter.type?.let {
                    selectionCriteria.add("type = ?")
                    selectionArgs.add(it.toString())
                }
                
                filter.address?.let {
                    selectionCriteria.add("address LIKE ?")
                    selectionArgs.add("%$it%")
                }
                
                filter.searchQuery?.let {
                    selectionCriteria.add("body LIKE ?")
                    selectionArgs.add("%$it%")
                }
                
                if (filter.startDate != null && filter.endDate != null) {
                    selectionCriteria.add("date >= ? AND date <= ?")
                    selectionArgs.add(filter.startDate.toString())
                    selectionArgs.add(filter.endDate.toString())
                } else if (filter.startDate != null) {
                    selectionCriteria.add("date >= ?")
                    selectionArgs.add(filter.startDate.toString())
                } else if (filter.endDate != null) {
                    selectionCriteria.add("date <= ?")
                    selectionArgs.add(filter.endDate.toString())
                }
                
                filter.threadId?.let {
                    selectionCriteria.add("thread_id = ?")
                    selectionArgs.add(it)
                }
                
                if (filter.unreadOnly) {
                    selectionCriteria.add("read = 0")
                }
                
                // Create selection string
                val selection = if (selectionCriteria.isNotEmpty()) {
                    selectionCriteria.joinToString(" AND ")
                } else {
                    null
                }
                
                // Create selection args array
                val args = if (selectionArgs.isNotEmpty()) {
                    selectionArgs.toTypedArray()
                } else {
                    null
                }
                
                // Add limit if specified
                val limit = filter.limit?.toString()
                
                val messages = querySmsMessages(selection, args, limit)
                callback.onSuccess(messages)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching SMS messages", e)
                callback.onError(e)
            }
        }.start()
    }
    
    /**
     * Export SMS messages to a CSV file
     * @param messages List of messages to export
     * @param file File to export to
     * @param includeHeaders Whether to include CSV headers
     * @return Boolean indicating success
     */
    suspend fun exportToCsv(
        messages: List<SmsMessage>,
        file: File,
        includeHeaders: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            file.printWriter().use { writer ->
                // Write headers
                if (includeHeaders) {
                    writer.println("id,thread_id,address,body,date,date_formatted,type,type_name,read,seen,service_center")
                }
                
                // Write data
                messages.forEach { message ->
                    val formattedDate = message.getFormattedDate()
                    val escapedBody = message.body.replace("\"", "\"\"")
                    
                    writer.println(
                        "${message.id}," +
                            "${message.threadId}," +
                            "${message.address}," +
                            "\"${escapedBody}\"," +
                            "${message.date}," +
                            "${formattedDate}," +
                            "${message.type}," +
                            "${message.getMessageTypeName()}," +
                            "${if (message.read) 1 else 0}," +
                            "${if (message.seen) 1 else 0}," +
                            "${message.serviceCenterAddress ?: ""}"
                    )
                }
            }
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting SMS messages to CSV", e)
            return@withContext false
        }
    }
    
    /**
     * Export SMS messages to JSON file
     * @param messages List of messages to export
     * @param file File to export to
     * @return Boolean indicating success
     */
    suspend fun exportToJson(messages: List<SmsMessage>, file: File): Boolean =
        withContext(Dispatchers.IO) {
            try {
                file.printWriter().use { writer ->
                    writer.print("[")
                    messages.forEachIndexed { index, message ->
                        val escapedBody = message.body.replace("\"", "\\\"")
                            .replace("\n", "\\n")
                            .replace("\r", "\\r")
                            .replace("\t", "\\t")
                        
                        writer.print("""
                            {
                                "id": "${message.id}",
                                "thread_id": "${message.threadId}",
                                "address": "${message.address}",
                                "body": "${escapedBody}",
                                "date": ${message.date},
                                "date_formatted": "${message.getFormattedDate()}",
                                "type": ${message.type},
                                "type_name": "${message.getMessageTypeName()}",
                                "read": ${message.read},
                                "seen": ${message.seen},
                                "service_center": "${message.serviceCenterAddress ?: ""}"
                            }
                        """.trimIndent())
                        
                        if (index < messages.size - 1) {
                            writer.print(",")
                        }
                    }
                    writer.print("]")
                }
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting SMS messages to JSON", e)
                return@withContext false
            }
        }
    
    /**
     * Log SMS messages to console
     * @param messages List of messages to log
     * @param tag Log tag to use
     */
    fun logMessagesToConsole(messages: List<SmsMessage>, tag: String = TAG) {
        Log.i(tag, "====== START OF SMS DUMP ======")
        Log.i(tag, "Total SMS count: ${messages.size}")
        
        // Count by type
        val typeCount = messages.groupBy { it.type }
        typeCount.forEach { (type, msgs) ->
            val typeName = when (type) {
                Telephony.Sms.MESSAGE_TYPE_INBOX -> "Inbox"
                Telephony.Sms.MESSAGE_TYPE_SENT -> "Sent"
                Telephony.Sms.MESSAGE_TYPE_DRAFT -> "Draft"
                Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "Outbox"
                Telephony.Sms.MESSAGE_TYPE_FAILED -> "Failed"
                Telephony.Sms.MESSAGE_TYPE_QUEUED -> "Queued"
                else -> "Unknown"
            }
            Log.i(tag, "$typeName messages: ${msgs.size}")
        }
        
        Log.i(tag, "\n===== DETAILED MESSAGE LOG =====")
        messages.forEachIndexed { index, message ->
            Log.i(tag, "---------- Message #${index + 1} ----------")
            Log.i(tag, "ID: ${message.id}")
            Log.i(tag, "Thread ID: ${message.threadId}")
            Log.i(tag, "Address: ${message.address}")
            Log.i(tag, "Type: ${message.getMessageTypeName()} (${message.type})")
            Log.i(tag, "Date: ${message.getFormattedDate()}")
            Log.i(tag, "Read: ${message.read}")
            Log.i(tag, "Body: ${message.body}")
            if (message.serviceCenterAddress != null) {
                Log.i(tag, "Service Center: ${message.serviceCenterAddress}")
            }
        }
        
        Log.i(tag, "====== END OF SMS DUMP ======")
    }
    
    /**
     * Get conversation threads
     * @return List of thread IDs and summary information
     */
    suspend fun getConversationThreads(): List<SmsThread> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) {
            Log.e(TAG, "SMS permission not granted")
            return@withContext emptyList<SmsThread>()
        }
        
        val threads = mutableListOf<SmsThread>()
        val threadMap = mutableMapOf<String, MutableList<SmsMessage>>()
        
        // Get all messages
        val allMessages = querySmsMessages()
        
        // Group by thread ID
        allMessages.forEach { message ->
            val threadMessages = threadMap.getOrPut(message.threadId) { mutableListOf() }
            threadMessages.add(message)
        }
        
        // Create thread objects
        threadMap.forEach { (threadId, messages) ->
            val sortedMessages = messages.sortedByDescending { it.date }
            val latestMessage = sortedMessages.firstOrNull()
            val address = latestMessage?.address ?: "Unknown"
            
            val thread = SmsThread(
                threadId = threadId,
                address = address,
                messageCount = messages.size,
                unreadCount = messages.count { !it.read },
                lastMessageDate = latestMessage?.date ?: 0L,
                lastMessageBody = latestMessage?.body ?: "",
                lastMessageType = latestMessage?.type ?: 0
            )
            
            threads.add(thread)
        }
        
        return@withContext threads.sortedByDescending { it.lastMessageDate }
    }
    
    /**
     * Data class to hold conversation thread information
     */
    data class SmsThread(
        val threadId: String,
        val address: String,
        val messageCount: Int,
        val unreadCount: Int,
        val lastMessageDate: Long,
        val lastMessageBody: String,
        val lastMessageType: Int
    ) {
        /**
         * Get a formatted date string for the last message
         * @param pattern Date format pattern
         * @return Formatted date string
         */
        fun getFormattedLastMessageDate(pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = lastMessageDate
            val sdf = SimpleDateFormat(pattern, Locale.getDefault())
            return sdf.format(calendar.time)
        }
        
        /**
         * Get the human-readable message type for the last message
         * @return String representing the message type
         */
        fun getLastMessageTypeName(): String {
            return when (lastMessageType) {
                Telephony.Sms.MESSAGE_TYPE_INBOX -> "Inbox"
                Telephony.Sms.MESSAGE_TYPE_SENT -> "Sent"
                Telephony.Sms.MESSAGE_TYPE_DRAFT -> "Draft"
                Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "Outbox"
                Telephony.Sms.MESSAGE_TYPE_FAILED -> "Failed"
                Telephony.Sms.MESSAGE_TYPE_QUEUED -> "Queued"
                else -> "Unknown"
            }
        }
    }
    
    /**
     * Get SMS statistics
     * @return SmsStats object with statistics
     */
    suspend fun getStatistics(): SmsStats = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) {
            Log.e(TAG, "SMS permission not granted")
            return@withContext SmsStats()
        }
        
        val allMessages = querySmsMessages()
        
        val totalCount = allMessages.size
        val inboxCount = allMessages.count { it.type == Telephony.Sms.MESSAGE_TYPE_INBOX }
        val sentCount = allMessages.count { it.type == Telephony.Sms.MESSAGE_TYPE_SENT }
        val draftCount = allMessages.count { it.type == Telephony.Sms.MESSAGE_TYPE_DRAFT }
        val failedCount = allMessages.count { it.type == Telephony.Sms.MESSAGE_TYPE_FAILED }
        val unreadCount = allMessages.count { !it.read }
        
        // Calculate date ranges
        val dates = allMessages.map { it.date }
        val oldestDate = dates.minOrNull() ?: 0L
        val newestDate = dates.maxOrNull() ?: 0L
        
        // Count by contacts
        val contactCounts = allMessages.groupBy { it.address }.mapValues { it.value.size }
        val topContacts = contactCounts.entries.sortedByDescending { it.value }
            .take(10)
            .map { it.key to it.value }
            .toMap()
        
        // Messages per day
        val calendar = Calendar.getInstance()
        val today = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val thirtyDaysAgo = calendar.timeInMillis
        
        val messagesLast30Days = allMessages.count { it.date in thirtyDaysAgo..today }
        val averagePerDay = if (messagesLast30Days > 0) messagesLast30Days / 30.0 else 0.0
        
        return@withContext SmsStats(
            totalMessages = totalCount,
            inboxMessages = inboxCount,
            sentMessages = sentCount,
            draftMessages = draftCount,
            failedMessages = failedCount,
            unreadMessages = unreadCount,
            oldestMessageDate = oldestDate,
            newestMessageDate = newestDate,
            topContacts = topContacts,
            messagesLast30Days = messagesLast30Days,
            averageMessagesPerDay = averagePerDay
        )
    }
    
    /**
     * Data class to hold SMS statistics
     */
    data class SmsStats(
        val totalMessages: Int = 0,
        val inboxMessages: Int = 0,
        val sentMessages: Int = 0,
        val draftMessages: Int = 0,
        val failedMessages: Int = 0,
        val unreadMessages: Int = 0,
        val oldestMessageDate: Long = 0L,
        val newestMessageDate: Long = 0L,
        val topContacts: Map<String, Int> = emptyMap(),
        val messagesLast30Days: Int = 0,
        val averageMessagesPerDay: Double = 0.0
    ) {
        /**
         * Get a formatted date string for the oldest message
         * @param pattern Date format pattern
         * @return Formatted date string
         */
        fun getFormattedOldestDate(pattern: String = "yyyy-MM-dd"): String {
            if (oldestMessageDate == 0L) return "N/A"
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = oldestMessageDate
            val sdf = SimpleDateFormat(pattern, Locale.getDefault())
            return sdf.format(calendar.time)
        }
        
        /**
         * Get a formatted date string for the newest message
         * @param pattern Date format pattern
         * @return Formatted date string
         */
        fun getFormattedNewestDate(pattern: String = "yyyy-MM-dd"): String {
            if (newestMessageDate == 0L) return "N/A"
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = newestMessageDate
            val sdf = SimpleDateFormat(pattern, Locale.getDefault())
            return sdf.format(calendar.time)
        }
    }
    
    /**
     * Internal function to query SMS content provider
     * @param selection SQL WHERE clause
     * @param selectionArgs SQL WHERE clause arguments
     * @param limit Maximum number of results
     * @return List of SmsMessage objects
     */
    private fun querySmsMessages(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        limit: String? = null
    ): List<SmsMessage> {
        val smsList = mutableListOf<SmsMessage>()
        
        val sortOrder = "date DESC"
        
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms"),
            null,
            selection,
            selectionArgs,
            if (limit != null) "$sortOrder LIMIT $limit" else sortOrder
        )
        
        cursor?.use { c ->
            val indexId = c.getColumnIndexOrThrow("_id")
            val indexThreadId = c.getColumnIndexOrThrow("thread_id")
            val indexAddress = c.getColumnIndexOrThrow("address")
            val indexBody = c.getColumnIndexOrThrow("body")
            val indexDate = c.getColumnIndexOrThrow("date")
            val indexType = c.getColumnIndexOrThrow("type")
            val indexRead = c.getColumnIndexOrThrow("read")
            val indexSeen = c.getColumnIndex("seen")
            val indexServiceCenter = c.getColumnIndex("service_center")
            val indexProtocol = c.getColumnIndex("protocol")
            
            while (c.moveToNext()) {
                val id = c.getString(indexId)
                val threadId = c.getString(indexThreadId)
                val address = c.getString(indexAddress)
                val body = c.getString(indexBody)
                val date = c.getLong(indexDate)
                val type = c.getInt(indexType)
                val read = c.getInt(indexRead) == 1
                val seen = if (indexSeen != -1) c.getInt(indexSeen) == 1 else true
                val serviceCenter = if (indexServiceCenter != -1) c.getString(indexServiceCenter) else null
                val protocol = if (indexProtocol != -1) c.getInt(indexProtocol) else null
                
                val message = SmsMessage(
                    id = id,
                    threadId = threadId,
                    address = address,
                    body = body,
                    date = date,
                    type = type,
                    read = read,
                    seen = seen,
                    serviceCenterAddress = serviceCenter,
                    protocol = protocol
                )
                
                smsList.add(message)
            }
        }
        
        return smsList
    }
}