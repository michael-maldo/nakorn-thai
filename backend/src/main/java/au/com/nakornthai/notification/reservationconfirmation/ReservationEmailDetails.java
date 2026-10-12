package au.com.nakornthai.notification.reservationconfirmation;

import au.com.nakornthai.reservation.infrastructure.ReservationJpaEntity;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Render once at enqueue time; delivery never reloads mutable reservation data. */
public final class ReservationEmailDetails {
 private ReservationEmailDetails() {}
 public static String reference(UUID id){return id.toString().substring(0,8).toUpperCase(Locale.ROOT);}
 public static String time(SendReservationConfirmationCommand c){
  return c.requestedAt().format(DateTimeFormatter.ofPattern("EEEE d MMM uuuu 'at' h:mm a",Locale.forLanguageTag("en-AU")))+" ("+c.timezone().getId()+")";
 }
 public static String event(SendReservationConfirmationCommand c){return switch(c.type()) {
  case RESERVATION_RECEIVED -> "We received your booking request. Your table is not confirmed yet.";
  case RESERVATION_CONFIRMED -> "Your booking is confirmed.";
  case RESERVATION_DECLINED -> "Unfortunately, your booking request was declined. Please contact the restaurant to discuss alternatives.";
  case RESERVATION_CANCELLED -> "Your booking was cancelled.";
  default -> throw new IllegalArgumentException("Unsupported reservation notification");
 };}
 public static String subject(SendReservationConfirmationCommand c,boolean staff){
  if(staff)return "Nakorn Thai — New table request #"+reference(c.reservationId());
  String label=switch(c.type()){case RESERVATION_RECEIVED->"Request received";case RESERVATION_CONFIRMED->"Confirmed";case RESERVATION_DECLINED->"Declined";case RESERVATION_CANCELLED->"Cancelled";default->throw new IllegalArgumentException("Unsupported reservation notification");};
  return "Nakorn Thai — Reservation #"+reference(c.reservationId())+" — "+label;
 }
 public static String customer(SendReservationConfirmationCommand c){
  return "Hello "+c.customerName()+",\n\n"+event(c)+"\n\n"+details(c)+"\nPlease contact the restaurant if you need help with this reservation.\n\nNakorn Thai\n";
 }
 private static String details(SendReservationConfirmationCommand c){return "Reservation reference: "+reference(c.reservationId())+"\nRequested time: "+time(c)+"\nParty size: "+c.partySize()+" guests\n";}
 public static String staff(SendReservationConfirmationCommand c,ReservationJpaEntity r,String url){
  var text=new StringBuilder("New table request\n\nStatus: REQUESTED — not yet confirmed.\nReview availability before confirming this request.\n\n").append(details(c)).append("Customer: ").append(r.getCustomerName()).append('\n');
  append(text,"Phone",r.getPhone());append(text,"Email",r.getEmail());
  if(r.getNotes()!=null&&!r.getNotes().isBlank())text.append("\nCustomer reservation notes:\n").append(r.getNotes()).append('\n');
  return text.append("\nNakorn Thai\n\nStaff sign-in required.\nView reservations\n").append(url).append('\n').toString();
 }
 private static void append(StringBuilder text,String label,String value){if(value!=null&&!value.isBlank())text.append(label).append(": ").append(value).append('\n');}
}
