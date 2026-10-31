package com.dodocomm.app.core.channel

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small SMTP client for the Email channel. Supports implicit TLS or required
 * STARTTLS, AUTH PLAIN/LOGIN and a UTF-8 plain-text message. Replaces the
 * unmaintained JavaMail Android port and keeps the same channel contract.
 */
class SmtpClient {

    suspend fun send(
        host: String,
        port: Int,
        security: String,
        username: String,
        password: String,
        recipient: String,
        subject: String,
        body: String,
    ): Unit = withContext(Dispatchers.IO) {
        val safeHost = host.trim()
        val safeUser = username.trim()
        require(safeHost.isNotBlank()) { "SMTP 服务器未填写" }
        require(safeUser.isNotBlank() && password.isNotBlank()) { "SMTP 账号或授权码未填写" }
        require(SmtpMessage.isValidEmail(safeUser)) { "发件邮箱格式不正确" }
        val recipients = SmtpMessage.splitRecipients(recipient)
        require(recipients.isNotEmpty()) { "没有收件人" }
        require(recipients.all(SmtpMessage::isValidEmail)) { "收件人邮箱格式不正确" }

        val implicitTls = security.uppercase() in setOf("SSL", "SMTPS")
        var session = Session.open(safeHost, port, implicitTls)
        try {
            session.expect(setOf(220))
            session.sendLine("EHLO ${ehloName()}")
            var ehlo = session.expect(setOf(250))

            if (!implicitTls) {
                session.sendLine("STARTTLS")
                session.expect(setOf(220))
                session = session.startTls()
                session.sendLine("EHLO ${ehloName()}")
                ehlo = session.expect(setOf(250))
            }

            authenticate(session, safeUser, password, ehlo)
            session.sendLine("MAIL FROM:<$safeUser>")
            session.expect(setOf(250))
            recipients.forEach { address ->
                session.sendLine("RCPT TO:<$address>")
                session.expect(setOf(250, 251))
            }
            session.sendLine("DATA")
            session.expect(setOf(354))
            val message = SmtpMessage.build(
                from = safeUser,
                recipients = recipients,
                subject = subject,
                body = body,
            )
            session.sendRaw(SmtpMessage.dotStuff(message))
            session.sendLine(".")
            session.expect(setOf(250))
            runCatching {
                session.sendLine("QUIT")
            }
        } finally {
            session.close()
        }
    }

    private fun authenticate(session: Session, username: String, password: String, ehlo: String) {
        val upper = ehlo.uppercase()
        val supportsPlain = upper.contains("AUTH") && upper.contains("PLAIN")
        val supportsLogin = upper.contains("AUTH") && upper.contains("LOGIN")

        if (supportsPlain || !supportsLogin) {
            val token = Base64.getEncoder().encodeToString(
                "\u0000$username\u0000$password".toByteArray(Charsets.UTF_8),
            )
            session.sendLine("AUTH PLAIN $token")
            val reply = session.readReply()
            val code = reply.take(3).toIntOrNull()
            if (code == 235) return
            if (code == 334) {
                session.sendLine(token)
                val second = session.expect(setOf(235))
                if (second.isNotBlank()) return
            }
            if (!supportsLogin) throw IOException("SMTP 认证失败：$reply")
        }

        session.sendLine("AUTH LOGIN")
        session.expect(setOf(334))
        session.sendLine(Base64.getEncoder().encodeToString(username.toByteArray(Charsets.UTF_8)))
        session.expect(setOf(334))
        session.sendLine(Base64.getEncoder().encodeToString(password.toByteArray(Charsets.UTF_8)))
        session.expect(setOf(235))
    }

    private class Session private constructor(
        private val socket: Socket,
        private val host: String,
        private val port: Int,
    ) {
        private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
        private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.ISO_8859_1))

        fun sendLine(line: String) {
            writer.write(line)
            writer.write("\r\n")
            writer.flush()
        }

        fun sendRaw(text: String) {
            writer.write(text)
            if (!text.endsWith("\r\n")) writer.write("\r\n")
            writer.flush()
        }

        fun readReply(): String {
            val lines = mutableListOf<String>()
            while (true) {
                val line = reader.readLine() ?: throw IOException("SMTP 服务器断开连接")
                lines += line
                if (line.length < 4 || line[3] != '-') break
                if (lines.size > 20) throw IOException("SMTP 响应过长")
            }
            return lines.joinToString("\n")
        }

        fun expect(allowed: Set<Int>): String {
            val reply = readReply()
            val code = reply.take(3).toIntOrNull() ?: throw IOException("SMTP 响应格式错误")
            if (code !in allowed) throw IOException("SMTP 服务器拒绝：$reply")
            return reply
        }

        fun startTls(): Session {
            val factory: SSLSocketFactory = SSLContext.getDefault().socketFactory
            val tls = factory.createSocket(socket, host, port, true) as SSLSocket
            val parameters = tls.sslParameters
            parameters.endpointIdentificationAlgorithm = "HTTPS"
            tls.sslParameters = parameters
            tls.useClientMode = true
            tls.soTimeout = READ_TIMEOUT_MS
            tls.startHandshake()
            return Session(tls, host, port)
        }

        fun close() {
            runCatching { socket.close() }
        }

        companion object {
            fun open(host: String, port: Int, implicitTls: Boolean): Session {
                require(port in 1..65535) { "SMTP 端口不合法" }
                val plain = Socket()
                plain.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                plain.soTimeout = READ_TIMEOUT_MS
                if (!implicitTls) return Session(plain, host, port)

                val factory: SSLSocketFactory = SSLContext.getDefault().socketFactory
                val tls = factory.createSocket(plain, host, port, true) as SSLSocket
                val parameters = tls.sslParameters
                parameters.endpointIdentificationAlgorithm = "HTTPS"
                tls.sslParameters = parameters
                tls.useClientMode = true
                tls.soTimeout = READ_TIMEOUT_MS
                tls.startHandshake()
                return Session(tls, host, port)
            }
        }
    }

    private fun ehloName(): String = runCatching {
        java.net.InetAddress.getLocalHost().hostName.substringBefore('.').takeIf { it.isNotBlank() } ?: "localhost"
    }.getOrDefault("localhost")

    private companion object {
        const val CONNECT_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 6_000
    }
}
