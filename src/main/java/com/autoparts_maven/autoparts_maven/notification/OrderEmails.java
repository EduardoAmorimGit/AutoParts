package com.autoparts_maven.autoparts_maven.notification;

import com.autoparts_maven.autoparts_maven.legal.StoreInfo;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.OrderItemResponse;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.OrderResponse;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Plain-text customer e-mails (pt-BR). Every message ends with the seller identification and contacts. */
@Component
class OrderEmails {

    static final ZoneId RIO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(RIO);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    record Content(String subject, String body) {
    }

    private final StoreInfo store;

    OrderEmails(StoreInfo store) {
        this.store = store;
    }

    Content placed(String name, OrderResponse order) {
        return new Content("Recebemos seu pedido #" + order.id(), greeting(name)
                + "Recebemos o seu pedido #" + order.id() + ". Ele fica reservado até "
                + DATE_TIME.format(order.paymentDueAt()) + " aguardando o pagamento.\n\n"
                + summary(order)
                + "\nSe o pagamento não for confirmado até esse horário, o pedido é cancelado automaticamente "
                + "e nada é cobrado.\n" + footer());
    }

    Content paid(String name, OrderResponse order) {
        return new Content("Pagamento confirmado - pedido #" + order.id(), greeting(name)
                + "O pagamento do pedido #" + order.id() + " foi confirmado. Já estamos separando os itens.\n\n"
                + summary(order)
                + "\nVocê pode cancelar o pedido até ele sair para entrega, com reembolso integral, "
                + "e devolver os produtos em até 7 dias após o recebimento (direito de arrependimento, CDC art. 49).\n"
                + footer());
    }

    Content shipped(String name, OrderResponse order) {
        return new Content("Pedido #" + order.id() + " saiu para entrega", greeting(name)
                + "O pedido #" + order.id() + " saiu para entrega em " + address(order) + ".\n\n"
                + "Acompanhe a entrega: " + order.trackingUrl() + "\n" + footer());
    }

    Content delivered(String name, OrderResponse order) {
        return new Content("Pedido #" + order.id() + " entregue", greeting(name)
                + "O pedido #" + order.id() + " foi entregue em " + DATE_TIME.format(order.deliveredAt()) + ".\n\n"
                + "Você pode desistir da compra em até 7 dias corridos a partir do recebimento, sem precisar "
                + "justificar, com reembolso integral, inclusive do frete. Se algum produto apresentar defeito, "
                + "você tem até 90 dias para reclamar. Faça a solicitação pela área \"Meus pedidos\" ou pelos "
                + "contatos abaixo.\n" + footer());
    }

    Content cancelled(String name, OrderResponse order) {
        String paidNote = order.paidAt() == null
                ? "Nenhum valor foi cobrado por este pedido.\n"
                : "O valor pago será estornado integralmente pelo mesmo meio de pagamento. Você receberá outro "
                        + "e-mail assim que o estorno for confirmado.\n";
        return new Content("Pedido #" + order.id() + " cancelado", greeting(name)
                + "O pedido #" + order.id() + " foi cancelado.\nMotivo: " + order.cancelReason() + "\n\n"
                + paidNote + footer());
    }

    Content refunded(String name, Long orderId, BigDecimal amount, String method) {
        String when = "PIX".equals(method)
                ? "O valor volta para a conta de origem do Pix, normalmente em instantes."
                : "No cartão de crédito, o estorno aparece na fatura atual ou na seguinte, conforme o banco emissor.";
        return new Content("Estorno confirmado - pedido #" + orderId, greeting(name)
                + "Confirmamos o estorno de " + money(amount) + " referente ao pedido #" + orderId + ".\n"
                + when + "\n" + footer());
    }

    private String summary(OrderResponse order) {
        StringBuilder text = new StringBuilder("Itens:\n");
        for (OrderItemResponse item : order.items()) {
            text.append("- ").append(item.quantity()).append(" x ").append(item.productName())
                    .append(" (").append(money(item.unitPrice())).append(") = ").append(money(item.lineTotal()))
                    .append('\n');
        }
        return text.append("Subtotal: ").append(money(order.subtotal())).append('\n')
                .append("Entrega: ").append(money(order.deliveryFee())).append('\n')
                .append("Total: ").append(money(order.total())).append('\n')
                .append("Endereço de entrega: ").append(address(order)).append('\n')
                .toString();
    }

    private static String address(OrderResponse order) {
        var a = order.deliveryAddress();
        return a.street() + ", " + a.number() + (a.complement() == null ? "" : " - " + a.complement())
                + ", " + a.district() + ", " + a.city() + "/" + a.state();
    }

    private static String greeting(String name) {
        return "Olá, " + (name == null || name.isBlank() ? "cliente" : name.trim()) + "!\n\n";
    }

    private String footer() {
        return "\n--\n" + store.name() + " - " + store.legalName() + " - CNPJ " + store.cnpj() + "\n"
                + store.address() + "\n"
                + "Atendimento: " + store.supportEmail() + " | " + store.supportPhone() + "\n"
                + "Política de trocas, devoluções e reembolso: " + store.siteUrl() + "\n";
    }

    static String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(value).replace(' ', ' ');
    }

    static String format(Instant instant) {
        return DATE_TIME.format(instant);
    }
}
