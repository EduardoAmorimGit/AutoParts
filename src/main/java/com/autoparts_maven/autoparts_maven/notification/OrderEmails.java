package com.autoparts_maven.autoparts_maven.notification;

import com.autoparts_maven.autoparts_maven.legal.StoreInfo;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.OrderItemResponse;
import com.autoparts_maven.autoparts_maven.order.dto.OrderDtos.OrderResponse;
import com.autoparts_maven.autoparts_maven.returns.dto.ReturnDtos.ReturnResponse;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
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
    private final String passwordResetUrl;

    OrderEmails(StoreInfo store, @Value("${auth.password-reset.url:}") String passwordResetUrl,
            @Value("${store.site-url:}") String siteUrl) {
        this.store = store;
        this.passwordResetUrl = passwordResetUrl.isBlank()
                ? siteUrl.replaceAll("/+$", "") + "/redefinir-senha"
                : passwordResetUrl;
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

    Content returnRequested(String name, ReturnResponse request) {
        String what = "WITHDRAWAL".equals(request.type())
                ? "desistência da compra (direito de arrependimento)"
                : "produto com defeito";
        return new Content("Recebemos sua solicitação de devolução #" + request.id(), greeting(name)
                + "Recebemos sua solicitação de devolução #" + request.id() + " do pedido #" + request.orderId()
                + " (" + what + ") em " + DATE_TIME.format(request.requestedAt()) + ".\n\n"
                + "Valor a ser reembolsado: " + money(request.refundAmount())
                + (request.deliveryFeeAmount().signum() > 0 ? " (inclui o frete de " + money(request.deliveryFeeAmount()) + ")" : "")
                + ".\n\nNossa equipe vai responder em até 2 dias úteis com as instruções de coleta, que é feita "
                + "sem custo para você. Guarde os produtos e, se possível, a embalagem.\n" + footer());
    }

    Content returnApproved(String name, ReturnResponse request) {
        return new Content("Devolução #" + request.id() + " aprovada", greeting(name)
                + "Sua devolução #" + request.id() + " foi aprovada. Entraremos em contato para combinar a coleta, "
                + "sem custo para você."
                + (request.adminNote() == null ? "" : "\n\nObservação da loja: " + request.adminNote())
                + "\n\nO reembolso de " + money(request.refundAmount())
                + " é feito pelo mesmo meio de pagamento usado na compra.\n" + footer());
    }

    Content returnReceived(String name, ReturnResponse request) {
        return new Content("Recebemos os produtos da devolução #" + request.id(), greeting(name)
                + "Os produtos da devolução #" + request.id() + " chegaram à nossa loja. Você receberá a "
                + "confirmação do reembolso ou da troca em seguida.\n" + footer());
    }

    Content returnReplaced(String name, ReturnResponse request) {
        return new Content("Troca da devolução #" + request.id() + " concluída", greeting(name)
                + "O produto com defeito da devolução #" + request.id() + " foi substituído por uma unidade nova."
                + (request.adminNote() == null ? "" : "\n\nObservação da loja: " + request.adminNote())
                + "\n" + footer());
    }

    Content returnRejected(String name, ReturnResponse request) {
        return new Content("Devolução #" + request.id() + " não aprovada", greeting(name)
                + "Analisamos a devolução #" + request.id() + " do pedido #" + request.orderId()
                + " e ela não foi aprovada.\nMotivo: " + request.rejectionReason() + "\n\n"
                + "Se discordar ou tiver novas informações, responda a este e-mail ou fale com o nosso atendimento: "
                + "vamos reavaliar.\n" + footer());
    }

    /**
     * The token goes in the URL fragment (after '#'): browsers never send it to servers nor in the
     * Referer header, so it does not end up in access logs. The store page reads it from location.hash.
     */
    Content passwordReset(String name, String token, Instant expiresAt) {
        return new Content("Redefinição de senha", greeting(name)
                + "Recebemos um pedido para redefinir a senha da sua conta. Para criar uma nova senha, acesse:\n\n"
                + passwordResetUrl + "#token=" + token + "\n\n"
                + "O link vale até " + DATE_TIME.format(expiresAt) + " e só pode ser usado uma vez.\n"
                + "Se não foi você, ignore este e-mail: a sua senha continua a mesma.\n" + footer());
    }

    Content passwordChanged(String name, Instant changedAt) {
        return new Content("Sua senha foi alterada", greeting(name)
                + "A senha da sua conta foi alterada em " + DATE_TIME.format(changedAt)
                + ". Por segurança, encerramos as sessões abertas em todos os aparelhos.\n\n"
                + "Se não foi você, use a opção \"Esqueci minha senha\" para criar uma nova e fale com o nosso "
                + "atendimento.\n" + footer());
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
