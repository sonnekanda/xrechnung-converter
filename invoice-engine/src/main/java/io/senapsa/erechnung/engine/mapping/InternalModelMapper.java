package io.senapsa.erechnung.engine.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import org.mustangproject.BankDetails;
import org.mustangproject.Contact;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Bildet das interne Rechnungsmodell auf ein Mustangproject-{@link Invoice} ab.
 * Es wird ausschliesslich aus dem internen Modell generiert (zentrale
 * Zwischenschicht); fehlende Pflichtfelder werden vorher im Pruefbildschirm
 * ergaenzt, sodass hier von vollstaendigen Daten ausgegangen werden kann.
 */
@Component
public class InternalModelMapper {

    public Invoice toInvoice(JsonNode model) {
        ModelReader r = new ModelReader(model);

        String currency = orDefault(r.str("kopf", "waehrung"), "EUR");

        TradeParty seller = new TradeParty(
                r.str("kopf", "verkaeufer_name"),
                r.str("kopf", "verkaeufer_strasse"),
                r.str("kopf", "verkaeufer_plz"),
                r.str("kopf", "verkaeufer_ort"),
                orDefault(r.str("kopf", "verkaeufer_land"), "DE"));
        applyIfPresent(r.str("kopf", "verkaeufer_ustid"), seller::addVATID);
        applyIfPresent(r.str("kopf", "verkaeufer_steuernummer"), seller::addTaxID);
        // XRechnung verlangt einen Verkaeufer-Kontakt (BG-6) und Zahlungsweg (IBAN).
        String contactName = orDefault(r.str("kopf", "verkaeufer_kontakt_name"),
                r.str("kopf", "verkaeufer_name"));
        seller.setContact(new Contact(
                contactName,
                r.str("kopf", "verkaeufer_telefon"),
                r.str("kopf", "verkaeufer_email")));
        String iban = r.str("kopf", "bankverbindung_iban");
        if (iban != null && !iban.isBlank()) {
            seller.addBankDetails(new BankDetails(iban, r.str("kopf", "bankverbindung_bic")));
        }

        TradeParty buyer = new TradeParty(
                r.str("kopf", "kaeufer_name"),
                r.str("kopf", "kaeufer_strasse"),
                r.str("kopf", "kaeufer_plz"),
                r.str("kopf", "kaeufer_ort"),
                orDefault(r.str("kopf", "kaeufer_land"), "DE"));
        applyIfPresent(r.str("kopf", "kaeufer_ustid"), buyer::addVATID);

        Invoice invoice = new Invoice()
                .setNumber(r.str("kopf", "rechnungsnummer"))
                .setCurrency(currency)
                .setSender(seller)
                .setRecipient(buyer);

        Date issue = r.date("kopf", "rechnungsdatum");
        if (issue != null) {
            invoice.setIssueDate(issue);
        }
        Date due = r.date("kopf", "faelligkeit");
        if (due != null) {
            invoice.setDueDate(due);
        }
        Date delivery = r.date("kopf", "leistungsdatum");
        invoice.setDeliveryDate(delivery != null ? delivery : issue);

        // Leitweg-ID = Buyer reference (BT-10), fuer B2G verpflichtend.
        applyIfPresent(r.str("kopf", "leitweg_id"), invoice::setReferenceNumber);

        for (JsonNode pos : r.positionen()) {
            invoice.addItem(toItem(pos));
        }
        return invoice;
    }

    private Item toItem(JsonNode pos) {
        BigDecimal vat = orZero(ModelReader.posDec(pos, "steuersatz"));
        Product product = new Product(
                orDefault(ModelReader.posStr(pos, "beschreibung"), "Position"),
                "",
                orDefault(ModelReader.posStr(pos, "einheit_code"), "C62"), // C62 = Stueck (UN/ECE)
                vat);
        applyIfPresent(ModelReader.posStr(pos, "artikelnummer"), product::setSellerAssignedID);

        BigDecimal price = orZero(ModelReader.posDec(pos, "einzelpreis"));
        BigDecimal qty = orZero(ModelReader.posDec(pos, "menge"));
        return new Item(product, price, qty);
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void applyIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }
}
