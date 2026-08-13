package com.shejera.services

import com.shejera.config.SmtpConfig
import org.slf4j.LoggerFactory
import java.util.Properties
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage

/** Optional SMTP mailer (SHEJERA_SMTP_* env / shejera.smtp). */
class EmailService(
    private val smtp: SmtpConfig,
) {
    private val log = LoggerFactory.getLogger(EmailService::class.java)

    val configured: Boolean
        get() = smtp.configured

    /** After public “Davet iste”: confirm the request was received (no invite link yet). */
    fun sendInviteRequestReceivedEmail(
        toEmail: String,
        displayName: String,
    ): Boolean =
        send(
            toEmail = toEmail,
            subject = "Shejera — davetiye isteğin alındı",
            body =
                buildString {
                    append("Merhaba")
                    if (displayName.isNotBlank()) append(" $displayName")
                    appendLine(",")
                    appendLine()
                    appendLine("Davetiye isteğin alındı.")
                    appendLine(
                        "Bir yönetici onayladıktan sonra sana ayrı bir e-posta ile katkı bağlantısı gelecek.",
                    )
                    appendLine()
                    appendLine("— Shejera")
                },
            logLabel = "invite-request-received",
        )

    /** After admin approves a request: invite link for /contrib. */
    fun sendInviteApprovedEmail(
        toEmail: String,
        displayName: String,
        inviteUrl: String,
    ): Boolean =
        send(
            toEmail = toEmail,
            subject = "Shejera — davetin onaylandı",
            body =
                buildString {
                    append("Merhaba")
                    if (displayName.isNotBlank()) append(" $displayName")
                    appendLine(",")
                    appendLine()
                    appendLine("Davetiye isteğin onaylandı.")
                    appendLine("Aşağıdaki bağlantı ile katkı sayfasına girebilirsin:")
                    appendLine()
                    appendLine(inviteUrl)
                    appendLine()
                    appendLine("Bu bağlantı kişiseldir; paylaşma.")
                    appendLine()
                    appendLine("— Shejera")
                },
            logLabel = "invite-approved",
        )

    private fun send(
        toEmail: String,
        subject: String,
        body: String,
        logLabel: String,
    ): Boolean {
        val smtpHost = smtp.host
        val fromAddress = smtp.fromAddress
        if (!smtp.configured || smtpHost == null || fromAddress == null) {
            log.warn(
                "SMTP not configured; {} email not sent to {}",
                logLabel,
                toEmail,
            )
            return false
        }

        val props =
            Properties().apply {
                put("mail.smtp.host", smtpHost)
                put("mail.smtp.port", smtp.port.toString())
                put("mail.smtp.auth", if (smtp.user != null) "true" else "false")
                put("mail.smtp.starttls.enable", smtp.startTls.toString())
                put("mail.smtp.connectiontimeout", "10000")
                put("mail.smtp.timeout", "10000")
            }

        val session =
            if (smtp.user != null) {
                Session.getInstance(
                    props,
                    object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication =
                            PasswordAuthentication(smtp.user, smtp.password)
                    },
                )
            } else {
                Session.getInstance(props)
            }

        val message =
            MimeMessage(session).apply {
                setFrom(InternetAddress(fromAddress))
                setRecipient(Message.RecipientType.TO, InternetAddress(toEmail))
                this.subject = subject
                setText(body, "UTF-8")
            }

        return try {
            Transport.send(message)
            log.info("{} email sent to {}", logLabel, toEmail)
            true
        } catch (e: Exception) {
            log.error("Failed to send {} email to {}: {}", logLabel, toEmail, e.message)
            false
        }
    }
}
