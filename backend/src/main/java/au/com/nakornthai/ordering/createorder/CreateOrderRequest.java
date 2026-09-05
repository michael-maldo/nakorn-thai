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
    @NotEmpty @Size(max=30) List<@Valid @NotNull Line> items) {
    public record Line(@NotNull UUID variationId, @Min(1) @Max(20) int quantity,
                       @Min(0) long expectedUnitPriceMinor) {}
}
