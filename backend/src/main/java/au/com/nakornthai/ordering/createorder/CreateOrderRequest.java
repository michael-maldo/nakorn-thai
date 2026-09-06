package au.com.nakornthai.ordering.createorder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
public record CreateOrderRequest(
    @NotNull UUID requestId,
    @NotNull @Pattern(regexp="[a-f0-9]{64}") String trackingToken,
    @NotBlank @Size(max=100) String customerName,
    @NotBlank @Pattern(regexp="[+0-9() .-]{6,30}") String phone,
    @NotNull @Size(max=1000) String notes,
    @NotEmpty @Size(max=30) List<@Valid @NotNull Line> items, @Email @Size(max=254) String email, @Pattern(regexp="PAY_AT_RESTAURANT|PAYPAL|PAYID") String paymentMethod) {
    public CreateOrderRequest(UUID requestId,String trackingToken,String customerName,String phone,String notes,List<Line> items) { this(requestId,trackingToken,customerName,phone,notes,items,null,null); }
    public record Line(@NotNull UUID variationId, @Min(1) @Max(20) int quantity,
                       @Min(0) long expectedUnitPriceMinor) {}
}
