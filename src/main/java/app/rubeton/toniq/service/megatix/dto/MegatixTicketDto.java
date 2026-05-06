package app.rubeton.toniq.service.megatix.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class MegatixTicketDto {

    private String id;
    private String name;
    private String description;
    private String currencyCode;
    private BigDecimal facePrice;
    private String salesStartsAt;
    private String salesEndsAt;
    private Integer displayOrder;
    private Boolean active;
    private Boolean soldOut;
    private Boolean salesClosed;
    private Boolean allocationExhausted;
    private Integer freeSeatsCount;

    public void setId(final String id) {
        this.id = id;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @JsonAlias({"currency", "currency_code"})
    public void setCurrencyCode(final String currencyCode) {
        this.currencyCode = currencyCode;
    }

    @JsonAlias({"face_price", "price", "list_price"})
    public void setFacePrice(final BigDecimal facePrice) {
        this.facePrice = facePrice;
    }

    @JsonAlias({"sales_starts_at", "sales_start_at"})
    public void setSalesStartsAt(final String salesStartsAt) {
        this.salesStartsAt = salesStartsAt;
    }

    @JsonAlias({"sales_ends_at", "sales_end_at"})
    public void setSalesEndsAt(final String salesEndsAt) {
        this.salesEndsAt = salesEndsAt;
    }

    @JsonAlias({"display_order", "sort_order", "position"})
    public void setDisplayOrder(final Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    @JsonAlias({"is_active", "active", "visible", "on_sale"})
    public void setActive(final Boolean active) {
        this.active = active;
    }

    @JsonAlias("is_sold_out")
    public void setSoldOut(final Boolean soldOut) {
        this.soldOut = soldOut;
    }

    @JsonAlias("is_sales_closed")
    public void setSalesClosed(final Boolean salesClosed) {
        this.salesClosed = salesClosed;
    }

    @JsonAlias("allocation_exhausted")
    public void setAllocationExhausted(final Boolean allocationExhausted) {
        this.allocationExhausted = allocationExhausted;
    }

    @JsonAlias({"free_seats_count", "remaining_quantity", "availability_count"})
    public void setFreeSeatsCount(final Integer freeSeatsCount) {
        this.freeSeatsCount = freeSeatsCount;
    }
}
