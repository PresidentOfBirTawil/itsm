package com.itsm.notification;

/**
 * One message for one person, ready to be sent through any channel. It holds
 * plain values (ids, names, text) rather than entities, so a channel can send
 * it later and in its own database transaction.
 */
public record NotificationMessage(NotificationType type, Long recipientId, String recipientName,
                                  String recipientEmail, Long incidentId, String incidentCode,
                                  String title, String body) {
}
