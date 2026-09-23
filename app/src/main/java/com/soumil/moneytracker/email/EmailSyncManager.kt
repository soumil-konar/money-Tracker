package com.soumil.moneytracker.email

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EmailTransactionMessage(
    val sender: String,
    val subject: String,
    val body: String,
    val timestampMillis: Long,
)

class EmailSyncManager {

    companion object {
        private const val IMAP_HOST = "imap.gmail.com"
        private const val IMAP_PORT = 993
        private const val SOCKET_TIMEOUT_MS = 15000

        val MONITORED_BANK_DOMAINS = listOf(
            "hdfcbank.net",
            "hdfcbank.com",
            "icicibank.com",
            "sbi.co.in",
            "sbicard.com",
            "axisbank.com",
            "kotak.com",
            "indusind.com",
            "bankofbaroda.co.in",
            "pnb.co.in",
            "cred.club",
            "paytm.com",
        )
    }

    suspend fun testCredentials(email: String, appPassword: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (email.isBlank() || appPassword.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Email and app password cannot be blank."))
        }

        try {
            val socket = createTlsSocket()
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                val writer = PrintWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8), true)

                // Read server greeting
                reader.readLine()

                // Execute login
                writer.println("T01 LOGIN \"$email\" \"$appPassword\"")
                var response = reader.readLine().orEmpty()
                while (!response.startsWith("T01 ") && response.isNotBlank()) {
                    response = reader.readLine().orEmpty()
                }

                if (response.startsWith("T01 OK", ignoreCase = true)) {
                    writer.println("T02 LOGOUT")
                    Result.success(true)
                } else {
                    Result.failure(IllegalStateException("Gmail authentication failed: $response"))
                }
            } finally {
                runCatching { socket.close() }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun fetchRecentBankAlerts(
        email: String,
        appPassword: String,
        maxMessages: Int = 30,
    ): Result<List<EmailTransactionMessage>> = withContext(Dispatchers.IO) {
        if (email.isBlank() || appPassword.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Email and app password are not configured."))
        }

        val messages = mutableListOf<EmailTransactionMessage>()

        try {
            val socket = createTlsSocket()
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                val writer = PrintWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8), true)

                // 1. Read greeting
                reader.readLine()

                // 2. Login
                writer.println("A01 LOGIN \"$email\" \"$appPassword\"")
                var line = reader.readLine().orEmpty()
                while (!line.startsWith("A01 ") && line.isNotBlank()) {
                    line = reader.readLine().orEmpty()
                }
                if (!line.startsWith("A01 OK", ignoreCase = true)) {
                    return@withContext Result.failure(IllegalStateException("Authentication failed."))
                }

                // 3. Select Inbox
                writer.println("A02 SELECT INBOX")
                line = reader.readLine().orEmpty()
                while (!line.startsWith("A02 ") && line.isNotBlank()) {
                    line = reader.readLine().orEmpty()
                }

                // 4. Search recent messages from bank senders
                val messageIds = mutableSetOf<Int>()

                for ((idx, domain) in MONITORED_BANK_DOMAINS.take(8).withIndex()) {
                    val tag = "S$idx"
                    writer.println("$tag SEARCH FROM \"$domain\"")
                    var searchResponse = reader.readLine().orEmpty()
                    while (!searchResponse.startsWith("$tag ") && searchResponse.isNotBlank()) {
                        if (searchResponse.startsWith("* SEARCH", ignoreCase = true)) {
                            val ids = searchResponse.removePrefix("* SEARCH").trim()
                                .split(" ")
                                .mapNotNull { it.trim().toIntOrNull() }
                            messageIds.addAll(ids.takeLast(10))
                        }
                        searchResponse = reader.readLine().orEmpty()
                    }
                }

                // If no domain-specific matches, fallback to recent UNSEEN
                if (messageIds.isEmpty()) {
                    writer.println("S99 SEARCH UNSEEN")
                    var searchResponse = reader.readLine().orEmpty()
                    while (!searchResponse.startsWith("S99 ") && searchResponse.isNotBlank()) {
                        if (searchResponse.startsWith("* SEARCH", ignoreCase = true)) {
                            val ids = searchResponse.removePrefix("* SEARCH").trim()
                                .split(" ")
                                .mapNotNull { it.trim().toIntOrNull() }
                            messageIds.addAll(ids.takeLast(maxMessages))
                        }
                        searchResponse = reader.readLine().orEmpty()
                    }
                }

                // 5. Fetch message details for newest IDs
                val sortedIds = messageIds.sortedDescending().take(maxMessages)

                for ((fetchIdx, msgId) in sortedIds.withIndex()) {
                    val tag = "F$fetchIdx"
                    writer.println("$tag FETCH $msgId (BODY.PEEK[HEADER.FIELDS (FROM SUBJECT DATE)] BODY.PEEK[TEXT]<0.4096>)")

                    val headerLines = mutableListOf<String>()
                    val bodyLines = mutableListOf<String>()
                    var readingBody = false

                    var fetchLine = reader.readLine()
                    while (fetchLine != null && !fetchLine.startsWith("$tag ")) {
                        if (fetchLine.startsWith("* $msgId FETCH")) {
                            readingBody = false
                        } else if (fetchLine.startsWith("From:", ignoreCase = true) ||
                            fetchLine.startsWith("Subject:", ignoreCase = true) ||
                            fetchLine.startsWith("Date:", ignoreCase = true)
                        ) {
                            headerLines.add(fetchLine)
                        } else if (fetchLine.startsWith(") ") || fetchLine == ")") {
                            readingBody = false
                        } else {
                            bodyLines.add(fetchLine)
                        }
                        fetchLine = reader.readLine()
                    }

                    val fromHeader = headerLines.firstOrNull { it.startsWith("From:", ignoreCase = true) }
                        ?.removePrefix("From:")?.removePrefix("from:")?.trim().orEmpty()
                    val subjectHeader = headerLines.firstOrNull { it.startsWith("Subject:", ignoreCase = true) }
                        ?.removePrefix("Subject:")?.removePrefix("subject:")?.trim().orEmpty()
                    val dateHeader = headerLines.firstOrNull { it.startsWith("Date:", ignoreCase = true) }
                        ?.removePrefix("Date:")?.removePrefix("date:")?.trim()

                    val rawBodyText = bodyLines.joinToString("\n").trim()
                    val cleanedBody = cleanEmailBody(rawBodyText)

                    val parsedDate = dateHeader?.let { parseEmailDate(it) } ?: System.currentTimeMillis()

                    if (cleanedBody.isNotBlank() || subjectHeader.isNotBlank()) {
                        messages.add(
                            EmailTransactionMessage(
                                sender = extractSenderName(fromHeader),
                                subject = subjectHeader,
                                body = "$subjectHeader\n$cleanedBody".trim(),
                                timestampMillis = parsedDate,
                            ),
                        )
                    }
                }

                // 6. Logout
                writer.println("A99 LOGOUT")

                Result.success(messages)
            } finally {
                runCatching { socket.close() }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun createTlsSocket(): SSLSocket {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val socket = factory.createSocket(IMAP_HOST, IMAP_PORT) as SSLSocket
        socket.soTimeout = SOCKET_TIMEOUT_MS
        socket.startHandshake()
        return socket
    }

    private fun extractSenderName(fromHeader: String): String {
        val clean = fromHeader.replace("\"", "").trim()
        val emailMatch = Regex("<([^>]+)>").find(clean)
        if (emailMatch != null) {
            val email = emailMatch.groupValues[1]
            val name = clean.substringBefore("<").trim()
            return if (name.isNotBlank()) name else email
        }
        return clean.ifBlank { "Bank Alert" }
    }

    private fun cleanEmailBody(raw: String): String {
        var text = raw
        // Handle basic quoted-printable
        if (text.contains("=")) {
            text = text.replace("=\r\n", "").replace("=\n", "")
        }
        // Strip HTML tags if email is HTML
        if (text.contains("<") && text.contains(">")) {
            text = text.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<[^>]+>"), " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&gt;", ">")
                .replace("&lt;", "<")
        }
        return text.lines().map(String::trim).filter(String::isNotEmpty).joinToString(" ")
    }

    private fun parseEmailDate(header: String): Long? {
        val formats = listOf(
            "EEE, d MMM yyyy HH:mm:ss Z",
            "d MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss",
        )
        for (format in formats) {
            val date = runCatching {
                SimpleDateFormat(format, Locale.ENGLISH).parse(header)
            }.getOrNull()
            if (date != null) return date.time
        }
        return null
    }
}
