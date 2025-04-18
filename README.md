# SMS Logger Library

An Android library for accessing, analyzing, and exporting SMS messages.

## Features

- ✅ Easy access to SMS messages in Android apps
- ✅ Powerful filtering options (by date, type, content, etc.)
- ✅ Asynchronous and coroutine-based APIs
- ✅ Export to CSV and JSON formats
- ✅ SMS statistics and analytics
- ✅ Conversation thread support
- ✅ Detailed message metadata access
- ✅ Proper permission handling

## Installation

### Gradle

Add the Maven repository to your project's `settings.gradle` or root `build.gradle` file:

```groovy
repositories {
    mavenCentral()
    // or if using JitPack
    maven { url 'https://jitpack.io' }
}
```

Add the dependency to your app's `build.gradle` file:

```groovy
dependencies {
    implementation 'com.yourcompany.smslogger:sms-logger:1.0.0'
    // or if using JitPack
    implementation 'com.github.yourcompany:sms-logger:1.0.0'
}
```

## Permissions

Your app must request the following permissions in AndroidManifest.xml:

```xml
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" /> <!-- Optional for real-time monitoring -->
```

For Android 6.0 (API level 23) and above, you must also request runtime permissions.

## Usage

### Initialization

Initialize the SmsLogger instance in your Application or Activity:

```kotlin
// Get singleton instance
val smsLogger = SmsLogger.getInstance(context)
```

### Permission Check

Always check for permissions before accessing SMS data:

```kotlin
if (SmsLogger.hasPermission(context)) {
    // Access SMS data
} else {
    // Request permissions
    requestPermissions(...)
}
```

### Getting SMS Messages

#### Using Coroutines

```kotlin
lifecycleScope.launch {
    // Get all messages
    val allMessages = smsLogger.getAllMessages()
    
    // Apply filters
    val filter = SmsLogger.SmsFilter(
        type = Telephony.Sms.MESSAGE_TYPE_INBOX,
        startDate = calendar.timeInMillis,
        searchQuery = "hello"
    )
    val filteredMessages = smsLogger.getMessages(filter)
}
```

#### Using Callbacks

```kotlin
smsLogger.getMessagesAsync(object : SmsLogger.SmsCallback {
    override fun onSuccess(messages: List<SmsLogger.SmsMessage>) {
        // Process messages
    }
    
    override fun onError(error: Exception) {
        // Handle error
    }
})
```

### Filtering Options

The `SmsFilter` class provides powerful filtering capabilities:

```kotlin
val filter = SmsLogger.SmsFilter(
    type = Telephony.Sms.MESSAGE_TYPE_INBOX, // Message type (inbox, sent, etc.)
    address = "+1234567890",                 // Phone number
    searchQuery = "hello",                   // Text to search for
    startDate = startTimestamp,              // Date range start
    endDate = endTimestamp,                  // Date range end
    threadId = "123",                        // Conversation thread ID
    unreadOnly = true,                       // Only unread messages
    limit = 50                               // Limit results
)
```

### Exporting Data

```kotlin
// Export to CSV
val exportFile = File(context.getExternalFilesDir(null), "sms_export.csv")
val success = smsLogger.exportToCsv(messages, exportFile)

// Export to JSON
val jsonFile = File(context.getExternalFilesDir(null), "sms_export.json")
val success = smsLogger.exportToJson(messages, jsonFile)
```

### SMS Statistics

```kotlin
val stats = smsLogger.getStatistics()
println("Total messages: ${stats.totalMessages}")
println("Inbox messages: ${stats.inboxMessages}")
println("Sent messages: ${stats.sentMessages}")
println("Top contact: ${stats.topContacts.entries.firstOrNull()}")
```

### Conversation Threads

```kotlin
val threads = smsLogger.getConversationThreads()
threads.forEach { thread ->
    println("Thread ID: ${thread.threadId}")
    println("Contact: ${thread.address}")
    println("Messages: ${thread.messageCount}")
    println("Last message: ${thread.lastMessageBody}")
}
```

### Logging to Console

```kotlin
// Log all messages to the Android console (Logcat)
smsLogger.logMessagesToConsole(messages, "MY_TAG")
```

## SmsMessage Object

The `SmsMessage` class provides access to the following properties:

- `id`: Unique message ID
- `threadId`: Conversation thread ID
- `address`: Phone number or sender ID
- `body`: Message text content
- `date`: Timestamp in milliseconds
- `type`: Message type (inbox, sent, draft, etc.)
- `read`: Whether the message has been read
- `seen`: Whether the message has been seen
- `serviceCenterAddress`: SMSC address
- `protocol`: Protocol identifier

Helper methods:
- `getFormattedDate()`: Format the timestamp
- `getMessageTypeName()`: Get human-readable type name

## Google Play Restrictions

Note that Google Play has restrictions on apps that request SMS permissions. Your app may need to be submitted for a review to explain why it needs SMS access. 

## License

This library is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
