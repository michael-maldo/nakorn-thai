package au.com.nakornthai.notification.orderconfirmation;
import au.com.nakornthai.notification.domain.NotificationType;
import java.time.*;
import java.util.UUID;
public record SendOrderConfirmationCommand(UUID orderId,NotificationType type,String verifiedPhone,String email,String customerName,long totalMinor,Instant estimatedReadyAt,ZoneId timezone) {}
