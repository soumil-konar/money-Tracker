package com.soumil.moneytracker.email

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
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
        private const val SOCKET_TIMEOUT_MS = 30000

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

        private val BANK_HEADER_KEYWORDS = listOf(
            "hdfc", "icici", "sbi", "axis", "kotak", "indusind", "pnb", "baroda",
            "cred", "paytm", "razorpay", "billdesk", "bank", "alert", "upi", "credit card",
            "debit", "credited", "debited", "spent", "statement", "vpa",
        )
    }

    fun sanitizeEmail(email: String): String {
        val trimmed = email.trim()
        return if (trimmed.isNotBlank() && !trimmed.contains("@")) {
            "$trimmed@gmail.com"
        } else {
            trimmed
        }
    }

    fun sanitizeAppPassword(password: String): String {
        return password.filter { it.isLetter() }.lowercase()
    }

    fun parseImapError(rawResponse: String): String {
        val lower = rawResponse.lowercase()
        return when {
            "authenticationfailed" in lower || "invalid credentials" in lower -> {
                "Authentication failed. Ensure you are using a 16-character Google App Password (not your standard Gmail password) and that IMAP is enabled in your Gmail settings (Settings > Forwarding and POP/IMAP > Enable IMAP)."
            }
            "application-specific password required" in lower || "app password" in lower -> {
                "Google requires an App Password. Go to Google Account > Security > 2-Step Verification > App Passwords, create an App Password for 'Mail', and paste the 16 characters here."
            }
            "unknown command" in lower -> {
                "Gmail rejected the command. Please check your network and re-authenticate."
            }
            else -> {
                "Gmail authentication failed: ${rawResponse.removePrefix("A01 NO").removePrefix("T01 NO").trim()}"
            }
        }
    }

    suspend fun testCredentials(email: String, appPassword: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanEmail = sanitizeEmail(email)
        val cleanPassword = sanitizeAppPassword(appPassword)

        if (cleanEmail.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Gmail address cannot be blank."))
        }
        if (cleanPassword.length != 16) {
            return@withContext Result.failure(
                IllegalArgumentException("Google App Password must be exactly 16 letters (found ${cleanPassword.length}). Please generate an App Password in your Google Account settings."),
            )
        }

        try {
            val socket = createTlsSocket()
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))

                // 1. Read server greeting
                reader.readLine()

                // 2. Execute login with strict CRLF
                sendCommand(writer, "T01", "LOGIN \"$cleanEmail\" \"$cleanPassword\"")
                val response = readUntilTag(reader, "T01")

                if (response.startsWith("T01 OK", ignoreCase = true)) {
                    sendCommand(writer, "T02", "LOGOUT")
                    readUntilTag(reader, "T02")
                    Result.success(true)
                } else {
                    Result.failure(IllegalStateException(parseImapError(response)))
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
        val cleanEmail = sanitizeEmail(email)
        val cleanPassword = sanitizeAppPassword(appPassword)

        if (cleanEmail.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Gmail address is not configured."))
        }
        if (cleanPassword.length != 16) {
            return@withContext Result.failure(
                IllegalArgumentException("Google App Password must be exactly 16 letters (found ${cleanPassword.length})."),
            )
        }

        val messages = mutableListOf<EmailTransactionMessage>()

        try {
            val socket = createTlsSocket()
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))

                // 1. Read greeting
                reader.readLine()

                // 2. Login with strict CRLF
                sendCommand(writer, "A01", "LOGIN \"$cleanEmail\" \"$cleanPassword\"")
                val loginResponse = readUntilTag(reader, "A01")
                if (!loginResponse.startsWith("A01 OK", ignoreCase = true)) {
                    return@withContext Result.failure(IllegalStateException(parseImapError(loginResponse)))
                }

                // 3. Select Inbox and read message count
                var totalMessages = 0
                sendCommand(writer, "A02", "SELECT INBOX")
                readUntilTag(reader, "A02") { line ->
                    val existsMatch = Regex("""\* (\d+) EXISTS""").find(line)
                    if (existsMatch != null) {
                        totalMessages = existsMatch.groupValues[1].toIntOrNull() ?: 0
                    }
                }

                // 4. Fast search strategy:
                // Primary: Use Gmail's native indexed search (X-GM-RAW) for near-instant search across the last 45 days
                val messageIds = mutableSetOf<Int>()
                val rawQuery = "from:(hdfc OR icici OR sbi OR axis OR kotak OR indusind OR pnb OR baroda OR cred OR paytm OR alert OR upi) newer_than:45d"
                sendCommand(writer, "A03", "SEARCH X-GM-RAW \"$rawQuery\"")
                readUntilTag(reader, "A03") { line ->
                    if (line.startsWith("* SEARCH", ignoreCase = true)) {
                        val ids = line.removePrefix("* SEARCH").trim()
                            .split(" ")
                            .mapNotNull { it.trim().toIntOrNull() }
                        messageIds.addAll(ids)
                    }
                }

                // Fallback: If X-GM-RAW returned no IDs, inspect the latest 50 message headers from the inbox sequence
                if (messageIds.isEmpty() && totalMessages > 0) {
                    val startSeq = maxOf(1, totalMessages - 49)
                    if (startSeq <= totalMessages) {
                        val candidateIds = mutableSetOf<Int>()
                        var currentMsgSeq = 0

                        sendCommand(writer, "A04", "FETCH $startSeq:$totalMessages (BODY.PEEK[HEADER.FIELDS (FROM SUBJECT)])")
                        readUntilTag(reader, "A04") { line ->
                            val fetchMatch = Regex("""\* (\d+) FETCH""").find(line)
                            if (fetchMatch != null) {
                                currentMsgSeq = fetchMatch.groupValues[1].toIntOrNull() ?: 0
                            } else if (currentMsgSeq > 0 && isBankRelatedHeader(line)) {
                                candidateIds.add(currentMsgSeq)
                            }
                        }
                        messageIds.addAll(candidateIds)
                    }
                }

                // 5. Fetch message details for the newest IDs
                val sortedIds = messageIds.sortedDescending().take(maxMessages)

                for ((fetchIdx, msgId) in sortedIds.withIndex()) {
                    val tag = "F$fetchIdx"
                    sendCommand(writer, tag, "FETCH $msgId (BODY.PEEK[HEADER.FIELDS (FROM SUBJECT DATE)] BODY.PEEK[TEXT]<0.4096>)")

                    val headerLines = mutableListOf<String>()
                    val bodyLines = mutableListOf<String>()
                    var inHeader = true

                    readUntilTag(reader, tag) { line ->
                        if (line.startsWith("* $msgId FETCH")) {
                            inHeader = true
                        } else if (inHeader) {
                            if (line.isBlank() || line.startsWith(")") || line.contains("BODY[TEXT]")) {
                                inHeader = false
                            } else {
                                headerLines.add(line)
                            }
                        } else if (!line.startsWith(")") && !line.startsWith("$tag ")) {
                            bodyLines.add(line)
                        }
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

                // 6. Logout cleanly
                sendCommand(writer, "A99", "LOGOUT")
                readUntilTag(reader, "A99")

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
        val socket = factory.createSocket() as SSLSocket
        socket.connect(InetSocketAddress(IMAP_HOST, IMAP_PORT), 15000)
        socket.soTimeout = SOCKET_TIMEOUT_MS
        socket.startHandshake()
        return socket
    }

    private fun sendCommand(writer: BufferedWriter, tag: String, command: String) {
        writer.write("$tag $command\r\n")
        writer.flush()
    }

    private fun readUntilTag(
        reader: BufferedReader,
        tag: String,
        onLine: (String) -> Unit = {},
    ): String {
        val tagPrefix = "$tag "
        while (true) {
            val line = reader.readLine() ?: break
            onLine(line)
            if (line.startsWith(tagPrefix, ignoreCase = true)) {
                return line
            }
        }
        return ""
    }

    private fun isBankRelatedHeader(line: String): Boolean {
        val lower = line.lowercase()
        return BANK_HEADER_KEYWORDS.any { lower.contains(it) }
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
                .replace("=20", " ")
                .replace("=3D", "=")
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
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
                .replace("&#8377;", "INR ")
                .replace("&#x20B9;", "INR ")
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
