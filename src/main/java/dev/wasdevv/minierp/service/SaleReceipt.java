package dev.wasdevv.minierp.service;

import dev.wasdevv.minierp.model.Money;
import dev.wasdevv.minierp.model.Sale;
import dev.wasdevv.minierp.model.SaleItem;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The text version of a sale's receipt, and the WhatsApp link that sends it. Not a fiscal document. */
@Component
public class SaleReceipt {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ZoneId zone;
    private final String storeName;

    public SaleReceipt(ZoneId shopZone, @Value("${minierp.store-name:Mini ERP}") String storeName) {
        this.zone = shopZone;
        this.storeName = storeName;
    }

    public String storeName() {
        return storeName;
    }

    public String text(Sale sale) {
        StringBuilder out = new StringBuilder()
                .append("*").append(storeName).append("*\n")
                .append("Venda #").append(sale.getId()).append(" · ")
                .append(sale.getSaleDate().atZoneSameInstant(zone).format(DATE_TIME)).append('\n');
        if (sale.isCancelled()) {
            out.append("*VENDA CANCELADA*\n");
        }
        out.append('\n');
        for (SaleItem item : sale.getItems()) {
            out.append(item.getQuantity()).append("x ").append(item.getProduct().getName())
                    .append(" — ").append(Money.format(item.getSubtotal())).append('\n');
        }
        out.append('\n');
        if (sale.hasDiscount()) {
            out.append("Subtotal: ").append(Money.format(sale.getSubtotalAmount())).append('\n')
                    .append("Desconto: −").append(Money.format(sale.getDiscountAmount())).append('\n');
        }
        out.append("*Total: ").append(Money.format(sale.getTotalAmount())).append("*\n");
        if (sale.getPaymentMethod() != null) {
            out.append("Pagamento: ").append(sale.getPaymentMethod().getLabel()).append('\n');
        }
        if (sale.getChangeAmount() != null) {
            out.append("Recebido: ").append(Money.format(sale.getAmountReceived()))
                    .append(" · Troco: ").append(Money.format(sale.getChangeAmount())).append('\n');
        }
        return out.append("\nObrigado pela preferência!").toString();
    }

    /** To the customer's number when there is one; otherwise WhatsApp asks whom to send it to. */
    public String whatsAppUrl(Sale sale) {
        String text = URLEncoder.encode(text(sale), StandardCharsets.UTF_8).replace("+", "%20");
        String phone = sale.getCustomer() == null ? null : sale.getCustomer().getPhone();
        return (phone == null ? "https://wa.me/" : "https://wa.me/55" + phone) + "?text=" + text;
    }
}
